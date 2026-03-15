package edu.stanford.courses.api.courses.domain;

import edu.stanford.courses.api.courses.domain.models.Course;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseRepositoryTest {

    @Mock DynamoDbClient dynamo;
    CourseRepository repo;

    @BeforeEach void setUp() { repo = new CourseRepository(dynamo, "courses"); }

    @Test
    void findByIdReturnsCourseWhenFound() {
        when(dynamo.getItem(any(GetItemRequest.class))).thenReturn(
            GetItemResponse.builder().item(Map.of(
                "courseId",      AttributeValue.fromS("CS229"),
                "title",         AttributeValue.fromS("Machine Learning"),
                "description",   AttributeValue.fromS("Intro to ML"),
                "units",         AttributeValue.fromS("3-4"),
                "instructors",   AttributeValue.fromL(List.of(AttributeValue.fromS("Andrew Ng"))),
                "quarter",       AttributeValue.fromS("Autumn 2024"),
                "url",           AttributeValue.fromS("https://example.com"),
                "prerequisites", AttributeValue.fromL(List.of()),
                "prereqNote",    AttributeValue.fromS("")
            )).build());

        var result = repo.findById("CS229");

        assertThat(result).isPresent();
        assertThat(result.get().title()).isEqualTo("Machine Learning");
        assertThat(result.get().instructors()).containsExactly("Andrew Ng");
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        when(dynamo.getItem(any(GetItemRequest.class)))
            .thenReturn(GetItemResponse.builder().build());
        assertThat(repo.findById("MISSING")).isEmpty();
    }
}
