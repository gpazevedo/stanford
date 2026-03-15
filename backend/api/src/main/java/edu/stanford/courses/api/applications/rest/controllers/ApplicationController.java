package edu.stanford.courses.api.applications.rest.controllers;

import edu.stanford.courses.api.courses.rest.dtos.CourseSearchResponse;
import edu.stanford.courses.api.users.domain.UserRepository;
import edu.stanford.courses.api.applications.domain.ApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/applications")
public class ApplicationController {

    private final ApplicationService appService;
    private final UserRepository userRepo;

    public ApplicationController(ApplicationService appService, UserRepository userRepo) {
        this.appService = appService; this.userRepo = userRepo;
    }

    @GetMapping
    List<CourseSearchResponse> list(@AuthenticationPrincipal Jwt jwt) {
        var userId = jwt.getSubject();
        var user = userRepo.findOrCreate(userId, jwt.getClaimAsString("email"));
        return appService.listApplications(userId, user);
    }

    @PostMapping("/{courseId}")
    @ResponseStatus(HttpStatus.CREATED)
    void apply(@PathVariable String courseId, @AuthenticationPrincipal Jwt jwt) {
        var userId = jwt.getSubject();
        var user = userRepo.findOrCreate(userId, jwt.getClaimAsString("email"));
        appService.apply(userId, courseId, user);
    }

    @DeleteMapping("/{courseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void withdraw(@PathVariable String courseId, @AuthenticationPrincipal Jwt jwt) {
        appService.withdraw(jwt.getSubject(), courseId);
    }
}
