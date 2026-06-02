package com.secondhand.product.controller;

import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.common.ApiResponse;
import com.secondhand.product.category.service.CategoryService;
import com.secondhand.product.entity.Product;
import com.secondhand.product.entity.ProductStatus;
import com.secondhand.product.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;

    public ProductController(ProductService productService, CategoryService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    @PostMapping
    public ApiResponse<Product> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateProductRequest req) {
        return ApiResponse.ok(productService.create(principal.userId(),
                new ProductService.CreateCommand(req.title(), req.priceCent(), req.coverImageUrl(),
                        req.description(), req.categoryId(), req.quantity())));
    }

    @PutMapping("/{id}")
    public ApiResponse<Product> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable("id") long productId,
            @Valid @RequestBody UpdateProductRequest req) {
        return ApiResponse.ok(productService.update(principal.userId(), productId,
                new ProductService.UpdateCommand(req.title(), req.priceCent(), req.coverImageUrl(),
                        req.description(), req.status(), req.categoryId())));
    }

    @GetMapping
    public ApiResponse<Page<Product>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        // 关键词搜索优先
        if (keyword != null && !keyword.isBlank()) {
            if (categoryId != null) {
                List<Long> ids = categoryService.getCategoryAndChildrenIds(categoryId);
                return ApiResponse.ok(productService.searchOnSale(page, size, keyword.trim(), ids));
            }
            return ApiResponse.ok(productService.searchOnSale(page, size, keyword.trim()));
        }
        if (categoryId != null) {
            List<Long> ids = categoryService.getCategoryAndChildrenIds(categoryId);
            return ApiResponse.ok(productService.listOnSale(page, size, ids));
        }
        return ApiResponse.ok(productService.listOnSale(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<Product> detail(@PathVariable("id") long productId) {
        return ApiResponse.ok(productService.getById(productId));
    }

    public record CreateProductRequest(
            @NotBlank @Size(max = 100) String title,
            @NotNull @Min(1) Integer priceCent,
            @Size(max = 512) String coverImageUrl,
            @NotBlank String description,
            Long categoryId,
            @Min(1) Integer quantity) {}

    public record UpdateProductRequest(
            @Size(max = 100) String title,
            @Min(1) Integer priceCent,
            @Size(max = 512) String coverImageUrl,
            String description,
            ProductStatus status,
            Long categoryId) {}
}
