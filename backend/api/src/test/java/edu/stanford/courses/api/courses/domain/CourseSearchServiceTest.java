package edu.stanford.courses.api.courses.domain;

import edu.stanford.courses.api.config.AppConfigService;
import edu.stanford.courses.api.config.AppSettings;
import edu.stanford.courses.api.courses.domain.models.Course;
import edu.stanford.courses.api.applications.domain.ApplicationRepository;
import edu.stanford.courses.api.users.domain.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseSearchServiceTest {

    @Mock S3VectorsClient s3Vectors;
    @Mock BedrockRuntimeClient bedrock;
    @Mock CourseRepository courseRepo;
    @Mock ApplicationRepository appRepo;
    @Mock AppConfigService appConfig;
    CourseSearchService service;

    @BeforeEach void setUp() {
        when(appConfig.getSettings()).thenReturn(
            new AppSettings("amazon.titan-embed-text-v2:0", "claude", 10, false, true));
        service = new CourseSearchService(s3Vectors, bedrock, courseRepo, appRepo,
            appConfig, "course-embeddings");
    }

    @Test
    void returnsSearchResultsWithPrereqAnnotation() {
        when(bedrock.invokeModel(any(InvokeModelRequest.class))).thenReturn(InvokeModelResponse.builder()
            .body(SdkBytes.fromUtf8String("{\"embedding\":[0.1,0.2,0.3]}")).build());
        when(s3Vectors.queryVectors(any(QueryVectorsRequest.class))).thenReturn(QueryVectorsResponse.builder()
            .vectors(List.of(
                QueryOutputVector.builder().key("CS229").distance(0.05f).build()))
            .build());
        when(courseRepo.findAllByIds(Set.of("CS229"))).thenReturn(Map.of(
            "CS229", new Course("CS229", "ML", "desc", "3",
                List.of("Ng"), "Autumn", "url", List.of("CS106B"), "")));
        when(appRepo.findAppliedByUser("u1")).thenReturn(List.of());

        var user = new User("u1", "a@b.com", "A", List.of("CS106B"), "2024");
        var results = service.search("machine learning", 10, "u1", user);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).courseId()).isEqualTo("CS229");
        assertThat(results.get(0).canApply()).isTrue();
        assertThat(results.get(0).missingPrereqs()).isEmpty();
    }

    @Test
    void clampsLimitToMax20() {
        when(bedrock.invokeModel(any(InvokeModelRequest.class))).thenReturn(InvokeModelResponse.builder()
            .body(SdkBytes.fromUtf8String("{\"embedding\":[0.1]}")).build());
        when(s3Vectors.queryVectors(any(QueryVectorsRequest.class))).thenAnswer(inv -> {
            QueryVectorsRequest req = inv.getArgument(0);
            assertThat(req.topK()).isEqualTo(20);
            return QueryVectorsResponse.builder().vectors(List.of()).build();
        });
        when(appRepo.findAppliedByUser(any())).thenReturn(List.of());
        when(courseRepo.findAllByIds(any())).thenReturn(Map.of());

        var user = new User("u1", "a@b", "A", List.of(), "2024");
        service.search("query", 50, "u1", user); // 50 > 20, should clamp
    }
}
