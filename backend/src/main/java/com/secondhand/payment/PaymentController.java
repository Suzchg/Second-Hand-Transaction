package com.secondhand.payment;

import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.common.ApiResponse;
import com.secondhand.order.entity.Order;
import com.secondhand.order.entity.OrderStatus;
import com.secondhand.order.repository.OrderRepository;
import com.secondhand.common.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final MockPaymentService mockService;
    private final OrderRepository orderRepo;

    public PaymentController(PaymentService paymentService, MockPaymentService mockService,
                             OrderRepository orderRepo) {
        this.paymentService = paymentService;
        this.mockService = mockService;
        this.orderRepo = orderRepo;
    }

    /** 创建支付 */
    @PostMapping
    public ApiResponse<PaymentService.PaymentResult> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody CreatePaymentRequest req) {
        Order order = orderRepo.findById(req.orderId())
                .orElseThrow(() -> new AppException("NOT_FOUND", "订单不存在", HttpStatus.NOT_FOUND));
        if (!order.getBuyerId().equals(principal.userId())) {
            throw new AppException("FORBIDDEN", "无权操作", HttpStatus.FORBIDDEN);
        }
        var cmd = new PaymentService.CreatePaymentCommand(
                order.getId(), principal.userId(), order.getAmountCent(),
                req.method() != null ? req.method() : PaymentService.PaymentMethod.ALIPAY,
                "二手交易订单 #" + order.getId());
        return ApiResponse.ok(paymentService.createPayment(cmd));
    }

    /** 查询支付状态 */
    @GetMapping("/{paymentNo}")
    public ApiResponse<PaymentService.PaymentStatus> query(@PathVariable String paymentNo) {
        return ApiResponse.ok(paymentService.queryPayment(paymentNo));
    }

    /** Mock：模拟支付成功（开发环境） */
    @PostMapping("/{paymentNo}/mock-pay")
    public ApiResponse<String> mockPay(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String paymentNo,
            @RequestParam Long orderId) {
        mockService.mockPay(paymentNo);
        // 更新订单状态
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "订单不存在", HttpStatus.NOT_FOUND));
        if (order.getStatus() == OrderStatus.WAIT_PAY) {
            order.setStatus(OrderStatus.WAIT_DELIVER);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepo.save(order);
        }
        return ApiResponse.ok("支付成功");
    }

    record CreatePaymentRequest(Long orderId, PaymentService.PaymentMethod method) {}
}
