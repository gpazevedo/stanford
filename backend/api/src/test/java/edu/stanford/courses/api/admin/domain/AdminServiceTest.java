package edu.stanford.courses.api.admin.domain;

import edu.stanford.courses.api.courses.domain.models.Course;
import edu.stanford.courses.api.courses.domain.CourseRepository;
import edu.stanford.courses.api.applications.domain.models.Application;
import edu.stanford.courses.api.applications.domain.ApplicationRepository;
import edu.stanford.courses.api.users.domain.models.User;
import edu.stanford.courses.api.users.domain.UserRepository;
import edu.stanford.courses.api.admin.rest.dtos.AdminCourseVM;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock CourseRepository courseRepo;
    @Mock ApplicationRepository appRepo;
    @Mock UserRepository userRepo;
    AdminService service;

    @BeforeEach void setUp() {
        service = new AdminService(courseRepo, appRepo, userRepo);
    }

    @Test
    void listCoursesJoinsApplicantCountsAndSortsDescending() {
        when(courseRepo.findAll()).thenReturn(List.of(
            new Course("CS229", "ML",  "d", "3", List.of(), "Q", "u", List.of(), ""),
            new Course("CS231N","DL",  "d", "3", List.of(), "Q", "u", List.of(), ""),
            new Course("CS106B","Prog","d", "4", List.of(), "Q", "u", List.of(), "")));
        when(appRepo.countAppliedByCourse()).thenReturn(Map.of("CS229", 42L, "CS106B", 5L));

        List<AdminCourseVM> result = service.listCourses();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).courseId()).isEqualTo("CS229");
        assertThat(result.get(0).applicantCount()).isEqualTo(42);
        assertThat(result.get(1).courseId()).isEqualTo("CS106B");
        assertThat(result.get(2).applicantCount()).isZero(); // CS231N has no applications
    }

    @Test
    void listApplicantsJoinsUserDetails() {
        when(appRepo.findAppliedByCourse("CS229")).thenReturn(List.of(
            new Application("u1", "CS229", "APPLIED", "2024-01-01", "2024-01-01")));
        when(userRepo.findAllByIds(Set.of("u1"))).thenReturn(Map.of(
            "u1", new User("u1", "jane@stanford.edu", "Jane Doe", List.of(), "2024")));

        var result = service.listApplicants("CS229");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Jane Doe");
        assertThat(result.get(0).email()).isEqualTo("jane@stanford.edu");
    }
}
