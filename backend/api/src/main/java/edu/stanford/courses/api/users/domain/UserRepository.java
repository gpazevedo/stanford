package edu.stanford.courses.api.users.domain;

import edu.stanford.courses.api.users.domain.models.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class UserRepository {

    private final DynamoDbClient dynamo;
    private final String table;

    public UserRepository(DynamoDbClient dynamo,
                          @Value("${aws.tables.users}") String table) {
        this.dynamo = dynamo; this.table = table;
    }

    public Optional<User> findById(String userId) {
        var resp = dynamo.getItem(GetItemRequest.builder().tableName(table)
            .key(Map.of("userId", AttributeValue.fromS(userId)))
            .build());
        return resp.hasItem() ? Optional.of(toItem(resp.item())) : Optional.empty();
    }

    /** Returns existing user or creates a new one with empty completedCourseIds. */
    public User findOrCreate(String userId, String email) {
        return findById(userId).orElseGet(() -> {
            var user = new User(userId, email, email, List.of(), Instant.now().toString());
            save(user);
            return user;
        });
    }

    public void save(User user) {
        dynamo.putItem(PutItemRequest.builder().tableName(table).item(Map.of(
            "userId",             AttributeValue.fromS(user.userId()),
            "email",              AttributeValue.fromS(user.email()),
            "name",               AttributeValue.fromS(user.name()),
            "completedCourseIds", AttributeValue.fromL(user.completedCourseIds().stream()
                                      .map(AttributeValue::fromS).toList()),
            "updatedAt",          AttributeValue.fromS(user.updatedAt())
        )).build());
    }

    public Map<String, User> findAllByIds(Set<String> ids) {
        if (ids.isEmpty()) return Map.of();
        var result = new HashMap<String, User>();
        var pending = ids.stream()
            .map(id -> Map.of("userId", AttributeValue.fromS(id)))
            .collect(Collectors.toCollection(ArrayList::new));
        while (!pending.isEmpty()) {
            var resp = dynamo.batchGetItem(BatchGetItemRequest.builder()
                .requestItems(Map.of(table, KeysAndAttributes.builder().keys(pending).build()))
                .build());
            resp.responses().getOrDefault(table, List.of())
                .forEach(item -> { var u = toItem(item); result.put(u.userId(), u); });
            var unprocessed = resp.unprocessedKeys().get(table);
            pending = unprocessed != null ? new ArrayList<>(unprocessed.keys()) : new ArrayList<>();
        }
        return result;
    }

    private User toItem(Map<String, AttributeValue> item) {
        return new User(item.get("userId").s(), item.get("email").s(), item.get("name").s(),
            item.get("completedCourseIds").l().stream().map(AttributeValue::s).toList(),
            item.get("updatedAt").s());
    }
}
