package edu.stanford.courses.api.users.rest.controllers;

import edu.stanford.courses.api.users.domain.models.User;
import edu.stanford.courses.api.courses.domain.CourseRepository;
import edu.stanford.courses.api.users.domain.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/profile")
public class ProfileController {

    private final UserRepository userRepo;
    private final CourseRepository courseRepo;

    public ProfileController(UserRepository userRepo, CourseRepository courseRepo) {
        this.userRepo = userRepo; this.courseRepo = courseRepo;
    }

    @GetMapping("/completed-courses")
    Map<String, List<String>> getCompletedCourses(@AuthenticationPrincipal Jwt jwt) {
        var user = userRepo.findOrCreate(jwt.getSubject(), jwt.getClaimAsString("email"));
        return Map.of("courseIds", user.completedCourseIds());
    }

    @PutMapping("/completed-courses")
    Map<String, List<String>> updateCompletedCourses(
            @RequestBody Map<String, List<String>> body,
            @AuthenticationPrincipal Jwt jwt) {
        var courseIds = new HashSet<>(body.getOrDefault("courseIds", List.of()));
        var found = courseRepo.findAllByIds(courseIds);
        var invalid = courseIds.stream().filter(id -> !found.containsKey(id)).toList();
        if (!invalid.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unknown courseIds: " + invalid);

        var user = userRepo.findOrCreate(jwt.getSubject(), jwt.getClaimAsString("email"));
        var updated = new User(user.userId(), user.email(), user.name(),
            List.copyOf(courseIds), Instant.now().toString());
        userRepo.save(updated);
        return Map.of("courseIds", updated.completedCourseIds());
    }
}
