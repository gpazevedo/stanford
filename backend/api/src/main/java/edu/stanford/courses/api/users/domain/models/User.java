package edu.stanford.courses.api.users.domain.models;
import java.util.List;
public record User(String userId, String email, String name,
                   List<String> completedCourseIds, String updatedAt) {}
