package com.secondhand.product.controller;

import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.common.ApiResponse;
import com.secondhand.product.entity.Product;
import com.secondhand.product.service.ProductService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class MyProductController {

    private final ProductService productService;

    public MyProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 卖家自己的商品列表 */
    @GetMapping("/api/my-products")
    public ApiResponse<Page<Product>> myProducts(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ApiResponse.ok(productService.getMyProducts(principal.userId(), page, size));
    }
}
