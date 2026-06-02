package com.secondhand.order.repository;

import com.secondhand.order.entity.Order;
import com.secondhand.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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
}
