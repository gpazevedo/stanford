package edu.stanford.courses.ingestion;

import edu.stanford.courses.ingestion.model.AppSettings;
import edu.stanford.courses.ingestion.model.ScrapedCourse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.*;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock DynamoDbClient dynamo;
    @Mock BedrockRuntimeClient bedrock;
    @Mock S3VectorsClient s3Vectors;
    @Mock AppConfigService appConfig;
    IngestionService service;

    @BeforeEach void setUp() {
        when(appConfig.getSettings()).thenReturn(
            new AppSettings("amazon.titan-embed-text-v2:0", "claude", 10, false, true));
        service = new IngestionService(dynamo, bedrock, s3Vectors, appConfig,
            "courses", "applications", "course-embeddings");
    }

    @Test
    void upsertsScrapedCourseIntoDynamoAndVectors() {
        when(dynamo.scan(any(ScanRequest.class))).thenReturn(
            ScanResponse.builder().items(List.of()).build());
        when(bedrock.invokeModel(any(InvokeModelRequest.class))).thenReturn(InvokeModelResponse.builder()
            .body(SdkBytes.fromUtf8String("{\"embedding\":[0.1,0.2,0.3]}")).build());

        service.ingest(List.of(new ScrapedCourse("CS229", "ML", "desc", "3",
            List.of("Ng"), "Autumn 2024", "http://example.com/CS229", List.of(), "")));

        verify(s3Vectors).putVectors(any(PutVectorsRequest.class));
        verify(dynamo).putItem(argThat((PutItemRequest req) -> req.tableName().equals("courses") &&
            req.item().get("courseId").s().equals("CS229")));
    }

    @Test
    void deletesStaleCoursesFromDynamoAndVectors() {
        when(dynamo.scan(any(ScanRequest.class))).thenReturn(
            ScanResponse.builder().items(List.of(
                Map.of("courseId", AttributeValue.fromS("CS229")),
                Map.of("courseId", AttributeValue.fromS("CS230"))
            )).build());
        when(dynamo.query(any(QueryRequest.class))).thenReturn(
            QueryResponse.builder().items(List.of()).count(0).build());
        when(bedrock.invokeModel(any(InvokeModelRequest.class))).thenReturn(InvokeModelResponse.builder()
            .body(SdkBytes.fromUtf8String("{\"embedding\":[0.1,0.2,0.3]}")).build());

        service.ingest(List.of(new ScrapedCourse("CS229", "ML", "desc", "3",
            List.of("Ng"), "Autumn 2024", "http://example.com", List.of(), "")));

        verify(dynamo).deleteItem(any(DeleteItemRequest.class));
        verify(s3Vectors).deleteVectors(any(DeleteVectorsRequest.class));
    }

    @Test
    void withdrawsAppliedApplicationsForStaleCourse() {
        when(dynamo.scan(any(ScanRequest.class))).thenReturn(
            ScanResponse.builder().items(List.of(
                Map.of("courseId", AttributeValue.fromS("CS230"))
            )).build());
        when(dynamo.query(any(QueryRequest.class))).thenReturn(
            QueryResponse.builder().items(List.of(Map.of(
                "userId",   AttributeValue.fromS("user-1"),
                "courseId", AttributeValue.fromS("CS230"),
                "status",   AttributeValue.fromS("APPLIED")
            ))).count(1).build());

        service.ingest(List.of());

        verify(dynamo).updateItem(any(UpdateItemRequest.class));
    }
}
