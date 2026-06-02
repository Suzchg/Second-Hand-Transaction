package com.secondhand.rating.controller;

import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.common.ApiResponse;
import com.secondhand.rating.entity.Rating;
import com.secondhand.rating.service.RatingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    /** 提交评分 */
    @PostMapping("/api/orders/{orderId}/rate")
    public ApiResponse<Rating> rate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long orderId,
            @Valid @RequestBody RateRequest req) {
        return ApiResponse.ok(ratingService.rate(orderId, principal.userId(),
                req.score(), req.comment()));
    }

    /** 获取订单的评分（用于展示已评分状态） */
    @GetMapping("/api/orders/{orderId}/rating")
    public ApiResponse<Rating> getOrderRating(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long orderId) {
        return ApiResponse.ok(ratingService.getOrderRating(orderId));
    }

    /** 卖家评分统计（公开） */
    @GetMapping("/api/users/{userId}/rating")
    public ApiResponse<RatingService.SellerRating> sellerRating(
            @PathVariable Long userId) {
        return ApiResponse.ok(ratingService.getSellerRating(userId));
    }

    record RateRequest(
            @NotNull @Min(1) @Max(5) Integer score,
            String comment) {}
}
