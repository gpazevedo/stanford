package edu.stanford.courses.api.users.rest.controllers;

import edu.stanford.courses.api.users.domain.models.User;
import edu.stanford.courses.api.users.domain.UserRepository;
import edu.stanford.courses.api.courses.domain.CourseRepository;
import edu.stanford.courses.api.courses.domain.models.Course;
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
class ProfileControllerTest {

    @Mock UserRepository userRepo;
    @Mock CourseRepository courseRepo;
    ProfileController controller;

    @BeforeEach void setUp() {
        controller = new ProfileController(userRepo, courseRepo);
    }

    private org.springframework.security.oauth2.jwt.Jwt mockJwt(String subject) {
        var jwt = mock(org.springframework.security.oauth2.jwt.Jwt.class,
            org.mockito.Mockito.withSettings().lenient());
        when(jwt.getSubject()).thenReturn(subject);
        when(jwt.getClaimAsString("email")).thenReturn("user@stanford.edu");
        return jwt;
    }

    @Test
    void getCompletedCoursesReturnsList() {
        when(userRepo.findOrCreate("u1", "user@stanford.edu"))
            .thenReturn(new User("u1", "user@stanford.edu", "U", List.of("CS106B"), "2024"));

        var result = controller.getCompletedCourses(mockJwt("u1"));

        assertThat(result).containsEntry("courseIds", List.of("CS106B"));
    }

    @Test
    void putValidatesAndSavesCompletedCourses() {
        when(userRepo.findOrCreate(any(), any()))
            .thenReturn(new User("u1", "user@stanford.edu", "U", List.of(), "2024"));
        when(courseRepo.findAllByIds(Set.of("CS106B")))
            .thenReturn(Map.of("CS106B",
                new Course("CS106B", "Prog", "d", "4", List.of(), "Q", "u", List.of(), "")));

        controller.updateCompletedCourses(Map.of("courseIds", List.of("CS106B")), mockJwt("u1"));

        verify(userRepo).save(argThat(u -> u.completedCourseIds().contains("CS106B")));
    }

    @Test
    void putRejects400ForUnknownCourseId() {
        when(courseRepo.findAllByIds(Set.of("BOGUS"))).thenReturn(Map.of());

        assertThatThrownBy(() ->
            controller.updateCompletedCourses(Map.of("courseIds", List.of("BOGUS")), mockJwt("u1")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e ->
                assertThat(((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
