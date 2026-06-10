package com.secondhand.order.repository;

import com.secondhand.order.entity.Order;
import com.secondhand.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdAndBuyerId(Long id, Long buyerId);
    Optional<Order> findByIdAndSellerId(Long id, Long sellerId);

    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(OrderStatus status);
    long countByCreatedAtAfter(LocalDateTime after);

    // 用户相关
    Page<Order> findByBuyerIdOrderByCreatedAtDesc(Long buyerId, Pageable pageable);
    Page<Order> findBySellerIdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);
    Page<Order> findByBuyerIdAndStatusOrderByCreatedAtDesc(Long buyerId, OrderStatus status, Pageable pageable);
    Page<Order> findBySellerIdAndStatusOrderByCreatedAtDesc(Long sellerId, OrderStatus status, Pageable pageable);

    /** 统计买家指定状态订单数 */
    long countByBuyerIdAndStatusIn(Long buyerId, List<OrderStatus> statuses);
    /** 统计卖家指定状态订单数 */
    long countBySellerIdAndStatusIn(Long sellerId, List<OrderStatus> statuses);

    /** 用户作为买家或卖家的所有订单 ID */
    List<Order> findByBuyerIdOrSellerIdOrderByCreatedAtDesc(Long buyerId, Long sellerId);

    /** 结算：已完成且超过7天售后期的订单 */
    List<Order> findByStatusAndCompletedAtBefore(OrderStatus status, LocalDateTime before);

    /** 收货超时：待收货且发货超过指定天数的订单 */
    List<Order> findByStatusAndShippedAtBefore(OrderStatus status, LocalDateTime before);
}
