package com.secondhand.order.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "amount_cent", nullable = false)
    private Integer amountCent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Column(name = "receiver_name") private String receiverName;
    @Column(name = "receiver_phone") private String receiverPhone;
    @Column(name = "receiver_address") private String receiverAddress;

    @Column(name = "address_id") private Long addressId;
    @Column(name = "paid_at") private LocalDateTime paidAt;
    @Column(name = "shipped_at") private LocalDateTime shippedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "cancelled_at") private LocalDateTime cancelledAt;
    @Column(name = "settled_at") private LocalDateTime settledAt;

    @Column(name = "created_at") private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long b) { this.buyerId = b; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long s) { this.sellerId = s; }
    public Long getProductId() { return productId; }
    public void setProductId(Long p) { this.productId = p; }
    public Integer getAmountCent() { return amountCent; }
    public void setAmountCent(Integer a) { this.amountCent = a; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus s) { this.status = s; }
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String n) { this.receiverName = n; }
    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String p) { this.receiverPhone = p; }
    public String getReceiverAddress() { return receiverAddress; }
    public void setReceiverAddress(String a) { this.receiverAddress = a; }
    public Long getAddressId() { return addressId; }
    public void setAddressId(Long id) { this.addressId = id; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime t) { this.paidAt = t; }
    public LocalDateTime getShippedAt() { return shippedAt; }
    public void setShippedAt(LocalDateTime t) { this.shippedAt = t; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime t) { this.completedAt = t; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime t) { this.cancelledAt = t; }
    public LocalDateTime getSettledAt() { return settledAt; }
    public void setSettledAt(LocalDateTime t) { this.settledAt = t; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t) { this.updatedAt = t; }
}
