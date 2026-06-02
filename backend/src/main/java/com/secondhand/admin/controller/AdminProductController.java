package com.secondhand.admin.controller;

import com.secondhand.common.ApiResponse;
import com.secondhand.common.AppException;
import com.secondhand.product.entity.Product;
import com.secondhand.product.entity.ProductStatus;
import com.secondhand.product.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductRepository productRepo;

    public AdminProductController(ProductRepository productRepo) {
        this.productRepo = productRepo;
    }

    /** 分页查询所有商品 */
    @GetMapping
    public ApiResponse<Page<Product>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductStatus status) {
        Page<Product> result;
        if (keyword != null && !keyword.isBlank()) {
            result = productRepo.findByTitleContainingIgnoreCase(keyword.trim(), PageRequest.of(page, size));
        } else if (status != null) {
            result = productRepo.findByStatus(status, PageRequest.of(page, size));
        } else {
            result = productRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        }
        return ApiResponse.ok(result);
    }

    /** 强制下架 */
    @PutMapping("/{id}/off-shelf")
    public ApiResponse<Product> offShelf(@PathVariable Long id) {
        Product p = productRepo.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "商品不存在", HttpStatus.NOT_FOUND));
        p.setStatus(ProductStatus.OFF_SALE);
        p.setUpdatedAt(LocalDateTime.now());
        return ApiResponse.ok(productRepo.save(p));
    }

    /** 重新上架 */
    @PutMapping("/{id}/on-shelf")
    public ApiResponse<Product> onShelf(@PathVariable Long id) {
        Product p = productRepo.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "商品不存在", HttpStatus.NOT_FOUND));
        p.setStatus(ProductStatus.ON_SALE);
        p.setUpdatedAt(LocalDateTime.now());
        return ApiResponse.ok(productRepo.save(p));
    }

    /** 删除违规商品 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productRepo.deleteById(id);
        return ApiResponse.ok();
    }
}
