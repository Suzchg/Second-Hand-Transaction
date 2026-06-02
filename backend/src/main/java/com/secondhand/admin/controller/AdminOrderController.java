package com.secondhand.admin.controller;

import com.secondhand.common.ApiResponse;
import com.secondhand.common.AppException;
import com.secondhand.order.entity.Order;
import com.secondhand.order.entity.OrderStatus;
import com.secondhand.order.repository.OrderRepository;
import com.secondhand.order.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderRepository orderRepo;
    private final OrderService orderService;

    public AdminOrderController(OrderRepository orderRepo, OrderService orderService) {
        this.orderRepo = orderRepo;
        this.orderService = orderService;
    }

    /** 分页查询所有订单 */
    @GetMapping
    public ApiResponse<Page<Order>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status) {
        Page<Order> result;
        if (status != null) {
            result = orderRepo.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(page, size));
        } else {
            result = orderRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        }
        return ApiResponse.ok(result);
    }

    /** 订单详情 */
    @GetMapping("/{id}")
    public ApiResponse<OrderService.OrderDetail> detail(@PathVariable Long id) {
        Order order = orderRepo.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "订单不存在", HttpStatus.NOT_FOUND));
        // 管理员可以看任何订单，我们用一个通用的 userId 查询（绕过权限检查）
        return ApiResponse.ok(orderService.getOrderDetail(order.getBuyerId(), id));
    }

    /** 手动标记已支付 */
    @PostMapping("/{id}/mark-paid")
    public ApiResponse<Order> markPaid(@PathVariable Long id) {
        Order order = orderRepo.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "订单不存在", HttpStatus.NOT_FOUND));
        if (order.getStatus() != OrderStatus.WAIT_PAY) {
            throw new AppException("CONFLICT", "当前状态不允许标记支付", HttpStatus.CONFLICT);
        }
        order.setStatus(OrderStatus.WAIT_DELIVER);
        order.setPaidAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return ApiResponse.ok(orderRepo.save(order));
    }

    /** 取消订单 */
    @PostMapping("/{id}/cancel")
    public ApiResponse<Order> cancel(@PathVariable Long id) {
        Order order = orderRepo.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "订单不存在", HttpStatus.NOT_FOUND));
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new AppException("CONFLICT", "订单已结束，无法取消", HttpStatus.CONFLICT);
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return ApiResponse.ok(orderRepo.save(order));
    }
}
