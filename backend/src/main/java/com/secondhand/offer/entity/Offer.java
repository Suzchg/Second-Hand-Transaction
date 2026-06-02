package com.secondhand.offer.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "offers")
public class Offer {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "offered_price_cent", nullable = false)
    private Integer offeredPriceCent;

    @Column(length = 500)
    private String message;

    /** 报价被接受后创建的订单 ID */
    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OfferStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long v) { this.productId = v; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long v) { this.buyerId = v; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long v) { this.sellerId = v; }
    public Integer getOfferedPriceCent() { return offeredPriceCent; }
    public void setOfferedPriceCent(Integer v) { this.offeredPriceCent = v; }
    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long v) { this.orderId = v; }
    public OfferStatus getStatus() { return status; }
    public void setStatus(OfferStatus v) { this.status = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
