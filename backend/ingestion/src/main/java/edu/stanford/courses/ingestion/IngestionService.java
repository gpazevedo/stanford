package edu.stanford.courses.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.stanford.courses.ingestion.model.ScrapedCourse;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class IngestionService {

    private static final Logger LOG = Logger.getLogger(IngestionService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DynamoDbClient dynamo;
    private final BedrockRuntimeClient bedrock;
    private final S3VectorsClient s3Vectors;
    private final AppConfigService appConfig;
    private final String coursesTable, applicationsTable, vectorIndex;

    public IngestionService(DynamoDbClient dynamo, BedrockRuntimeClient bedrock,
                            S3VectorsClient s3Vectors, AppConfigService appConfig,
                            String coursesTable, String applicationsTable, String vectorIndex) {
        this.dynamo = dynamo;
        this.bedrock = bedrock;
        this.s3Vectors = s3Vectors;
        this.appConfig = appConfig;
        this.coursesTable = coursesTable;
        this.applicationsTable = applicationsTable;
        this.vectorIndex = vectorIndex;
    }

    public void ingest(List<ScrapedCourse> scraped) {
        var settings   = appConfig.getSettings();
        var scrapedIds = scraped.stream().map(ScrapedCourse::courseId).collect(Collectors.toSet());
        var existing   = scanCourseIds();

        var stale = new HashSet<>(existing);
        stale.removeAll(scrapedIds);

        int deleted = 0, withdrawn = 0;
        for (var staleId : stale) {
            dynamo.deleteItem(DeleteItemRequest.builder()
                .tableName(coursesTable)
                .key(Map.of("courseId", AttributeValue.fromS(staleId)))
                .build());
            s3Vectors.deleteVectors(DeleteVectorsRequest.builder()
                .indexName(vectorIndex)
                .keys(List.of(staleId))
                .build());
            withdrawn += withdrawApplications(staleId);
            deleted++;
        }

        int added = 0, updated = 0;
        for (var course : scraped) {
            var vec = embed(course, settings.embeddingModelId());
            upsertVector(course, vec);
            upsertCourse(course);
            if (existing.contains(course.courseId())) updated++; else added++;
        }
        LOG.info("Ingestion complete: added=%d updated=%d deleted=%d withdrawn=%d"
            .formatted(added, updated, deleted, withdrawn));
    }

    private Set<String> scanCourseIds() {
        var ids = new HashSet<String>();
        Map<String, AttributeValue> lastKey = null;
        do {
            var builder = ScanRequest.builder()
                .tableName(coursesTable)
                .projectionExpression("courseId");
            if (lastKey != null) builder.exclusiveStartKey(lastKey);
            var resp = dynamo.scan(builder.build());
            resp.items().forEach(item -> ids.add(item.get("courseId").s()));
            lastKey = resp.lastEvaluatedKey().isEmpty() ? null : resp.lastEvaluatedKey();
        } while (lastKey != null);
        return ids;
    }

    private int withdrawApplications(String courseId) {
        var resp = dynamo.query(QueryRequest.builder()
            .tableName(applicationsTable)
            .indexName("courseId-index")
            .keyConditionExpression("courseId = :cid")
            .filterExpression("#s = :applied")
            .expressionAttributeNames(Map.of("#s", "status"))
            .expressionAttributeValues(Map.of(
                ":cid",     AttributeValue.fromS(courseId),
                ":applied", AttributeValue.fromS("APPLIED")))
            .build());
        var now = Instant.now().toString();
        for (var item : resp.items()) {
            dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(applicationsTable)
                .key(Map.of("userId",   item.get("userId"),
                            "courseId", item.get("courseId")))
                .updateExpression("SET #s = :withdrawn, updatedAt = :now")
                .expressionAttributeNames(Map.of("#s", "status"))
                .expressionAttributeValues(Map.of(
                    ":withdrawn", AttributeValue.fromS("WITHDRAWN"),
                    ":now",       AttributeValue.fromS(now)))
                .build());
        }
        return resp.count();
    }

    private float[] embed(ScrapedCourse course, String modelId) {
        var payload = MAPPER.createObjectNode()
            .put("inputText", course.title() + ". " + course.description())
            .toString();
        var resp = bedrock.invokeModel(InvokeModelRequest.builder()
            .modelId(modelId)
            .contentType("application/json").accept("application/json")
            .body(SdkBytes.fromUtf8String(payload))
            .build());
        try {
            var arr = MAPPER.readTree(resp.body().asUtf8String()).get("embedding");
            var vec = new float[arr.size()];
            for (int i = 0; i < vec.length; i++) vec[i] = (float) arr.get(i).asDouble();
            return vec;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Bedrock embedding response", e);
        }
    }

    private void upsertVector(ScrapedCourse course, float[] vec) {
        var floatList = new ArrayList<Float>(vec.length);
        for (float v : vec) floatList.add(v);
        var metadata = Document.fromMap(Map.of(
            "title",   Document.fromString(course.title()),
            "units",   Document.fromString(course.units()),
            "quarter", Document.fromString(course.quarter())));
        s3Vectors.putVectors(PutVectorsRequest.builder()
            .indexName(vectorIndex)
            .vectors(List.of(PutInputVector.builder()
                .key(course.courseId())
                .data(VectorData.fromFloat32(floatList))
                .metadata(metadata)
                .build()))
            .build());
    }

    private void upsertCourse(ScrapedCourse c) {
        dynamo.putItem(PutItemRequest.builder()
            .tableName(coursesTable)
            .item(Map.of(
                "courseId",      AttributeValue.fromS(c.courseId()),
                "title",         AttributeValue.fromS(c.title()),
                "description",   AttributeValue.fromS(c.description()),
                "units",         AttributeValue.fromS(c.units()),
                "instructors",   AttributeValue.fromL(
                                     c.instructors().stream()
                                      .map(AttributeValue::fromS).toList()),
                "quarter",       AttributeValue.fromS(c.quarter()),
                "url",           AttributeValue.fromS(c.url()),
                "prerequisites", AttributeValue.fromL(
                                     c.prerequisites().stream()
                                      .map(AttributeValue::fromS).toList()),
                "prereqNote",    AttributeValue.fromS(c.prereqNote()),
                "ingestedAt",    AttributeValue.fromS(Instant.now().toString())
            )).build());
    }
}
