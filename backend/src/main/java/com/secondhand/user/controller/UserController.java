package com.secondhand.user.controller;

import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.common.ApiResponse;
import com.secondhand.order.service.OrderService;
import com.secondhand.product.entity.Product;
import com.secondhand.product.service.ProductService;
import com.secondhand.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final ProductService productService;
    private final OrderService orderService;

    public UserController(UserService userService, ProductService productService,
                          OrderService orderService) {
        this.userService = userService;
        this.productService = productService;
        this.orderService = orderService;
    }

    /** 获取卖家公开信息 */
    @GetMapping("/{id}/public")
    public ApiResponse<UserService.PublicUserDto> publicInfo(@PathVariable("id") Long userId) {
        return ApiResponse.ok(userService.getPublicInfo(userId));
    }

    /** 卖家在售商品列表 */
    @GetMapping("/{sellerId}/products")
    public ApiResponse<Page<Product>> sellerProducts(
            @PathVariable Long sellerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.ok(productService.getSellerOnSaleProducts(sellerId, page, size));
    }

    /** 卖家已售出订单（含评分信息） */
    @GetMapping("/{sellerId}/sold")
    public ApiResponse<List<OrderService.SoldProductDto>> sellerSold(
            @PathVariable Long sellerId) {
        return ApiResponse.ok(orderService.getSellerSoldProducts(sellerId));
    }

    /** 获取个人资料 */
    @GetMapping("/profile")
    public ApiResponse<UserService.ProfileDto> profile(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(userService.getProfile(principal.userId()));
    }

    /** 更新个人资料 */
    @PutMapping("/profile")
    public ApiResponse<UserService.ProfileDto> updateProfile(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest req) {
        return ApiResponse.ok(userService.updateProfile(principal.userId(),
                req.nickname(), req.phone(), req.email()));
    }

    /** 上传头像 */
    @PutMapping("/avatar")
    public ApiResponse<String> uploadAvatar(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam("file") MultipartFile file) {
        String url = userService.uploadAvatar(principal.userId(), file);
        return ApiResponse.ok(url);
    }

    record UpdateProfileRequest(
            @Size(max = 50) String nickname,
            @Size(max = 20) String phone,
            @Size(max = 128) String email) {}
}
