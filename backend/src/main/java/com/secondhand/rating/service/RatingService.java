package com.secondhand.rating.service;

import com.secondhand.common.AppException;
import com.secondhand.order.entity.Order;
import com.secondhand.order.entity.OrderStatus;
import com.secondhand.order.repository.OrderRepository;
import com.secondhand.rating.entity.Rating;
import com.secondhand.rating.repository.RatingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class RatingService {

    private final RatingRepository ratingRepo;
    private final OrderRepository orderRepo;

    public RatingService(RatingRepository ratingRepo, OrderRepository orderRepo) {
        this.ratingRepo = ratingRepo;
        this.orderRepo = orderRepo;
    }

    public record SellerRating(double averageScore, long totalCount) {}

    @Transactional
    public Rating rate(Long orderId, Long reviewerId, Integer score, String comment) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "订单不存在", HttpStatus.NOT_FOUND));
        if (!order.getBuyerId().equals(reviewerId)) {
            throw new AppException("FORBIDDEN", "只有买家可以评分", HttpStatus.FORBIDDEN);
        }
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new AppException("CONFLICT", "订单完成后方可评分", HttpStatus.CONFLICT);
        }
        if (ratingRepo.findByOrderId(orderId).isPresent()) {
            throw new AppException("CONFLICT", "该订单已评分", HttpStatus.CONFLICT);
        }
        if (score < 1 || score > 5) {
            throw new AppException("VALIDATION_ERROR", "评分须为 1-5 分", HttpStatus.BAD_REQUEST);
        }

        Rating r = new Rating();
        r.setOrderId(orderId);
        r.setProductId(order.getProductId());
        r.setSellerId(order.getSellerId());
        r.setReviewerId(reviewerId);
        r.setScore(score);
        r.setComment(comment);
        r.setCreatedAt(LocalDateTime.now());
        return ratingRepo.save(r);
    }

    @Transactional(readOnly = true)
    public Rating getOrderRating(Long orderId) {
        return ratingRepo.findByOrderId(orderId).orElse(null);
    }

    @Transactional(readOnly = true)
    public SellerRating getSellerRating(Long sellerId) {
        double avg = ratingRepo.getAverageScoreBySellerId(sellerId);
        long count = ratingRepo.getCountBySellerId(sellerId);
        return new SellerRating(Math.round(avg * 10.0) / 10.0, count);
    }
}
