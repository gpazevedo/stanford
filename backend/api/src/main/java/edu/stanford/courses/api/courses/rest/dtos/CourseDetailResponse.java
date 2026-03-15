package edu.stanford.courses.api.courses.rest.dtos;
import java.util.List;

public record CourseDetailResponse(
    String courseId, String title, String description,
    String units, String quarter, List<String> instructors,
    String prereqNote, List<PrereqStatus> prerequisites,
    boolean canApply, boolean applied
) {
    public record PrereqStatus(String courseId, String title, boolean met) {}
}
