package com.secondhand.admin.service;

import com.secondhand.admin.OnlineUserTracker;
import com.secondhand.order.entity.OrderStatus;
import com.secondhand.order.repository.OrderRepository;
import com.secondhand.auth.repository.UserRepository;
import com.secondhand.product.repository.ProductRepository;
import com.secondhand.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserRepository userRepo;
    private final ProductRepository productRepo;
    private final OrderRepository orderRepo;
    private final OnlineUserTracker onlineUserTracker;

    public AdminService(UserRepository userRepo, ProductRepository productRepo,
                        OrderRepository orderRepo, OnlineUserTracker onlineUserTracker) {
        this.userRepo = userRepo;
        this.productRepo = productRepo;
        this.orderRepo = orderRepo;
        this.onlineUserTracker = onlineUserTracker;
    }

    // ---- Dashboard ----

    public DashboardDto getDashboard() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        long totalUsers = userRepo.count();
        long activeUsers = onlineUserTracker.countActive(10);
        long totalProducts = productRepo.count();
        long onSaleProducts = productRepo.countByStatus(ProductStatus.ON_SALE);
        long totalOrders = orderRepo.count();
        long todayNewUsers = userRepo.countByCreatedAtAfter(todayStart);
        long todayNewOrders = orderRepo.countByCreatedAtAfter(todayStart);

        // 订单状态分布
        List<StatusCount> statusDist = Arrays.stream(OrderStatus.values())
                .map(s -> new StatusCount(s.name(), orderRepo.countByStatus(s)))
                .filter(sc -> sc.count > 0)
                .collect(Collectors.toList());

        return new DashboardDto(totalUsers, activeUsers, totalProducts, onSaleProducts,
                totalOrders, todayNewUsers, todayNewOrders, statusDist);
    }

    public record DashboardDto(long totalUsers, long activeUsers, long totalProducts,
                                long onSaleProducts, long totalOrders, long todayNewUsers,
                                long todayNewOrders, List<StatusCount> orderStatusDistribution) {}

    public record StatusCount(String status, long count) {}
}
