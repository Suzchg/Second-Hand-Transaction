package com.secondhand.order.service;

import com.secondhand.common.AppException;
import com.secondhand.order.entity.*;
import com.secondhand.order.repository.*;
import com.secondhand.product.entity.Product;
import com.secondhand.product.entity.ProductStatus;
import com.secondhand.product.service.ProductService;
import com.secondhand.rating.entity.Rating;
import com.secondhand.rating.repository.RatingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepo;
    private final OrderEventRepository orderEventRepo;
    private final ShipmentRepository shipmentRepo;
    private final ProductService productService;
    private final RatingRepository ratingRepo;

    public OrderService(OrderRepository orderRepo, OrderEventRepository orderEventRepo,
                        ShipmentRepository shipmentRepo, ProductService productService,
                        RatingRepository ratingRepo) {
        this.orderRepo = orderRepo;
        this.orderEventRepo = orderEventRepo;
        this.shipmentRepo = shipmentRepo;
        this.productService = productService;
        this.ratingRepo = ratingRepo;
    }

    public record CreateOrderCommand(long productId, String receiverName, String receiverPhone, String receiverAddress, Long addressId) {}
    public record ShipCommand(String carrierCode, String trackingNo) {}
    public record OrderDetail(Order order, Shipment shipment, List<OrderEvent> events,
                              boolean canPay, boolean canShip, boolean canConfirm,
                              boolean canCancel, boolean canApplyAfterSale) {}

    public record SoldProductDto(Long orderId, Long productId, String productTitle,
                                 String productCover, Integer priceCent,
                                 LocalDateTime completedAt, Integer ratingScore,
                                 String ratingComment) {}

    /** 卖家已售出的订单（已完成），含评分 */
    @Transactional(readOnly = true)
    public List<SoldProductDto> getSellerSoldProducts(Long sellerId) {
        List<Order> completed = orderRepo.findBySellerIdAndStatusOrderByCreatedAtDesc(
                sellerId, OrderStatus.COMPLETED, org.springframework.data.domain.Pageable.unpaged())
                .getContent();
        List<SoldProductDto> result = new ArrayList<>();
        for (Order o : completed) {
            Product p;
            try { p = productService.getById(o.getProductId()); }
            catch (Exception e) { continue; }

            Rating rating = ratingRepo.findByOrderId(o.getId()).orElse(null);

            result.add(new SoldProductDto(
                    o.getId(), o.getProductId(), p.getTitle(), p.getCoverImageUrl(),
                    o.getAmountCent(), o.getCompletedAt(),
                    rating != null ? rating.getScore() : null,
                    rating != null ? rating.getComment() : null
            ));
        }
        return result;
    }

    /** 以商品原价下单 */
    @Transactional
    public Order createOrder(long buyerId, CreateOrderCommand cmd) {
        Product product = productService.getById(cmd.productId());
        return createOrderInternal(buyerId, cmd, product.getPriceCent());
    }

    /** 以指定价格下单（卖家接受报价时用） */
    @Transactional
    public Order createOrderWithPrice(long buyerId, CreateOrderCommand cmd, int amountCent) {
        return createOrderInternal(buyerId, cmd, amountCent);
    }

    private Order createOrderInternal(long buyerId, CreateOrderCommand cmd, int amountCent) {
        Product product = productService.getById(cmd.productId());
        if (product.getSellerId().equals(buyerId)) {
            throw new AppException("FORBIDDEN", "不能购买自己的商品", HttpStatus.FORBIDDEN);
        }
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new AppException("CONFLICT", "商品当前不可购买", HttpStatus.CONFLICT);
        }
        if (product.getQuantity() == null || product.getQuantity() < 1) {
            throw new AppException("CONFLICT", "商品已售罄", HttpStatus.CONFLICT);
        }

        // 减库存，卖完自动下架
        product.setQuantity(product.getQuantity() - 1);
        if (product.getQuantity() == 0) {
            product.setStatus(ProductStatus.OFF_SALE);
        }
        product.setUpdatedAt(LocalDateTime.now());

        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();
        order.setBuyerId(buyerId);
        order.setSellerId(product.getSellerId());
        order.setProductId(product.getId());
        order.setAmountCent(amountCent);
        order.setStatus(OrderStatus.WAIT_PAY);
        order.setReceiverName(cmd.receiverName());
        order.setReceiverPhone(cmd.receiverPhone());
        order.setReceiverAddress(cmd.receiverAddress());
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        Order saved = orderRepo.save(order);

        appendEvent(saved.getId(), null, OrderStatus.WAIT_PAY, "订单已创建，等待支付");
        return saved;
    }

    @Transactional
    public Order pay(long buyerId, long orderId) {
        Order order = orderRepo.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "订单不存在", HttpStatus.NOT_FOUND));
        if (order.getStatus() != OrderStatus.WAIT_PAY) {
            throw new AppException("CONFLICT", "订单状态不允许支付", HttpStatus.CONFLICT);
        }
        OrderStatus from = order.getStatus();
        order.setStatus(OrderStatus.WAIT_DELIVER);
        order.setPaidAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        Order saved = orderRepo.save(order);
        appendEvent(saved.getId(), from, OrderStatus.WAIT_DELIVER, "买家已支付");
        return saved;
    }

    /** 兼容旧 pay 方法名 */
    @Transactional
    public Order markPaid(long buyerId, long orderId) {
        return pay(buyerId, orderId);
    }

    @Transactional
    public Shipment ship(long sellerId, long orderId, ShipCommand cmd) {
        Order order = orderRepo.findByIdAndSellerId(orderId, sellerId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "订单不存在", HttpStatus.NOT_FOUND));
        if (order.getStatus() != OrderStatus.WAIT_DELIVER) {
            throw new AppException("CONFLICT", "当前状态不允许发货", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        Shipment shipment = shipmentRepo.findByOrderId(order.getId()).orElse(null);
        if (shipment == null) {
            shipment = new Shipment();
            shipment.setOrderId(order.getId());
            shipment.setCreatedAt(now);
        }
        shipment.setCarrierCode(cmd.carrierCode());
        shipment.setTrackingNo(cmd.trackingNo());
        shipment.setStatus(ShipmentStatus.CREATED);
        shipment.setUpdatedAt(now);
        Shipment savedShipment = shipmentRepo.save(shipment);

        OrderStatus from = order.getStatus();
        order.setStatus(OrderStatus.WAIT_RECEIVE);
        order.setShippedAt(now);
        order.setUpdatedAt(now);
        orderRepo.save(order);
        appendEvent(order.getId(), from, OrderStatus.WAIT_RECEIVE, "卖家已发货 (" + cmd.carrierCode() + " " + cmd.trackingNo() + ")");
        return savedShipment;
    }

    @Transactional
    public Order confirmReceived(long buyerId, long orderId) {
        Order order = orderRepo.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "订单不存在", HttpStatus.NOT_FOUND));
        if (order.getStatus() != OrderStatus.WAIT_RECEIVE) {
            throw new AppException("CONFLICT", "当前状态不允许确认收货", HttpStatus.CONFLICT);
        }
        OrderStatus from = order.getStatus();
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        Order saved = orderRepo.save(order);
        appendEvent(saved.getId(), from, OrderStatus.COMPLETED, "买家已确认收货，交易完成");
        return saved;
    }

    @Transactional
    public Order cancel(long buyerId, long orderId) {
        Order order = orderRepo.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "订单不存在", HttpStatus.NOT_FOUND));
        if (order.getStatus() != OrderStatus.WAIT_PAY && order.getStatus() != OrderStatus.WAIT_PAY) {
            throw new AppException("CONFLICT", "仅待支付状态可取消", HttpStatus.CONFLICT);
        }
        OrderStatus from = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        Order saved = orderRepo.save(order);
        appendEvent(saved.getId(), from, OrderStatus.CANCELLED, "买家取消订单");
        return saved;
    }

    @Transactional(readOnly = true)
    public OrderDetail getOrderDetail(long userId, long orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "订单不存在", HttpStatus.NOT_FOUND));
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new AppException("FORBIDDEN", "无权查看该订单", HttpStatus.FORBIDDEN);
        }
        Shipment shipment = shipmentRepo.findByOrderId(orderId).orElse(null);
        List<OrderEvent> events = orderEventRepo.findByOrderIdOrderByIdAsc(orderId);

        OrderStatus s = order.getStatus();
        boolean isBuyer = order.getBuyerId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);

        return new OrderDetail(order, shipment, events,
                isBuyer && (s == OrderStatus.WAIT_PAY || s == OrderStatus.WAIT_PAY),      // canPay
                isSeller && s == OrderStatus.WAIT_DELIVER,                                 // canShip
                isBuyer && s == OrderStatus.WAIT_RECEIVE,                                  // canConfirm
                isBuyer && (s == OrderStatus.WAIT_PAY || s == OrderStatus.WAIT_PAY),       // canCancel
                isBuyer && (s == OrderStatus.WAIT_DELIVER || s == OrderStatus.WAIT_RECEIVE
                         || s == OrderStatus.COMPLETED)                                    // canApplyAfterSale
        );
    }

    private void appendEvent(Long orderId, OrderStatus from, OrderStatus to, String note) {
        OrderEvent ev = new OrderEvent();
        ev.setOrderId(orderId);
        ev.setFromStatus(from == null ? "NONE" : from.name());
        ev.setToStatus(to.name());
        ev.setNote(note);
        ev.setCreatedAt(LocalDateTime.now());
        orderEventRepo.save(ev);
    }
}
