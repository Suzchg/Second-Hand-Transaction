package com.secondhand.chat.service;

import com.secondhand.comment.entity.Comment;
import com.secondhand.comment.repository.CommentRepository;
import com.secondhand.order.entity.Order;
import com.secondhand.order.entity.OrderEvent;
import com.secondhand.order.repository.OrderEventRepository;
import com.secondhand.order.repository.OrderRepository;
import com.secondhand.product.entity.Product;
import com.secondhand.product.service.ProductService;
import com.secondhand.report.entity.Report;
import com.secondhand.report.entity.ReportStatus;
import com.secondhand.report.repository.ReportRepository;
import com.secondhand.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MessageCenterService {

    private final OrderRepository orderRepo;
    private final OrderEventRepository orderEventRepo;
    private final CommentRepository commentRepo;
    private final ReportRepository reportRepo;
    private final ProductService productService;
    private final UserService userService;

    public MessageCenterService(OrderRepository orderRepo,
                                 OrderEventRepository orderEventRepo,
                                 CommentRepository commentRepo,
                                 ReportRepository reportRepo,
                                 ProductService productService,
                                 UserService userService) {
        this.orderRepo = orderRepo;
        this.orderEventRepo = orderEventRepo;
        this.commentRepo = commentRepo;
        this.reportRepo = reportRepo;
        this.productService = productService;
        this.userService = userService;
    }

    public record SystemMessage(String id, String type, String title, String content,
                                 String relatedId, LocalDateTime time) {}

    public record CommentNotification(Long id, Long productId, String productTitle,
                                        Long commenterId, String commenterName,
                                        String commenterAvatar, String content,
                                        LocalDateTime time) {}

    /** 获取用户的系统消息（订单状态变更 + 举报处理通知） */
    @Transactional(readOnly = true)
    public List<SystemMessage> getSystemMessages(Long userId) {
        List<SystemMessage> messages = new ArrayList<>();

        // 1. 订单事件
        List<Order> orders = orderRepo.findByBuyerIdOrSellerIdOrderByCreatedAtDesc(userId, userId);
        if (!orders.isEmpty()) {
            List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
            List<OrderEvent> events = orderEventRepo.findByOrderIdInOrderByCreatedAtDesc(orderIds);
            for (OrderEvent ev : events) {
                messages.add(new SystemMessage(
                        "order-" + ev.getId(),
                        "order_event",
                        "订单 #" + ev.getOrderId(),
                        ev.getNote(),
                        String.valueOf(ev.getOrderId()),
                        ev.getCreatedAt()
                ));
            }
        }

        // 2. 举报通知 — 我提交的举报被处理
        List<Report> myReports = reportRepo.findByReporterIdOrderByCreatedAtDesc(userId);
        for (Report r : myReports) {
            if (r.getStatus() == ReportStatus.PENDING) continue;
            String productTitle = getProductTitle(r.getProductId());
            if (r.getStatus() == ReportStatus.HANDLED) {
                String note = r.getHandleNote() != null ? "（处理备注：" + r.getHandleNote() + "）" : "";
                messages.add(new SystemMessage(
                        "report-" + r.getId(),
                        "report_handled",
                        "举报已处理",
                        "您对「" + productTitle + "」的举报已被处理，该商品已下架或要求整改" + note,
                        String.valueOf(r.getProductId()),
                        r.getHandledAt()
                ));
            } else if (r.getStatus() == ReportStatus.DISMISSED) {
                String note = r.getHandleNote() != null ? "（驳回原因：" + r.getHandleNote() + "）" : "";
                messages.add(new SystemMessage(
                        "report-" + r.getId(),
                        "report_dismissed",
                        "举报被驳回",
                        "您对「" + productTitle + "」的举报已被驳回" + note,
                        String.valueOf(r.getProductId()),
                        r.getHandledAt()
                ));
            }
        }

        // 3. 举报通知 — 我的商品被举报并被处理
        List<Product> myProducts = productService.getMyProducts(userId, 0, 200).getContent();
        if (!myProducts.isEmpty()) {
            List<Long> myProductIds = myProducts.stream().map(Product::getId).collect(Collectors.toList());
            List<Report> reportsOnMyProducts = reportRepo.findByProductIdInOrderByCreatedAtDesc(myProductIds);
            for (Report r : reportsOnMyProducts) {
                // 跳过我自己举报自己的（已在上面处理）
                if (r.getReporterId().equals(userId)) continue;
                if (r.getStatus() == ReportStatus.PENDING) continue;

                String productTitle = getProductTitle(r.getProductId());
                if (r.getStatus() == ReportStatus.HANDLED) {
                    String note = r.getHandleNote() != null ? "（处理备注：" + r.getHandleNote() + "）" : "";
                    messages.add(new SystemMessage(
                            "report-owned-" + r.getId(),
                            "report_product_handled",
                            "您的商品被举报处理",
                            "您的商品「" + productTitle + "」因被举报已被下架或要求整改，请检查商品状态" + note,
                            String.valueOf(r.getProductId()),
                            r.getHandledAt()
                    ));
                }
            }
        }

        // 按时间倒序，限制最近 50 条
        messages.sort((a, b) -> b.time().compareTo(a.time()));
        if (messages.size() > 50) {
            messages = messages.subList(0, 50);
        }
        return messages;
    }

    private String getProductTitle(Long productId) {
        try { return productService.getById(productId).getTitle(); }
        catch (Exception e) { return "商品 #" + productId; }
    }

    /** 获取用户商品的评论通知（别人评论了用户的商品） */
    @Transactional(readOnly = true)
    public List<CommentNotification> getCommentNotifications(Long userId) {
        // 获取用户所有商品 ID
        List<Product> myProducts = productService.getMyProducts(userId, 0, 200).getContent();
        if (myProducts.isEmpty()) return Collections.emptyList();

        List<Long> myProductIds = myProducts.stream().map(Product::getId).collect(Collectors.toList());

        // 查询这些商品下的评论，排除自己的评论
        List<Comment> comments = commentRepo.findByProductIdInAndUserIdNotOrderByCreatedAtDesc(myProductIds, userId);

        // 限制最近 50 条
        List<CommentNotification> result = new ArrayList<>();
        Map<Long, Product> productMap = myProducts.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        Map<Long, UserService.PublicUserDto> userCache = new HashMap<>();

        for (Comment c : comments) {
            Product p = productMap.get(c.getProductId());
            if (p == null) continue;

            UserService.PublicUserDto commenter = userCache.computeIfAbsent(c.getUserId(), id -> {
                try { return userService.getPublicInfo(id); }
                catch (Exception e) { return null; }
            });

            result.add(new CommentNotification(
                    c.getId(),
                    c.getProductId(),
                    p.getTitle(),
                    c.getUserId(),
                    commenter != null ? commenter.nickname() : "用户 #" + c.getUserId(),
                    commenter != null ? commenter.avatarUrl() : null,
                    c.getContent(),
                    c.getCreatedAt()
            ));
            if (result.size() >= 50) break;
        }
        return result;
    }
}
