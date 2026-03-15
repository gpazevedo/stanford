package edu.stanford.courses.api.courses;

import edu.stanford.courses.api.courses.domain.CourseSearchService;
import edu.stanford.courses.api.courses.rest.dtos.CourseDetailResponse;
import edu.stanford.courses.api.courses.rest.dtos.CourseSearchResponse;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class CoursesAPI {
    private final CourseSearchService courseSearchService;
    public CoursesAPI(CourseSearchService courseSearchService) {
        this.courseSearchService = courseSearchService;
    }
    public List<CourseSearchResponse> search(String query) {
        return courseSearchService.search(query, 10, null, null);
    }
    public CourseDetailResponse getById(String courseId) {
        return courseSearchService.getById(courseId, null, null);
    }
}
