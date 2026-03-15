package edu.stanford.courses.api.admin.rest.controllers;

import edu.stanford.courses.api.admin.rest.dtos.AdminCourseVM;
import edu.stanford.courses.api.admin.domain.AdminService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) { this.adminService = adminService; }

    @GetMapping("/courses")
    public List<AdminCourseVM> listCourses() {
        return adminService.listCourses();
    }

    @GetMapping("/courses/{courseId}/applicants")
    public List<AdminService.ApplicantView> listApplicants(@PathVariable String courseId) {
        return adminService.listApplicants(courseId);
    }
}
