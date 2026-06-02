package com.secondhand.logistics.controller;

import com.secondhand.common.ApiResponse;
import com.secondhand.logistics.provider.LogisticsProvider;
import com.secondhand.logistics.service.LogisticsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shipments")
public class LogisticsController {

    private final LogisticsService logisticsService;

    public LogisticsController(LogisticsService logisticsService) {
        this.logisticsService = logisticsService;
    }

    @GetMapping("/{orderId}/track")
    public ApiResponse<LogisticsProvider.TrackResult> track(@PathVariable long orderId) {
        return ApiResponse.ok(logisticsService.trackByOrderId(orderId));
    }
}
