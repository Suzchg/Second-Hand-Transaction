package com.secondhand.product.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
public class Product {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "price_cent", nullable = false)
    private Integer priceCent;

    @Column(name = "cover_image_url", length = 512)
    private String coverImageUrl;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 库存数量，默认 1（二手商品通常只有一件） */
    @Column(nullable = false)
    private Integer quantity = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProductStatus status;

    /** 商品成色 */
    @Enumerated(EnumType.STRING)
    @Column(name = "product_condition", length = 32)
    private ProductCondition condition;

    @Column(name = "created_at") private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long s) { this.sellerId = s; }
    public String getTitle() { return title; }
    public void setTitle(String t) { this.title = t; }
    public Integer getPriceCent() { return priceCent; }
    public void setPriceCent(Integer p) { this.priceCent = p; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String u) { this.coverImageUrl = u; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer q) { this.quantity = q; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public ProductStatus getStatus() { return status; }
    public void setStatus(ProductStatus s) { this.status = s; }
    public ProductCondition getCondition() { return condition; }
    public void setCondition(ProductCondition c) { this.condition = c; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t) { this.updatedAt = t; }
}
