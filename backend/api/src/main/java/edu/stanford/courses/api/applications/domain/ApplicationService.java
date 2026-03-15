package edu.stanford.courses.api.applications.domain;

import edu.stanford.courses.api.applications.domain.models.Application;
import edu.stanford.courses.api.courses.domain.CourseRepository;
import edu.stanford.courses.api.courses.rest.dtos.CourseSearchResponse;
import edu.stanford.courses.api.users.domain.models.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private final ApplicationRepository appRepo;
    private final CourseRepository courseRepo;

    public ApplicationService(ApplicationRepository appRepo, CourseRepository courseRepo) {
        this.appRepo = appRepo; this.courseRepo = courseRepo;
    }

    public List<CourseSearchResponse> listApplications(String userId, User user) {
        var apps      = appRepo.findAppliedByUser(userId);
        var courseIds = apps.stream().map(Application::courseId).collect(Collectors.toSet());
        var courses   = courseRepo.findAllByIds(courseIds);
        return apps.stream().map(a -> {
            var c = courses.get(a.courseId());
            if (c == null) return null;
            var missing = c.prerequisites().stream()
                .filter(p -> !user.completedCourseIds().contains(p)).toList();
            return new CourseSearchResponse(c.courseId(), c.title(), c.units(),
                missing.isEmpty(), missing, true);
        }).filter(Objects::nonNull).toList();
    }

    public void apply(String userId, String courseId, User user) {
        var course = courseRepo.findById(courseId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        var missing = course.prerequisites().stream()
            .filter(p -> !user.completedCourseIds().contains(p)).toList();
        if (!missing.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Missing prerequisites: " + missing);

        appRepo.findByUserAndCourse(userId, courseId).ifPresent(existing -> {
            if ("APPLIED".equals(existing.status()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Already applied to this course");
        });

        var now = Instant.now().toString();
        appRepo.save(new Application(userId, courseId, "APPLIED", now, now));
    }

    public void withdraw(String userId, String courseId) {
        appRepo.findByUserAndCourse(userId, courseId)
            .filter(a -> "APPLIED".equals(a.status()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        appRepo.updateStatus(userId, courseId, "WITHDRAWN");
    }
}
