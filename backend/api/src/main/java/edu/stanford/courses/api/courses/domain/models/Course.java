package edu.stanford.courses.api.courses.domain.models;
import java.util.List;

public record Course(String courseId, String title, String description,
                     String units, List<String> instructors, String quarter,
                     String url, List<String> prerequisites, String prereqNote) {}
