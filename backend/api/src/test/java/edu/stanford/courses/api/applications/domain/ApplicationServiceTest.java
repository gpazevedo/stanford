package edu.stanford.courses.api.applications.domain;

import edu.stanford.courses.api.applications.domain.models.Application;
import edu.stanford.courses.api.courses.domain.CourseRepository;
import edu.stanford.courses.api.courses.domain.models.Course;
import edu.stanford.courses.api.users.domain.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock ApplicationRepository appRepo;
    @Mock CourseRepository courseRepo;
    ApplicationService service;

    @BeforeEach void setUp() {
        service = new ApplicationService(appRepo, courseRepo);
    }

    @Test
    void applySucceedsWhenPrereqsMet() {
        var course = new Course("CS229", "ML", "d", "3", List.of(), "Q", "u", List.of("CS106B"), "");
        var user   = new User("u1", "a@b", "A", List.of("CS106B"), "2024");
        when(courseRepo.findById("CS229")).thenReturn(Optional.of(course));
        when(appRepo.findByUserAndCourse("u1", "CS229")).thenReturn(Optional.empty());

        service.apply("u1", "CS229", user);

        verify(appRepo).save(argThat(a ->
            a.courseId().equals("CS229") && "APPLIED".equals(a.status())));
    }

    @Test
    void applyThrows400WhenPrereqsMissing() {
        var course = new Course("CS229", "ML", "d", "3", List.of(), "Q", "u", List.of("CS106B"), "");
        var user   = new User("u1", "a@b", "A", List.of(), "2024");
        when(courseRepo.findById("CS229")).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> service.apply("u1", "CS229", user))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e ->
                assertThat(((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void applyThrows409WhenAlreadyApplied() {
        var course   = new Course("CS229", "ML", "d", "3", List.of(), "Q", "u", List.of(), "");
        var existing = new Application("u1", "CS229", "APPLIED", "2024", "2024");
        var user     = new User("u1", "a@b", "A", List.of(), "2024");
        when(courseRepo.findById("CS229")).thenReturn(Optional.of(course));
        when(appRepo.findByUserAndCourse("u1", "CS229")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.apply("u1", "CS229", user))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e ->
                assertThat(((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void withdrawSetsStatusToWithdrawn() {
        var existing = new Application("u1", "CS229", "APPLIED", "2024", "2024");
        when(appRepo.findByUserAndCourse("u1", "CS229")).thenReturn(Optional.of(existing));

        service.withdraw("u1", "CS229");

        verify(appRepo).updateStatus("u1", "CS229", "WITHDRAWN");
    }

    @Test
    void withdrawThrows404WhenNotApplied() {
        when(appRepo.findByUserAndCourse("u1", "CS229")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.withdraw("u1", "CS229"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e ->
                assertThat(((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void withdrawThrows404WhenAlreadyWithdrawn() {
        var withdrawn = new Application("u1", "CS229", "WITHDRAWN", "2024", "2024");
        when(appRepo.findByUserAndCourse("u1", "CS229")).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.withdraw("u1", "CS229"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e ->
                assertThat(((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
