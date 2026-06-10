package com.secondhand.admin.controller;

import com.secondhand.aftersale.entity.AfterSaleRequest;
import com.secondhand.aftersale.entity.AfterSaleStatus;
import com.secondhand.aftersale.entity.AfterSaleType;
import com.secondhand.aftersale.service.AfterSaleService;
import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.common.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员售后管理接口。
 * 路径 /api/admin/** 已由 SecurityConfig 自动保护，仅 ADMIN 角色可访问。
 */
@RestController
@RequestMapping("/api/admin/after-sale")
public class AdminAfterSaleController {

    private final AfterSaleService afterSaleService;

    public AdminAfterSaleController(AfterSaleService afterSaleService) {
        this.afterSaleService = afterSaleService;
    }

    /**
     * 售后单列表（支持按状态/类型筛选，分页）。
     * 使用 String 接收参数，手动转换为枚举，避免 Spring MVC 对 null 枚举的绑定问题。
     */
    @GetMapping
    public ApiResponse<Page<AfterSaleRequest>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        AfterSaleStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try { statusEnum = AfterSaleStatus.valueOf(status); }
            catch (IllegalArgumentException ignored) { /* 非法值视为 null */ }
        }
        AfterSaleType typeEnum = null;
        if (type != null && !type.isBlank()) {
            try { typeEnum = AfterSaleType.valueOf(type); }
            catch (IllegalArgumentException ignored) { /* 非法值视为 null */ }
        }
        return ApiResponse.ok(afterSaleService.listAllAdmin(page, size, statusEnum, typeEnum));
    }

    /** 售后单详情（管理员可查看任意售后单，无所有权检查） */
    @GetMapping("/{id}")
    public ApiResponse<AfterSaleRequest> detail(@PathVariable long id) {
        return ApiResponse.ok(afterSaleService.getAdmin(id));
    }

    /** 平台仲裁（管理员专用，含责任判定和运费归属） */
    @PostMapping("/{id}/arbitrate")
    public ApiResponse<AfterSaleRequest> arbitrate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long requestId,
            @RequestBody ArbitratePayload req) {
        return ApiResponse.ok(afterSaleService.adminArbitrate(
                principal.userId(), requestId,
                new AfterSaleService.AdminArbitrateCommand(
                        req.result(), req.responsibility(),
                        req.shippingPaidBy(), req.shippingCostCent(),
                        req.partialRefundCent(), req.note())));
    }

    /** 手动触发超时处理 */
    @PostMapping("/process-timeouts")
    public ApiResponse<String> processTimeouts() {
        afterSaleService.processTimeouts();
        return ApiResponse.ok("ok");
    }

    // ---- payload ----

    record ArbitratePayload(
            String result,           // FULL_REFUND | PARTIAL_REFUND | DISMISS | RETURN_REFUND
            String responsibility,   // BUYER | SELLER | LOGISTICS
            String shippingPaidBy,   // BUYER | SELLER | PLATFORM
            Integer shippingCostCent,
            Integer partialRefundCent,
            String note) {}
}
