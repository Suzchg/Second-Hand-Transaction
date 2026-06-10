package com.secondhand.order.controller;

import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.common.ApiResponse;
import com.secondhand.order.entity.Order;
import com.secondhand.order.entity.OrderStatus;
import com.secondhand.order.entity.Shipment;
import com.secondhand.order.repository.OrderRepository;
import com.secondhand.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderRepository orderRepo;

    public OrderController(OrderService orderService, OrderRepository orderRepo) {
        this.orderService = orderService;
        this.orderRepo = orderRepo;
    }

    /** 我卖出的 */
    @GetMapping("/sold")
    public ApiResponse<Page<Order>> mySold(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status) {
        if (status != null) {
            return ApiResponse.ok(orderRepo.findBySellerIdAndStatusOrderByCreatedAtDesc(
                    principal.userId(), status, PageRequest.of(page, Math.min(size, 50))));
        }
        return ApiResponse.ok(orderRepo.findBySellerIdOrderByCreatedAtDesc(
                principal.userId(), PageRequest.of(page, Math.min(size, 50))));
    }

    /** 我买到的 */
    @GetMapping("/bought")
    public ApiResponse<Page<Order>> myBought(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status) {
        if (status != null) {
            return ApiResponse.ok(orderRepo.findByBuyerIdAndStatusOrderByCreatedAtDesc(
                    principal.userId(), status, PageRequest.of(page, Math.min(size, 50))));
        }
        return ApiResponse.ok(orderRepo.findByBuyerIdOrderByCreatedAtDesc(
                principal.userId(), PageRequest.of(page, Math.min(size, 50))));
    }

    @PostMapping
    public ApiResponse<Order> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateOrderRequest req) {
        return ApiResponse.ok(orderService.createOrder(principal.userId(),
                new OrderService.CreateOrderCommand(req.productId(), req.receiverName(),
                        req.receiverPhone(), req.receiverAddress(), req.addressId())));
    }

    /** 支付订单（替代 mark-paid） */
    @PostMapping("/{id}/pay")
    public ApiResponse<Order> pay(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long orderId) {
        return ApiResponse.ok(orderService.pay(principal.userId(), orderId));
    }

    /** @deprecated 兼容旧端点，请使用 /pay */
    @PostMapping("/{id}/mark-paid")
    public ApiResponse<Order> markPaid(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long orderId) {
        return ApiResponse.ok(orderService.markPaid(principal.userId(), orderId));
    }

    @PostMapping("/{id}/ship")
    public ApiResponse<Shipment> ship(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long orderId,
            @Valid @RequestBody ShipRequest req) {
        return ApiResponse.ok(orderService.ship(principal.userId(), orderId,
                new OrderService.ShipCommand(req.carrierCode(), req.trackingNo())));
    }

    /** 确认收货 */
    @PostMapping("/{id}/confirm")
    public ApiResponse<Order> confirm(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long orderId) {
        return ApiResponse.ok(orderService.confirmReceived(principal.userId(), orderId));
    }

    /** 取消订单 */
    @PostMapping("/{id}/cancel")
    public ApiResponse<Order> cancel(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long orderId) {
        return ApiResponse.ok(orderService.cancel(principal.userId(), orderId));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderService.OrderDetail> detail(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long orderId) {
        return ApiResponse.ok(orderService.getOrderDetail(principal.userId(), orderId));
    }

    /** 更新订单收货信息（买家在报价接受后补填） */
    @PutMapping("/{id}/receiver")
    public ApiResponse<Order> updateReceiver(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long orderId,
            @Valid @RequestBody UpdateReceiverRequest req) {
        return ApiResponse.ok(orderService.updateReceiver(principal.userId(), orderId,
                req.receiverName(), req.receiverPhone(), req.receiverAddress()));
    }

    /** 手动触发结算（管理员/定时任务） */
    @PostMapping("/process-settlements")
    public ApiResponse<Integer> processSettlements() {
        return ApiResponse.ok(orderService.processSettlements());
    }

    record CreateOrderRequest(long productId,
                              @NotBlank String receiverName,
                              @NotBlank String receiverPhone,
                              @NotBlank String receiverAddress,
                              Long addressId) {}

    record ShipRequest(@NotBlank String carrierCode,
                       @NotBlank String trackingNo) {}

    record UpdateReceiverRequest(@NotBlank String receiverName,
                                 @NotBlank String receiverPhone,
                                 @NotBlank String receiverAddress) {}
}
