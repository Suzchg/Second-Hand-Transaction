package com.secondhand.admin.controller;

import com.secondhand.admin.service.AdminService;
import com.secondhand.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {

    private final AdminService adminService;

    public AdminDashboardController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminService.DashboardDto> dashboard() {
        return ApiResponse.ok(adminService.getDashboard());
    }
}
