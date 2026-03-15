package edu.stanford.courses.api.admin.domain;

import edu.stanford.courses.api.courses.domain.CourseRepository;
import edu.stanford.courses.api.applications.domain.ApplicationRepository;
import edu.stanford.courses.api.applications.domain.models.Application;
import edu.stanford.courses.api.users.domain.UserRepository;
import edu.stanford.courses.api.admin.rest.dtos.AdminCourseVM;
import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final CourseRepository courseRepo;
    private final ApplicationRepository appRepo;
    private final UserRepository userRepo;

    public AdminService(CourseRepository courseRepo, ApplicationRepository appRepo,
                        UserRepository userRepo) {
        this.courseRepo = courseRepo; this.appRepo = appRepo; this.userRepo = userRepo;
    }

    public List<AdminCourseVM> listCourses() {
        var courses = courseRepo.findAll();
        var counts  = appRepo.countAppliedByCourse();
        return courses.stream()
            .map(c -> new AdminCourseVM(c.courseId(), c.title(),
                counts.getOrDefault(c.courseId(), 0L)))
            .sorted(Comparator.comparingLong(AdminCourseVM::applicantCount).reversed())
            .toList();
    }

    public record ApplicantView(String userId, String name, String email,
                                String appliedAt, String status) {}

    public List<ApplicantView> listApplicants(String courseId) {
        var apps    = appRepo.findAppliedByCourse(courseId);
        var userIds = apps.stream().map(Application::userId).collect(Collectors.toSet());
        var users   = userRepo.findAllByIds(userIds);
        return apps.stream().map(a -> {
            var u = users.get(a.userId());
            return new ApplicantView(
                a.userId(),
                u != null ? u.name()  : a.userId(),
                u != null ? u.email() : "",
                a.appliedAt(), a.status());
        }).toList();
    }
}
