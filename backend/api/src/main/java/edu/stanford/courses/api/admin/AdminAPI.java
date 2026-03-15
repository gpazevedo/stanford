package edu.stanford.courses.api.admin;

import edu.stanford.courses.api.admin.domain.AdminService;
import org.springframework.stereotype.Component;

@Component
public class AdminAPI {
    private final AdminService adminService;

    public AdminAPI(AdminService adminService) {
        this.adminService = adminService;
    }
    // delegate admin methods
}
