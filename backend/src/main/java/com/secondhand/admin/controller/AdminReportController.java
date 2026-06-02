package com.secondhand.admin.controller;

import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.common.ApiResponse;
import com.secondhand.report.entity.Report;
import com.secondhand.report.entity.ReportStatus;
import com.secondhand.report.service.ReportService;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** 举报列表 */
    @GetMapping
    public ApiResponse<Page<Report>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ReportStatus status) {
        return ApiResponse.ok(reportService.listAll(page, size, status));
    }

    /** 处理举报 */
    @PutMapping("/{id}/handle")
    public ApiResponse<Report> handle(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) HandleRequest req) {
        String note = req != null ? req.handleNote() : null;
        return ApiResponse.ok(reportService.handle(id, principal.userId(), note));
    }

    /** 驳回举报 */
    @PutMapping("/{id}/dismiss")
    public ApiResponse<Report> dismiss(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) HandleRequest req) {
        String note = req != null ? req.handleNote() : null;
        return ApiResponse.ok(reportService.dismiss(id, principal.userId(), note));
    }

    record HandleRequest(String handleNote) {}
}
