package com.secondhand.product.image.controller;

import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.common.ApiResponse;
import com.secondhand.product.entity.Product;
import com.secondhand.product.image.StorageService;
import com.secondhand.product.image.entity.ProductImage;
import com.secondhand.product.image.repository.ProductImageRepository;
import com.secondhand.product.repository.ProductRepository;
import com.secondhand.product.service.ProductService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/products/{productId}/images")
public class ProductImageController {

    private final ProductImageRepository imageRepo;
    private final ProductService productService;
    private final ProductRepository productRepo;
    private final StorageService storageService;

    public ProductImageController(ProductImageRepository imageRepo,
                                  ProductService productService,
                                  ProductRepository productRepo,
                                  StorageService storageService) {
        this.imageRepo = imageRepo;
        this.productService = productService;
        this.productRepo = productRepo;
        this.storageService = storageService;
    }

    /** 获取商品的所有图片 */
    @GetMapping
    public ApiResponse<List<ProductImage>> list(@PathVariable Long productId) {
        return ApiResponse.ok(imageRepo.findByProductIdOrderBySortOrderAsc(productId));
    }

    /** 上传图片（最多 9 张） */
    @PostMapping
    public ApiResponse<ProductImage> upload(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long productId,
            @RequestParam("file") MultipartFile file) {
        Product product = productService.getById(productId);
        if (!product.getSellerId().equals(principal.userId())) {
            return ApiResponse.fail("FORBIDDEN", "无权操作该商品");
        }

        int count = imageRepo.countByProductId(productId);
        if (count >= 9) {
            return ApiResponse.fail("LIMIT", "最多上传 9 张图片");
        }

        StorageService.StoredFile stored = storageService.store(file,
                "products/" + productId);

        ProductImage img = new ProductImage();
        img.setProductId(productId);
        img.setUrl(stored.storedPath());
        img.setThumbnailUrl(stored.thumbnailPath());
        img.setSortOrder(count);
        img.setCreatedAt(LocalDateTime.now());

        // 如果是第一张图片，自动设为商品封面（需要显式 save）
        if (count == 0) {
            product.setCoverImageUrl(stored.thumbnailPath());
            product.setUpdatedAt(LocalDateTime.now());
            productRepo.save(product);
        }

        return ApiResponse.ok(imageRepo.save(img));
    }

    /** 删除图片 */
    @DeleteMapping("/{imageId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        Product product = productService.getById(productId);
        if (!product.getSellerId().equals(principal.userId())) {
            return ApiResponse.fail("FORBIDDEN", "无权操作该商品");
        }

        ProductImage img = imageRepo.findById(imageId)
                .orElseThrow(() -> new RuntimeException("图片不存在"));
        storageService.delete(img.getUrl());
        imageRepo.delete(img);

        // 更新封面
        List<ProductImage> remaining = imageRepo.findByProductIdOrderBySortOrderAsc(productId);
        String oldCover = product.getCoverImageUrl();
        if (!remaining.isEmpty() && img.getThumbnailUrl().equals(oldCover)) {
            product.setCoverImageUrl(remaining.get(0).getThumbnailUrl());
        } else if (remaining.isEmpty()) {
            product.setCoverImageUrl(null);
        }
        product.setUpdatedAt(LocalDateTime.now());
        productRepo.save(product);

        return ApiResponse.ok();
    }

    /** 设为封面 */
    @PutMapping("/{imageId}/cover")
    public ApiResponse<Void> setCover(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        Product product = productService.getById(productId);
        if (!product.getSellerId().equals(principal.userId())) {
            return ApiResponse.fail("FORBIDDEN", "无权操作该商品");
        }

        ProductImage img = imageRepo.findById(imageId)
                .orElseThrow(() -> new RuntimeException("图片不存在"));
        product.setCoverImageUrl(img.getThumbnailUrl());
        product.setUpdatedAt(LocalDateTime.now());
        productRepo.save(product);
        return ApiResponse.ok();
    }
}
