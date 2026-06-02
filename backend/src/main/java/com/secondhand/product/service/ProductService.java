package com.secondhand.product.service;

import com.secondhand.common.AppException;
import com.secondhand.product.entity.Product;
import com.secondhand.product.entity.ProductCondition;
import com.secondhand.product.entity.ProductStatus;
import com.secondhand.product.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepo;

    public ProductService(ProductRepository productRepo) {
        this.productRepo = productRepo;
    }

    public record CreateCommand(String title, Integer priceCent, String coverImageUrl,
                                 String description, Long categoryId, Integer quantity,
                                 ProductCondition condition) {}
    public record UpdateCommand(String title, Integer priceCent, String coverImageUrl,
                                 String description, ProductStatus status, Long categoryId,
                                 ProductCondition condition) {}

    @Transactional
    public Product create(long sellerId, CreateCommand cmd) {
        LocalDateTime now = LocalDateTime.now();
        Product p = new Product();
        p.setSellerId(sellerId);
        p.setTitle(cmd.title());
        p.setPriceCent(cmd.priceCent());
        p.setCoverImageUrl(cmd.coverImageUrl());
        p.setDescription(cmd.description());
        p.setCategoryId(cmd.categoryId());
        p.setQuantity(cmd.quantity() != null && cmd.quantity() > 0 ? cmd.quantity() : 1);
        p.setCondition(cmd.condition());
        p.setStatus(ProductStatus.ON_SALE);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return productRepo.save(p);
    }

    @Transactional
    public Product update(long sellerId, long productId, UpdateCommand cmd) {
        Product p = productRepo.findById(productId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "商品不存在", HttpStatus.NOT_FOUND));
        if (!p.getSellerId().equals(sellerId)) {
            throw new AppException("FORBIDDEN", "无权编辑该商品", HttpStatus.FORBIDDEN);
        }
        if (cmd.title() != null) p.setTitle(cmd.title());
        if (cmd.priceCent() != null) p.setPriceCent(cmd.priceCent());
        if (cmd.coverImageUrl() != null) p.setCoverImageUrl(cmd.coverImageUrl());
        if (cmd.description() != null) p.setDescription(cmd.description());
        if (cmd.status() != null) {
            // 只有库存 > 0 才能上架
            if (cmd.status() == ProductStatus.ON_SALE && (p.getQuantity() == null || p.getQuantity() <= 0)) {
                throw new AppException("CONFLICT", "商品已售罄，无法上架", HttpStatus.CONFLICT);
            }
            p.setStatus(cmd.status());
        }
        if (cmd.categoryId() != null) p.setCategoryId(cmd.categoryId());
        if (cmd.condition() != null) p.setCondition(cmd.condition());
        p.setUpdatedAt(LocalDateTime.now());
        return productRepo.save(p);
    }

    @Transactional(readOnly = true)
    public Product getById(long id) {
        return productRepo.findById(id)
                .orElseThrow(() -> new AppException("NOT_FOUND", "商品不存在", HttpStatus.NOT_FOUND));
    }

    /** 列表（无分类筛选） */
    @Transactional(readOnly = true)
    public Page<Product> listOnSale(int page, int size) {
        return productRepo.findByStatusAndQuantityGreaterThan(ProductStatus.ON_SALE, 0, PageRequest.of(page, Math.min(size, 50)));
    }

    /** 列表（按分类筛选，含子分类） */
    @Transactional(readOnly = true)
    public Page<Product> listOnSale(int page, int size, List<Long> categoryIds) {
        return productRepo.findByStatusAndCategoryIdIn(
                ProductStatus.ON_SALE, categoryIds, PageRequest.of(page, Math.min(size, 50)));
    }

    /** 关键词搜索（在售） */
    @Transactional(readOnly = true)
    public Page<Product> searchOnSale(int page, int size, String keyword) {
        return productRepo.findByStatusAndQuantityAndTitleContaining(
                ProductStatus.ON_SALE, keyword.trim(), PageRequest.of(page, Math.min(size, 50)));
    }

    /** 关键词 + 分类搜索（在售） */
    @Transactional(readOnly = true)
    public Page<Product> searchOnSale(int page, int size, String keyword, List<Long> categoryIds) {
        return productRepo.findByStatusAndCategoryIdInAndTitleContaining(
                ProductStatus.ON_SALE, categoryIds, keyword.trim(), PageRequest.of(page, Math.min(size, 50)));
    }

    /** 卖家自己的商品列表 */
    @Transactional(readOnly = true)
    public Page<Product> getMyProducts(long sellerId, int page, int size) {
        return productRepo.findBySellerIdOrderByCreatedAtDesc(
                sellerId, PageRequest.of(page, Math.min(size, 50)));
    }

    /** 某卖家在售商品（公开查看） */
    @Transactional(readOnly = true)
    public Page<Product> getSellerOnSaleProducts(long sellerId, int page, int size) {
        return productRepo.findByStatusAndQuantityGreaterThanAndSellerId(
                ProductStatus.ON_SALE, sellerId, PageRequest.of(page, Math.min(size, 50)));
    }
}
