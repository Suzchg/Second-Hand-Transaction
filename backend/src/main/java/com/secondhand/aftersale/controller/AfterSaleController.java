package com.secondhand.aftersale.controller;

import com.secondhand.aftersale.entity.AfterSaleRequest;
import com.secondhand.aftersale.entity.AfterSaleType;
import com.secondhand.aftersale.service.AfterSaleService;
import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/after-sale")
public class AfterSaleController {

    private final AfterSaleService afterSaleService;

    public AfterSaleController(AfterSaleService afterSaleService) {
        this.afterSaleService = afterSaleService;
    }

    @PostMapping
    public ApiResponse<AfterSaleRequest> request(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody RequestPayload req) {
        return ApiResponse.ok(afterSaleService.request(principal.userId(),
                new AfterSaleService.RequestCommand(req.orderId(), req.type(), req.reason(), req.refundAmountCent())));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<AfterSaleRequest> approve(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long requestId) {
        return ApiResponse.ok(afterSaleService.approve(principal.userId(), requestId));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<AfterSaleRequest> reject(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long requestId,
            @RequestBody(required = false) RejectPayload req) {
        return ApiResponse.ok(afterSaleService.reject(principal.userId(), requestId,
                req != null ? req.note() : null));
    }

    @PostMapping("/{id}/return-ship")
    public ApiResponse<AfterSaleRequest> returnShip(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long requestId,
            @RequestBody ReturnShipPayload req) {
        return ApiResponse.ok(afterSaleService.returnShip(principal.userId(), requestId,
                req.carrierCode(), req.trackingNo()));
    }

    @PostMapping("/{id}/confirm-return")
    public ApiResponse<AfterSaleRequest> confirmReturn(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long requestId) {
        return ApiResponse.ok(afterSaleService.confirmReturn(principal.userId(), requestId));
    }

    @GetMapping("/{id}")
    public ApiResponse<AfterSaleRequest> detail(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long requestId) {
        return ApiResponse.ok(afterSaleService.get(principal.userId(), requestId));
    }

    record RequestPayload(long orderId, AfterSaleType type, String reason, Integer refundAmountCent) {}
    record RejectPayload(String note) {}
    record ReturnShipPayload(String carrierCode, String trackingNo) {}
}
