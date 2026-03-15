package edu.stanford.courses.api.courses.domain;

import edu.stanford.courses.api.courses.domain.models.Course;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class CourseRepository {

    private final DynamoDbClient dynamo;
    private final String table;

    public CourseRepository(DynamoDbClient dynamo,
                            @Value("${aws.tables.courses:courses}") String table) {
        this.dynamo = dynamo; this.table = table;
    }

    public Optional<Course> findById(String courseId) {
        var resp = dynamo.getItem(GetItemRequest.builder()
            .tableName(table)
            .key(Map.of("courseId", AttributeValue.fromS(courseId)))
            .build());
        return resp.hasItem() ? Optional.of(toItem(resp.item())) : Optional.empty();
    }

    public Map<String, Course> findAllByIds(Set<String> ids) {
        if (ids.isEmpty()) return Map.of();
        var result = new HashMap<String, Course>();
        var pending = ids.stream()
            .map(id -> Map.of("courseId", AttributeValue.fromS(id)))
            .collect(Collectors.toCollection(ArrayList::new));
        while (!pending.isEmpty()) {
            var resp = dynamo.batchGetItem(BatchGetItemRequest.builder()
                .requestItems(Map.of(table, KeysAndAttributes.builder().keys(pending).build()))
                .build());
            resp.responses().getOrDefault(table, List.of())
                .forEach(item -> { var c = toItem(item); result.put(c.courseId(), c); });
            var unprocessed = resp.unprocessedKeys().get(table);
            pending = unprocessed != null ? new ArrayList<>(unprocessed.keys())
                                          : new ArrayList<>();
        }
        return result;
    }

    public List<Course> findAll() {
        var result = new java.util.ArrayList<Course>();
        Map<String, AttributeValue> lastKey = null;
        do {
            var req = ScanRequest.builder().tableName(table);
            if (lastKey != null) req.exclusiveStartKey(lastKey);
            var resp = dynamo.scan(req.build());
            resp.items().forEach(item -> result.add(toItem(item)));
            lastKey = resp.lastEvaluatedKey().isEmpty() ? null : resp.lastEvaluatedKey();
        } while (lastKey != null);
        return result;
    }

    private Course toItem(Map<String, AttributeValue> item) {
        return new Course(
            item.get("courseId").s(),
            item.get("title").s(),
            item.get("description").s(),
            item.get("units").s(),
            item.get("instructors").l().stream().map(AttributeValue::s).toList(),
            item.get("quarter").s(),
            item.get("url").s(),
            item.get("prerequisites").l().stream().map(AttributeValue::s).toList(),
            item.getOrDefault("prereqNote", AttributeValue.fromS("")).s()
        );
    }
}
