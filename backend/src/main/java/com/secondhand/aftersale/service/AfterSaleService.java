package com.secondhand.aftersale.service;

import com.secondhand.aftersale.entity.*;
import com.secondhand.aftersale.repository.AfterSaleRepository;
import com.secondhand.common.AppException;
import com.secondhand.order.entity.Order;
import com.secondhand.order.entity.OrderStatus;
import com.secondhand.order.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 售后逻辑（二手交易平台模式）。
 *
 * 平台作为信息中介，不对商品质量兜底。售后基于双方协商+平台仲裁。
 *
 * 四大售后类型：
 * 1. REFUND_NOT_SHIPPED  — 仅退款（未发货/未签收）
 * 2. REFUND_RECEIVED     — 仅退款（已收货不退货）
 * 3. RETURN_REFUND       — 退货退款（核心链路，四段超时）
 * 4. PARTIAL_REFUND      — 部分退款（留用折价）
 *
 * 时效规则：
 * - 卖家审核：72h（3自然天）
 * - 买家寄件：7自然天
 * - 卖家确认收货：10自然天
 * - 确认收货后售后窗口：15自然天
 * - 卖家拒绝后买家申诉：3自然天
 */
@Service
public class AfterSaleService {

    private final AfterSaleRepository afterSaleRepo;
    private final OrderRepository orderRepo;

    public AfterSaleService(AfterSaleRepository afterSaleRepo, OrderRepository orderRepo) {
        this.afterSaleRepo = afterSaleRepo;
        this.orderRepo = orderRepo;
    }

    public record RequestCommand(long orderId, AfterSaleType type, String reason,
                                  Integer refundAmountCent, String buyerEvidence) {}

    // ==================== 发起售后 ====================

    @Transactional
    public AfterSaleRequest request(long buyerId, RequestCommand cmd) {
        Order order = orderRepo.findById(cmd.orderId())
                .orElseThrow(() -> new AppException("NOT_FOUND", "订单不存在", HttpStatus.NOT_FOUND));
        if (!order.getBuyerId().equals(buyerId)) {
            throw new AppException("FORBIDDEN", "无权对该订单发起售后", HttpStatus.FORBIDDEN);
        }

        // 售后规则：仅确认收货后7天内可手动发起；收货前不允手动发起（由超时自动触发）
        if (order.getCompletedAt() == null) {
            throw new AppException("FORBIDDEN", "确认收货前无法手动发起售后，超时后将自动触发", HttpStatus.FORBIDDEN);
        }
        if (order.getCompletedAt().plusDays(7).isBefore(LocalDateTime.now())) {
            throw new AppException("CLOSED", "已超过7天售后窗口期，平台不再受理", HttpStatus.GONE);
        }

        // 检查是否已有进行中的售后单
        List<AfterSaleRequest> existing = afterSaleRepo.findByOrderIdAndStatusNotIn(
                cmd.orderId(), List.of(AfterSaleStatus.CLOSED, AfterSaleStatus.REFUNDED));
        if (!existing.isEmpty()) {
            throw new AppException("CONFLICT", "该订单已有进行中的售后", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        AfterSaleRequest req = new AfterSaleRequest();
        req.setOrderId(order.getId());
        req.setBuyerId(buyerId);
        req.setSellerId(order.getSellerId());
        req.setRequestType(cmd.type());
        req.setReason(cmd.reason());
        req.setBuyerEvidence(cmd.buyerEvidence());
        req.setStatus(AfterSaleStatus.REQUESTED);
        req.setDeadlineAt(now.plusDays(3)); // 卖家72h处理时效
        req.setOrderCompletedAt(order.getCompletedAt());
        req.setRequestedAt(now);
        req.setCreatedAt(now);
        req.setUpdatedAt(now);

        // 退款金额：优先用买家指定金额，否则订单全额
        if (cmd.refundAmountCent() != null && cmd.refundAmountCent() > 0) {
            req.setRefundAmountCent(Math.min(cmd.refundAmountCent(), order.getAmountCent()));
        } else {
            req.setRefundAmountCent(order.getAmountCent());
        }

        AfterSaleRequest saved = afterSaleRepo.save(req);

        // 标记订单为售后中
        if (order.getStatus() != OrderStatus.AFTER_SALE) {
            order.setStatus(OrderStatus.AFTER_SALE);
            order.setUpdatedAt(now);
            orderRepo.save(order);
        }

        return saved;
    }

    // ==================== 卖家同意 ====================

    @Transactional
    public AfterSaleRequest approve(long sellerId, long requestId) {
        AfterSaleRequest req = findOwnedBySeller(sellerId, requestId);
        if (req.getStatus() != AfterSaleStatus.REQUESTED
                && req.getStatus() != AfterSaleStatus.PLATFORM_ARBITRATION) {
            throw new AppException("CONFLICT", "当前状态不允许此操作", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        req.setHandledAt(now);
        req.setUpdatedAt(now);

        AfterSaleType type = req.getRequestType();
        if (type == AfterSaleType.REFUND_NOT_SHIPPED
                || type == AfterSaleType.REFUND_RECEIVED
                || type == AfterSaleType.PARTIAL_REFUND) {
            // 无需退货：直接退款
            req.setStatus(AfterSaleStatus.REFUNDED);
            req.setRefundedAt(now);
            closeOrder(req);
        } else {
            // 退货退款：进入待买家寄件阶段
            req.setStatus(AfterSaleStatus.APPROVED);
            req.setDeadlineAt(now.plusDays(7)); // 买家7天寄件时效
        }

        return afterSaleRepo.save(req);
    }

    // ==================== 卖家拒绝 ====================

    @Transactional
    public AfterSaleRequest reject(long sellerId, long requestId, String note) {
        AfterSaleRequest req = findOwnedBySeller(sellerId, requestId);
        if (req.getStatus() != AfterSaleStatus.REQUESTED
                && req.getStatus() != AfterSaleStatus.PLATFORM_ARBITRATION) {
            throw new AppException("CONFLICT", "当前状态不允许此操作", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        req.setStatus(AfterSaleStatus.REJECTED);
        req.setHandledAt(now);
        req.setSellerResponse(note);
        req.setDeadlineAt(now.plusDays(3)); // 买家3天内可申请平台介入
        req.setUpdatedAt(now);
        return afterSaleRepo.save(req);
    }

    // ==================== 买家寄回退货 ====================

    @Transactional
    public AfterSaleRequest returnShip(long buyerId, long requestId,
                                        String carrierCode, String trackingNo) {
        AfterSaleRequest req = findOwnedByBuyer(buyerId, requestId);
        if (req.getStatus() != AfterSaleStatus.APPROVED) {
            throw new AppException("CONFLICT", "当前状态不允许寄回", HttpStatus.CONFLICT);
        }
        if (req.getRequestType() != AfterSaleType.RETURN_REFUND) {
            throw new AppException("CONFLICT", "仅退货退款类型需要寄回", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        req.setStatus(AfterSaleStatus.RETURN_SHIPPED);
        req.setReturnCarrierCode(carrierCode);
        req.setReturnTrackingNo(trackingNo);
        req.setReturnedAt(now);
        req.setDeadlineAt(now.plusDays(10)); // 卖家10天确认收货时效
        req.setUpdatedAt(now);
        return afterSaleRepo.save(req);
    }

    // ==================== 卖家确认收货（退货） ====================

    @Transactional
    public AfterSaleRequest confirmReturn(long sellerId, long requestId) {
        AfterSaleRequest req = findOwnedBySeller(sellerId, requestId);
        if (req.getStatus() != AfterSaleStatus.RETURN_SHIPPED) {
            throw new AppException("CONFLICT", "当前状态不允许确认收货", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        req.setStatus(AfterSaleStatus.REFUNDED);
        req.setRefundedAt(now);
        req.setUpdatedAt(now);
        closeOrder(req);
        return afterSaleRepo.save(req);
    }

    // ==================== 卖家拒绝收货（退货有问题） ====================

    @Transactional
    public AfterSaleRequest rejectReturn(long sellerId, long requestId, String note) {
        AfterSaleRequest req = findOwnedBySeller(sellerId, requestId);
        if (req.getStatus() != AfterSaleStatus.RETURN_SHIPPED) {
            throw new AppException("CONFLICT", "当前状态不允许此操作", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        req.setStatus(AfterSaleStatus.REJECTED);
        req.setSellerResponse(note);
        req.setDeadlineAt(now.plusDays(3)); // 买家可申请平台介入
        req.setUpdatedAt(now);
        return afterSaleRepo.save(req);
    }

    // ==================== 卖家上传举证 ====================

    @Transactional
    public AfterSaleRequest uploadSellerEvidence(long sellerId, long requestId, String evidence) {
        AfterSaleRequest req = findOwnedBySeller(sellerId, requestId);
        if (req.getStatus() == AfterSaleStatus.REFUNDED
                || req.getStatus() == AfterSaleStatus.CLOSED) {
            throw new AppException("CONFLICT", "售后已完结，无法上传举证", HttpStatus.CONFLICT);
        }
        req.setSellerEvidence(evidence);
        req.setUpdatedAt(LocalDateTime.now());
        return afterSaleRepo.save(req);
    }

    // ==================== 买家补充举证 ====================

    @Transactional
    public AfterSaleRequest supplementBuyerEvidence(long buyerId, long requestId, String evidence) {
        AfterSaleRequest req = findOwnedByBuyer(buyerId, requestId);
        if (req.getStatus() == AfterSaleStatus.REFUNDED
                || req.getStatus() == AfterSaleStatus.CLOSED) {
            throw new AppException("CONFLICT", "售后已完结，无法补充举证", HttpStatus.CONFLICT);
        }
        // 合并或替换买家证据
        if (evidence != null && !evidence.isBlank()) {
            String existing = req.getBuyerEvidence();
            if (existing != null && !existing.isBlank()) {
                req.setBuyerEvidence(existing + "\n---补充---\n" + evidence);
            } else {
                req.setBuyerEvidence(evidence);
            }
        }
        req.setUpdatedAt(LocalDateTime.now());
        return afterSaleRepo.save(req);
    }

    // ==================== 买家申请平台介入 ====================

    @Transactional
    public AfterSaleRequest escalateToPlatform(long buyerId, long requestId, String evidence) {
        AfterSaleRequest req = findOwnedByBuyer(buyerId, requestId);
        if (req.getStatus() != AfterSaleStatus.REJECTED) {
            throw new AppException("CONFLICT", "仅在卖家拒绝后可申请平台介入", HttpStatus.CONFLICT);
        }
        if (evidence != null && !evidence.isBlank()) {
            req.setBuyerEvidence(evidence);
        }
        LocalDateTime now = LocalDateTime.now();
        req.setStatus(AfterSaleStatus.PLATFORM_ARBITRATION);
        req.setDeadlineAt(now.plusDays(5)); // 平台3-5工作日
        req.setUpdatedAt(now);
        return afterSaleRepo.save(req);
    }

    // ==================== 平台仲裁（管理员专用，带责任判定和运费归属） ====================

    /**
     * 管理员仲裁售后单。
     *
     * @param adminId 管理员用户ID
     * @param requestId 售后单ID
     * @param cmd 仲裁命令（result / responsibility / shippingPaidBy / shippingCostCent / partialRefundCent / note）
     */
    @Transactional
    public AfterSaleRequest adminArbitrate(long adminId, long requestId, AdminArbitrateCommand cmd) {
        AfterSaleRequest req = afterSaleRepo.findById(requestId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "售后单不存在", HttpStatus.NOT_FOUND));
        if (req.getStatus() != AfterSaleStatus.PLATFORM_ARBITRATION) {
            throw new AppException("CONFLICT", "售后单未进入仲裁状态", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();

        // 设置责任归属
        if (cmd.responsibility() != null && !cmd.responsibility().isBlank()) {
            req.setResponsibility(cmd.responsibility());
        }

        // 设置运费归属
        if (cmd.shippingPaidBy() != null && !cmd.shippingPaidBy().isBlank()) {
            req.setShippingPaidBy(cmd.shippingPaidBy());
        }
        if (cmd.shippingCostCent() != null) {
            req.setShippingCostCent(cmd.shippingCostCent());
        }

        // 构建结构化仲裁结果
        StringBuilder arbResult = new StringBuilder();
        arbResult.append("裁决：");
        switch (cmd.result()) {
            case "FULL_REFUND" -> arbResult.append("全额退款");
            case "PARTIAL_REFUND" -> arbResult.append("部分折价退款");
            case "DISMISS" -> arbResult.append("驳回售后申请");
            case "RETURN_REFUND" -> arbResult.append("退货退款（平台裁定）");
            default -> arbResult.append(cmd.result());
        }
        if (cmd.responsibility() != null) {
            arbResult.append(" | 责任方：").append(responsibilityLabel(cmd.responsibility()));
        }
        if (cmd.shippingPaidBy() != null) {
            arbResult.append(" | 运费承担：").append(shippingPaidByLabel(cmd.shippingPaidBy()));
            if (cmd.shippingCostCent() != null && cmd.shippingCostCent() > 0) {
                arbResult.append(String.format(" ¥%.2f", cmd.shippingCostCent() / 100.0));
            }
        }
        if (cmd.note() != null && !cmd.note().isBlank()) {
            arbResult.append(" | 说明：").append(cmd.note());
        }
        req.setArbitrationResult(arbResult.toString());
        req.setHandledAt(now);
        req.setUpdatedAt(now);

        // 根据裁决结果更新售后状态
        switch (cmd.result()) {
            case "FULL_REFUND":
                req.setStatus(AfterSaleStatus.REFUNDED);
                req.setRefundedAt(now);
                closeOrderAfterSale(req);
                break;
            case "PARTIAL_REFUND":
                // 使用平台裁定的部分退款金额
                if (cmd.partialRefundCent() != null && cmd.partialRefundCent() > 0) {
                    req.setRefundAmountCent(cmd.partialRefundCent());
                }
                req.setStatus(AfterSaleStatus.REFUNDED);
                req.setRefundedAt(now);
                closeOrderAfterSale(req);
                break;
            case "RETURN_REFUND":
                // 平台裁定需要退货退款（推翻卖家拒绝决定）
                req.setStatus(AfterSaleStatus.APPROVED);
                req.setDeadlineAt(now.plusDays(7)); // 买家7天内寄回
                break;
            case "DISMISS":
                req.setStatus(AfterSaleStatus.CLOSED);
                req.setClosedAt(now);
                restoreOrder(req);
                break;
            default:
                throw new AppException("BAD_REQUEST", "无效的仲裁结果类型：" + cmd.result(), HttpStatus.BAD_REQUEST);
        }
        return afterSaleRepo.save(req);
    }

    /** 管理员仲裁命令 */
    public record AdminArbitrateCommand(
            String result,           // FULL_REFUND | PARTIAL_REFUND | DISMISS | RETURN_REFUND
            String responsibility,   // BUYER | SELLER | LOGISTICS
            String shippingPaidBy,   // BUYER | SELLER | PLATFORM
            Integer shippingCostCent,
            Integer partialRefundCent,
            String note) {}

    /** 旧版仲裁（保留兼容），实际委托给 adminArbitrate */
    @Transactional
    public AfterSaleRequest arbitrate(long adminId, long requestId,
                                       String result, String note) {
        return adminArbitrate(adminId, requestId,
                new AdminArbitrateCommand(result, null, null, null, null, note));
    }

    // ==================== 买家取消售后 ====================

    @Transactional
    public AfterSaleRequest cancelByBuyer(long buyerId, long requestId) {
        AfterSaleRequest req = findOwnedByBuyer(buyerId, requestId);
        if (req.getStatus() == AfterSaleStatus.REFUNDED
                || req.getStatus() == AfterSaleStatus.CLOSED) {
            throw new AppException("CONFLICT", "售后已完结，无法取消", HttpStatus.CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now();
        req.setStatus(AfterSaleStatus.CLOSED);
        req.setClosedAt(now);
        req.setUpdatedAt(now);
        restoreOrder(req);
        return afterSaleRepo.save(req);
    }

    // ==================== 超时自动处理（定时任务调用） ====================

    /**
     * 自动触发收货超时售后：WAIT_RECEIVE 状态超过14天未确认 → 自动发起仅退款
     */
    @Transactional
    public int autoTriggerTimeoutAfterSale() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(14);
        List<Order> stuckOrders = orderRepo.findByStatusAndShippedAtBefore(
                OrderStatus.WAIT_RECEIVE, deadline);
        int count = 0;
        for (Order order : stuckOrders) {
            // 检查是否已有售后单
            List<AfterSaleRequest> existing = afterSaleRepo.findByOrderIdAndStatusNotIn(
                    order.getId(), List.of(AfterSaleStatus.CLOSED, AfterSaleStatus.REFUNDED));
            if (!existing.isEmpty()) continue;

            LocalDateTime now = LocalDateTime.now();
            AfterSaleRequest req = new AfterSaleRequest();
            req.setOrderId(order.getId());
            req.setBuyerId(order.getBuyerId());
            req.setSellerId(order.getSellerId());
            req.setRequestType(AfterSaleType.REFUND_NOT_SHIPPED);
            req.setReason("订单超时14天未确认收货，系统自动发起售后");
            req.setStatus(AfterSaleStatus.REQUESTED);
            req.setDeadlineAt(now.plusDays(3));
            req.setRefundAmountCent(order.getAmountCent());
            req.setRequestedAt(now);
            req.setCreatedAt(now);
            req.setUpdatedAt(now);
            afterSaleRepo.save(req);

            order.setStatus(OrderStatus.AFTER_SALE);
            order.setUpdatedAt(now);
            orderRepo.save(order);
            count++;
        }
        return count;
    }

    @Transactional
    public void processTimeouts() {
        // 先处理收货超时自动售后
        autoTriggerTimeoutAfterSale();

        LocalDateTime now = LocalDateTime.now();
        List<AfterSaleRequest> expired = afterSaleRepo
                .findByStatusNotInAndDeadlineAtBefore(
                        List.of(AfterSaleStatus.REFUNDED, AfterSaleStatus.CLOSED), now);

        for (AfterSaleRequest req : expired) {
            switch (req.getStatus()) {
                case REQUESTED:
                    // 卖家超时72h未处理
                    if (req.getRequestType() == AfterSaleType.REFUND_NOT_SHIPPED) {
                        // 仅退款（未发货）：自动同意退款
                        req.setStatus(AfterSaleStatus.REFUNDED);
                        req.setRefundedAt(now);
                        closeOrder(req);
                    } else if (req.getRequestType() == AfterSaleType.RETURN_REFUND) {
                        // 退货退款：自动同意，生成退货地址
                        req.setStatus(AfterSaleStatus.APPROVED);
                        req.setDeadlineAt(now.plusDays(7)); // 买家7天寄件
                    }
                    // REFUND_RECEIVED / PARTIAL_REFUND 超时不自动退款
                    break;
                case REJECTED:
                    // 买家超时3天未申请平台介入 → 售后关闭
                    req.setStatus(AfterSaleStatus.CLOSED);
                    req.setClosedAt(now);
                    restoreOrder(req);
                    break;
                case APPROVED:
                    // 买家超时7天未寄件 → 售后关闭
                    req.setStatus(AfterSaleStatus.CLOSED);
                    req.setClosedAt(now);
                    restoreOrder(req);
                    break;
                case RETURN_SHIPPED:
                    // 卖家超时10天未确认收货 → 自动退款
                    req.setStatus(AfterSaleStatus.REFUNDED);
                    req.setRefundedAt(now);
                    closeOrder(req);
                    break;
                default:
                    break;
            }
            req.setUpdatedAt(now);
            afterSaleRepo.save(req);
        }
    }

    // ==================== 查询 ====================

    @Transactional(readOnly = true)
    public AfterSaleRequest get(long userId, long requestId) {
        AfterSaleRequest req = afterSaleRepo.findById(requestId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "售后单不存在", HttpStatus.NOT_FOUND));
        if (!req.getBuyerId().equals(userId) && !req.getSellerId().equals(userId)) {
            throw new AppException("FORBIDDEN", "无权查看该售后单", HttpStatus.FORBIDDEN);
        }
        return req;
    }

    @Transactional(readOnly = true)
    public List<AfterSaleRequest> listByOrder(long userId, long orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "订单不存在", HttpStatus.NOT_FOUND));
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new AppException("FORBIDDEN", "无权查看", HttpStatus.FORBIDDEN);
        }
        return afterSaleRepo.findByOrderIdOrderByCreatedAtDesc(orderId);
    }

    @Transactional(readOnly = true)
    public List<AfterSaleRequest> listByBuyer(long buyerId) {
        return afterSaleRepo.findByBuyerIdOrderByCreatedAtDesc(buyerId);
    }

    @Transactional(readOnly = true)
    public List<AfterSaleRequest> listBySeller(long sellerId) {
        return afterSaleRepo.findBySellerIdOrderByCreatedAtDesc(sellerId);
    }

    @Transactional(readOnly = true)
    public List<AfterSaleRequest> listAll(int size) {
        return afterSaleRepo.findAllByOrderByCreatedAtDesc(
                org.springframework.data.domain.PageRequest.of(0, Math.min(size, 200)))
                .getContent();
    }

    // ==================== 管理员专用查询 ====================

    @Transactional(readOnly = true)
    public Page<AfterSaleRequest> listAllAdmin(int page, int size,
                                                AfterSaleStatus status, AfterSaleType type) {
        if (status != null) {
            return afterSaleRepo.findByStatusOrderByCreatedAtDesc(
                    status, org.springframework.data.domain.PageRequest.of(page, size));
        }
        if (type != null) {
            return afterSaleRepo.findByRequestTypeOrderByCreatedAtDesc(
                    type, org.springframework.data.domain.PageRequest.of(page, size));
        }
        return afterSaleRepo.findAllByOrderByCreatedAtDesc(
                org.springframework.data.domain.PageRequest.of(page, Math.min(size, 200)));
    }

    @Transactional(readOnly = true)
    public AfterSaleRequest getAdmin(long requestId) {
        return afterSaleRepo.findById(requestId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "售后单不存在", HttpStatus.NOT_FOUND));
    }

    // ==================== 内部方法 ====================

    private AfterSaleRequest findOwnedBySeller(long sellerId, long requestId) {
        AfterSaleRequest req = afterSaleRepo.findById(requestId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "售后单不存在", HttpStatus.NOT_FOUND));
        if (!req.getSellerId().equals(sellerId)) {
            throw new AppException("FORBIDDEN", "无权处理该售后", HttpStatus.FORBIDDEN);
        }
        return req;
    }

    private AfterSaleRequest findOwnedByBuyer(long buyerId, long requestId) {
        AfterSaleRequest req = afterSaleRepo.findById(requestId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "售后单不存在", HttpStatus.NOT_FOUND));
        if (!req.getBuyerId().equals(buyerId)) {
            throw new AppException("FORBIDDEN", "无权操作该售后", HttpStatus.FORBIDDEN);
        }
        return req;
    }

    /**
     * 售后退款完成后关闭订单。
     * 全额退款 → CANCELLED（订单作废）
     * 部分退款 → 订单保持 COMPLETED（买家留用但折价补偿）
     */
    private void closeOrderAfterSale(AfterSaleRequest req) {
        orderRepo.findById(req.getOrderId()).ifPresent(order -> {
            Order originalOrder = getOrderOrNull(req.getOrderId());
            if (originalOrder == null) return;

            // 部分退款：订单保留为 COMPLETED（买家自留商品），后续正常结算
            if (req.getRefundAmountCent() != null
                    && originalOrder.getAmountCent() != null
                    && req.getRefundAmountCent() < originalOrder.getAmountCent()) {
                order.setStatus(OrderStatus.COMPLETED);
            } else {
                // 全额退款：订单作废
                order.setStatus(OrderStatus.CANCELLED);
                order.setCancelledAt(LocalDateTime.now());
            }
            order.setUpdatedAt(LocalDateTime.now());
            orderRepo.save(order);
        });
    }

    /** 保留旧方法名以兼容内部调用 */
    private void closeOrder(AfterSaleRequest req) {
        closeOrderAfterSale(req);
    }

    private void restoreOrder(AfterSaleRequest req) {
        orderRepo.findById(req.getOrderId()).ifPresent(order -> {
            // 售后被驳回/关闭 → 恢复到售后前状态
            if (order.getPaidAt() != null && order.getCompletedAt() != null) {
                order.setStatus(OrderStatus.COMPLETED);
            } else if (order.getPaidAt() != null && order.getShippedAt() != null) {
                order.setStatus(OrderStatus.WAIT_RECEIVE);
            } else if (order.getPaidAt() != null) {
                order.setStatus(OrderStatus.WAIT_DELIVER);
            } else {
                order.setStatus(OrderStatus.WAIT_PAY);
            }
            order.setUpdatedAt(LocalDateTime.now());
            orderRepo.save(order);
        });
    }

    private Order getOrderOrNull(long orderId) {
        return orderRepo.findById(orderId).orElse(null);
    }

    private String responsibilityLabel(String r) {
        return switch (r) {
            case "BUYER" -> "买家";
            case "SELLER" -> "卖家";
            case "LOGISTICS" -> "物流";
            default -> r;
        };
    }

    private String shippingPaidByLabel(String s) {
        return switch (s) {
            case "BUYER" -> "买家承担";
            case "SELLER" -> "卖家承担";
            case "PLATFORM" -> "平台承担";
            default -> s;
        };
    }
}
