package com.secondhand.aftersale.service;

import com.secondhand.aftersale.entity.*;
import com.secondhand.aftersale.repository.AfterSaleRepository;
import com.secondhand.common.AppException;
import com.secondhand.order.entity.Order;
import com.secondhand.order.entity.OrderStatus;
import com.secondhand.order.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 售后逻辑（闲鱼模式）。
 *
 * 规则：
 * - 待发货(WAIT_DELIVER)：买家申请 → 自动同意退款，无需退货（24h 异议期占位）
 * - 待收货(WAIT_RECEIVE)：买家申请 → 卖家 5 天响应 → 同意后买家寄回 → 卖家确认 → 退款
 * - 已完成(COMPLETED)：买家可在 7 天内申请 → 同待收货流程
 */
@Service
public class AfterSaleService {

    private final AfterSaleRepository afterSaleRepo;
    private final OrderRepository orderRepo;

    public AfterSaleService(AfterSaleRepository afterSaleRepo, OrderRepository orderRepo) {
        this.afterSaleRepo = afterSaleRepo;
        this.orderRepo = orderRepo;
    }

    public record RequestCommand(long orderId, AfterSaleType type, String reason, Integer refundAmountCent) {}

    @Transactional
    public AfterSaleRequest request(long buyerId, RequestCommand cmd) {
        Order order = orderRepo.findById(cmd.orderId())
                .orElseThrow(() -> new AppException("NOT_FOUND", "订单不存在", HttpStatus.NOT_FOUND));
        if (!order.getBuyerId().equals(buyerId)) {
            throw new AppException("FORBIDDEN", "无权对该订单发起售后", HttpStatus.FORBIDDEN);
        }

        OrderStatus s = order.getStatus();
        boolean canApply = s == OrderStatus.WAIT_DELIVER
                || s == OrderStatus.WAIT_RECEIVE
                || s == OrderStatus.COMPLETED;
        if (!canApply) {
            throw new AppException("CONFLICT", "当前订单状态不支持售后", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        AfterSaleRequest req = new AfterSaleRequest();
        req.setOrderId(order.getId());
        req.setBuyerId(buyerId);
        req.setSellerId(order.getSellerId());
        req.setRequestType(cmd.type());
        req.setReason(cmd.reason());
        req.setRefundAmountCent(cmd.refundAmountCent() != null ? cmd.refundAmountCent() : order.getAmountCent());
        req.setStatus(AfterSaleStatus.REQUESTED);
        req.setRequestedAt(now);
        req.setDeadlineAt(now.plusDays(5)); // 卖家 5 天响应期
        req.setCreatedAt(now);
        req.setUpdatedAt(now);
        AfterSaleRequest saved = afterSaleRepo.save(req);

        // 订单进入售后状态
        order.setStatus(OrderStatus.AFTER_SALE);
        order.setUpdatedAt(now);
        orderRepo.save(order);

        return saved;
    }

    @Transactional
    public AfterSaleRequest approve(long sellerId, long requestId) {
        AfterSaleRequest req = afterSaleRepo.findById(requestId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "售后单不存在", HttpStatus.NOT_FOUND));
        if (!req.getSellerId().equals(sellerId)) {
            throw new AppException("FORBIDDEN", "无权处理该售后", HttpStatus.FORBIDDEN);
        }
        if (req.getStatus() != AfterSaleStatus.REQUESTED) {
            throw new AppException("CONFLICT", "售后单状态不允许处理", HttpStatus.CONFLICT);
        }

        Order order = orderRepo.findById(req.getOrderId())
                .orElseThrow(() -> new AppException("NOT_FOUND", "订单不存在", HttpStatus.NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        OrderStatus orderStatus = order.getStatus();

        if (orderStatus == OrderStatus.WAIT_DELIVER) {
            // 待发货：卖家同意 = 直接退款，无需退货
            req.setStatus(AfterSaleStatus.REFUNDED);
            req.setHandledAt(now);
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(now);
        } else {
            // 待收货/已完成：卖家同意 = 等待买家寄回
            req.setStatus(AfterSaleStatus.APPROVED);
            req.setHandledAt(now);
            req.setDeadlineAt(now.plusDays(7)); // 买家 7 天内寄回
        }

        req.setUpdatedAt(now);
        order.setUpdatedAt(now);
        orderRepo.save(order);
        return afterSaleRepo.save(req);
    }

    @Transactional
    public AfterSaleRequest reject(long sellerId, long requestId, String note) {
        AfterSaleRequest req = afterSaleRepo.findById(requestId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "售后单不存在", HttpStatus.NOT_FOUND));
        if (!req.getSellerId().equals(sellerId)) {
            throw new AppException("FORBIDDEN", "无权处理该售后", HttpStatus.FORBIDDEN);
        }
        if (req.getStatus() != AfterSaleStatus.REQUESTED) {
            throw new AppException("CONFLICT", "售后单状态不允许处理", HttpStatus.CONFLICT);
        }

        Order order = orderRepo.findById(req.getOrderId())
                .orElseThrow(() -> new AppException("NOT_FOUND", "订单不存在", HttpStatus.NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        req.setStatus(AfterSaleStatus.REJECTED);
        req.setHandledAt(now);
        req.setSellerResponse(note);
        req.setUpdatedAt(now);

        // 恢复订单状态（回到售后申请前的状态）
        order.setStatus(OrderStatus.WAIT_RECEIVE); // 简化处理
        order.setUpdatedAt(now);
        orderRepo.save(order);

        return afterSaleRepo.save(req);
    }

    @Transactional
    public AfterSaleRequest returnShip(long buyerId, long requestId, String carrierCode, String trackingNo) {
        AfterSaleRequest req = afterSaleRepo.findById(requestId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "售后单不存在", HttpStatus.NOT_FOUND));
        if (!req.getBuyerId().equals(buyerId)) {
            throw new AppException("FORBIDDEN", "无权操作", HttpStatus.FORBIDDEN);
        }
        if (req.getStatus() != AfterSaleStatus.APPROVED) {
            throw new AppException("CONFLICT", "当前状态不允许寄回", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        req.setStatus(AfterSaleStatus.RETURN_SHIPPED);
        req.setReturnCarrierCode(carrierCode);
        req.setReturnTrackingNo(trackingNo);
        req.setUpdatedAt(now);
        return afterSaleRepo.save(req);
    }

    @Transactional
    public AfterSaleRequest confirmReturn(long sellerId, long requestId) {
        AfterSaleRequest req = afterSaleRepo.findById(requestId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "售后单不存在", HttpStatus.NOT_FOUND));
        if (!req.getSellerId().equals(sellerId)) {
            throw new AppException("FORBIDDEN", "无权操作", HttpStatus.FORBIDDEN);
        }
        if (req.getStatus() != AfterSaleStatus.RETURN_SHIPPED) {
            throw new AppException("CONFLICT", "当前状态不允许确认", HttpStatus.CONFLICT);
        }

        Order order = orderRepo.findById(req.getOrderId())
                .orElseThrow(() -> new AppException("NOT_FOUND", "订单不存在", HttpStatus.NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        req.setStatus(AfterSaleStatus.REFUNDED);
        req.setUpdatedAt(now);

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(now);
        order.setUpdatedAt(now);
        orderRepo.save(order);

        return afterSaleRepo.save(req);
    }

    @Transactional(readOnly = true)
    public AfterSaleRequest get(long userId, long requestId) {
        AfterSaleRequest req = afterSaleRepo.findById(requestId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "售后单不存在", HttpStatus.NOT_FOUND));
        if (!req.getBuyerId().equals(userId) && !req.getSellerId().equals(userId)) {
            throw new AppException("FORBIDDEN", "无权查看该售后单", HttpStatus.FORBIDDEN);
        }
        return req;
    }
}
