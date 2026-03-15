package edu.stanford.courses.api.applications.domain;

import edu.stanford.courses.api.applications.domain.models.Application;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class ApplicationRepository {

    private final DynamoDbClient dynamo;
    private final String table;

    public ApplicationRepository(DynamoDbClient dynamo,
                                  @Value("${aws.tables.applications}") String table) {
        this.dynamo = dynamo; this.table = table;
    }

    public Optional<Application> findByUserAndCourse(String userId, String courseId) {
        var resp = dynamo.getItem(GetItemRequest.builder().tableName(table)
            .key(Map.of("userId",   AttributeValue.fromS(userId),
                        "courseId", AttributeValue.fromS(courseId)))
            .build());
        return resp.hasItem() ? Optional.of(toItem(resp.item())) : Optional.empty();
    }

    public List<Application> findAppliedByUser(String userId) {
        return dynamo.query(QueryRequest.builder().tableName(table)
            .keyConditionExpression("userId = :uid")
            .filterExpression("#s = :applied")
            .expressionAttributeNames(Map.of("#s", "status"))
            .expressionAttributeValues(Map.of(
                ":uid",     AttributeValue.fromS(userId),
                ":applied", AttributeValue.fromS("APPLIED")))
            .build()).items().stream().map(this::toItem).toList();
    }

    public void save(Application app) {
        dynamo.putItem(PutItemRequest.builder().tableName(table).item(Map.of(
            "userId",    AttributeValue.fromS(app.userId()),
            "courseId",  AttributeValue.fromS(app.courseId()),
            "status",    AttributeValue.fromS(app.status()),
            "appliedAt", AttributeValue.fromS(app.appliedAt()),
            "updatedAt", AttributeValue.fromS(app.updatedAt())
        )).build());
    }

    public void updateStatus(String userId, String courseId, String status) {
        dynamo.updateItem(UpdateItemRequest.builder().tableName(table)
            .key(Map.of("userId",   AttributeValue.fromS(userId),
                        "courseId", AttributeValue.fromS(courseId)))
            .updateExpression("SET #s = :status, updatedAt = :now")
            .expressionAttributeNames(Map.of("#s", "status"))
            .expressionAttributeValues(Map.of(
                ":status", AttributeValue.fromS(status),
                ":now",    AttributeValue.fromS(Instant.now().toString())))
            .build());
    }

    /** Admin: count APPLIED applications per courseId via courseId-index GSI. */
    public Map<String, Long> countAppliedByCourse() {
        var counts = new HashMap<String, Long>();
        Map<String, AttributeValue> lastKey = null;
        do {
            var req = ScanRequest.builder().tableName(table)
                .indexName("courseId-index")
                .filterExpression("#s = :applied")
                .expressionAttributeNames(Map.of("#s", "status"))
                .expressionAttributeValues(Map.of(":applied", AttributeValue.fromS("APPLIED")));
            if (lastKey != null) req.exclusiveStartKey(lastKey);
            var resp = dynamo.scan(req.build());
            resp.items().forEach(item ->
                counts.merge(item.get("courseId").s(), 1L, Long::sum));
            lastKey = resp.lastEvaluatedKey().isEmpty() ? null : resp.lastEvaluatedKey();
        } while (lastKey != null);
        return counts;
    }

    /** Admin: list APPLIED applicants for a course. */
    public List<Application> findAppliedByCourse(String courseId) {
        return dynamo.query(QueryRequest.builder().tableName(table)
            .indexName("courseId-index")
            .keyConditionExpression("courseId = :cid")
            .filterExpression("#s = :applied")
            .expressionAttributeNames(Map.of("#s", "status"))
            .expressionAttributeValues(Map.of(
                ":cid",     AttributeValue.fromS(courseId),
                ":applied", AttributeValue.fromS("APPLIED")))
            .build()).items().stream().map(this::toItem).toList();
    }

    private Application toItem(Map<String, AttributeValue> item) {
        return new Application(item.get("userId").s(), item.get("courseId").s(),
            item.get("status").s(), item.get("appliedAt").s(), item.get("updatedAt").s());
    }
}
