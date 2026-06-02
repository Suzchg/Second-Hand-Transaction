package com.secondhand.favorite.controller;

import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.common.ApiResponse;
import com.secondhand.favorite.entity.Favorite;
import com.secondhand.favorite.service.FavoriteService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /** 收藏商品 */
    @PostMapping("/api/products/{productId}/favorite")
    public ApiResponse<Void> add(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long productId) {
        favoriteService.addFavorite(principal.userId(), productId);
        return ApiResponse.ok();
    }

    /** 取消收藏 */
    @DeleteMapping("/api/products/{productId}/favorite")
    public ApiResponse<Void> remove(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long productId) {
        favoriteService.removeFavorite(principal.userId(), productId);
        return ApiResponse.ok();
    }

    /** 查询收藏状态 */
    @GetMapping("/api/products/{productId}/favorite/status")
    public ApiResponse<Boolean> status(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long productId) {
        return ApiResponse.ok(favoriteService.isFavorited(principal.userId(), productId));
    }

    /** 我的收藏列表 */
    @GetMapping("/api/users/favorites")
    public ApiResponse<Page<Favorite>> myFavorites(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ApiResponse.ok(favoriteService.getMyFavorites(principal.userId(), page, size));
    }
}
