package edu.stanford.courses.ingestion.model;

import java.util.List;

public record ScrapedCourse(
    String courseId,
    String title,
    String description,
    String units,
    List<String> instructors,
    String quarter,
    String url,
    List<String> prerequisites,
    String prereqNote
) {}
