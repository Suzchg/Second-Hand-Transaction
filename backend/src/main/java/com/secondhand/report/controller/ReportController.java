package com.secondhand.report.controller;

import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.common.ApiResponse;
import com.secondhand.report.entity.Report;
import com.secondhand.report.entity.ReportReason;
import com.secondhand.report.service.ReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** 提交举报 */
    @PostMapping("/api/products/{productId}/report")
    public ApiResponse<Report> submit(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long productId,
            @Valid @RequestBody SubmitReportRequest req) {
        return ApiResponse.ok(reportService.submit(
                principal.userId(), productId, req.reasonType(), req.description()));
    }

    record SubmitReportRequest(
            @NotNull ReportReason reasonType,
            String description) {}
}
