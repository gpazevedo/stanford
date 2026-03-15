package edu.stanford.courses.api.courses.rest.dtos;
import java.util.List;

public record CourseSearchResponse(
    String courseId, String title, String units,
    boolean canApply, List<String> missingPrereqs, boolean applied
) {}
