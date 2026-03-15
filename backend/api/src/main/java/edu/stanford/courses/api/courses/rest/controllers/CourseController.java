package edu.stanford.courses.api.courses.rest.controllers;

import edu.stanford.courses.api.courses.rest.dtos.CourseDetailResponse;
import edu.stanford.courses.api.courses.rest.dtos.CourseSearchResponse;
import edu.stanford.courses.api.courses.domain.CourseSearchService;
import edu.stanford.courses.api.users.domain.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/courses")
public class CourseController {

    private final CourseSearchService searchService;
    private final UserRepository userRepo;

    public CourseController(CourseSearchService searchService, UserRepository userRepo) {
        this.searchService = searchService; this.userRepo = userRepo;
    }

    @GetMapping("/search")
    List<CourseSearchResponse> search(@RequestParam String q,
                                      @RequestParam(defaultValue = "10") int limit,
                                      @AuthenticationPrincipal Jwt jwt) {
        var userId = jwt.getSubject();
        var user = userRepo.findOrCreate(userId, jwt.getClaimAsString("email"));
        return searchService.search(q, limit, userId, user);
    }

    @GetMapping("/{courseId}")
    CourseDetailResponse detail(@PathVariable String courseId,
                                @AuthenticationPrincipal Jwt jwt) {
        var userId = jwt.getSubject();
        var user = userRepo.findOrCreate(userId, jwt.getClaimAsString("email"));
        return searchService.getById(courseId, userId, user);
    }
}
