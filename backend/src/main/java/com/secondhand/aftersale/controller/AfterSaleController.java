package com.secondhand.aftersale.controller;

import com.secondhand.aftersale.entity.AfterSaleRequest;
import com.secondhand.aftersale.entity.AfterSaleType;
import com.secondhand.aftersale.service.AfterSaleService;
import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/after-sale")
public class AfterSaleController {

    private final AfterSaleService afterSaleService;

    public AfterSaleController(AfterSaleService afterSaleService) {
        this.afterSaleService = afterSaleService;
    }

    /** 买家发起售后 */
    @PostMapping
    public ApiResponse<AfterSaleRequest> request(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody RequestPayload req) {
        return ApiResponse.ok(afterSaleService.request(principal.userId(),
                new AfterSaleService.RequestCommand(
                        req.orderId(), req.type(), req.reason(),
                        req.refundAmountCent(), req.buyerEvidence())));
    }

    /** 卖家同意 */
    @PostMapping("/{id}/approve")
    public ApiResponse<AfterSaleRequest> approve(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long requestId) {
        return ApiResponse.ok(afterSaleService.approve(principal.userId(), requestId));
    }

    /** 卖家拒绝 */
    @PostMapping("/{id}/reject")
    public ApiResponse<AfterSaleRequest> reject(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long requestId,
            @RequestBody(required = false) RejectPayload req) {
        return ApiResponse.ok(afterSaleService.reject(principal.userId(), requestId,
                req != null ? req.note() : null));
    }

    /** 买家寄回退货 */
    @PostMapping("/{id}/return-ship")
    public ApiResponse<AfterSaleRequest> returnShip(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long requestId,
            @RequestBody ReturnShipPayload req) {
        return ApiResponse.ok(afterSaleService.returnShip(principal.userId(), requestId,
                req.carrierCode(), req.trackingNo()));
    }

    /** 卖家确认收货 */
    @PostMapping("/{id}/confirm-return")
    public ApiResponse<AfterSaleRequest> confirmReturn(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long requestId) {
        return ApiResponse.ok(afterSaleService.confirmReturn(principal.userId(), requestId));
    }

    /** 卖家拒绝收货 */
    @PostMapping("/{id}/reject-return")
    public ApiResponse<AfterSaleRequest> rejectReturn(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long requestId,
            @RequestBody(required = false) RejectPayload req) {
        return ApiResponse.ok(afterSaleService.rejectReturn(principal.userId(), requestId,
                req != null ? req.note() : null));
    }

    /** 卖家上传举证材料 */
    @PostMapping("/{id}/seller-evidence")
    public ApiResponse<AfterSaleRequest> uploadSellerEvidence(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long requestId,
            @RequestBody EvidencePayload req) {
        return ApiResponse.ok(afterSaleService.uploadSellerEvidence(
                principal.userId(), requestId, req.evidence()));
    }

    /** 买家补充举证材料 */
    @PostMapping("/{id}/buyer-evidence")
    public ApiResponse<AfterSaleRequest> supplementBuyerEvidence(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long requestId,
            @RequestBody EvidencePayload req) {
        return ApiResponse.ok(afterSaleService.supplementBuyerEvidence(
                principal.userId(), requestId, req.evidence()));
    }

    /** 买家申请平台介入 */
    @PostMapping("/{id}/escalate")
    public ApiResponse<AfterSaleRequest> escalate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long requestId,
            @RequestBody(required = false) EscalatePayload req) {
        return ApiResponse.ok(afterSaleService.escalateToPlatform(
                principal.userId(), requestId,
                req != null ? req.evidence() : null));
    }

    /** 平台仲裁（管理员） */
    @PostMapping("/{id}/arbitrate")
    public ApiResponse<AfterSaleRequest> arbitrate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long requestId,
            @RequestBody ArbitratePayload req) {
        return ApiResponse.ok(afterSaleService.arbitrate(
                principal.userId(), requestId, req.result(), req.note()));
    }

    /** 买家取消售后 */
    @PostMapping("/{id}/cancel")
    public ApiResponse<AfterSaleRequest> cancel(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long requestId) {
        return ApiResponse.ok(afterSaleService.cancelByBuyer(principal.userId(), requestId));
    }

    /** 超时处理（定时任务/手动触发） */
    @PostMapping("/process-timeouts")
    public ApiResponse<String> processTimeouts() {
        afterSaleService.processTimeouts();
        return ApiResponse.ok("ok");
    }

    /** 售后单详情 */
    @GetMapping("/{id}")
    public ApiResponse<AfterSaleRequest> detail(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long requestId) {
        return ApiResponse.ok(afterSaleService.get(principal.userId(), requestId));
    }

    /** 某订单的售后记录 */
    @GetMapping("/by-order/{orderId}")
    public ApiResponse<List<AfterSaleRequest>> listByOrder(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable long orderId) {
        return ApiResponse.ok(afterSaleService.listByOrder(principal.userId(), orderId));
    }

    /** 买家的售后列表 */
    @GetMapping("/my-requests")
    public ApiResponse<List<AfterSaleRequest>> myRequests(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(afterSaleService.listByBuyer(principal.userId()));
    }

    /** 卖家收到的售后 */
    @GetMapping("/my-received")
    public ApiResponse<List<AfterSaleRequest>> myReceived(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(afterSaleService.listBySeller(principal.userId()));
    }

    /** 管理员：所有售后单 */
    @GetMapping("/all")
    public ApiResponse<List<AfterSaleRequest>> all(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "100") int size) {
        return ApiResponse.ok(afterSaleService.listAll(size));
    }

    // ---- payloads ----

    record RequestPayload(long orderId, AfterSaleType type, String reason,
                          Integer refundAmountCent, String buyerEvidence) {}
    record EvidencePayload(String evidence) {}
    record RejectPayload(String note) {}
    record ReturnShipPayload(String carrierCode, String trackingNo) {}
    record EscalatePayload(String evidence) {}
    record ArbitratePayload(String result, String note) {}
}
