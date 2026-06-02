package com.secondhand.aftersale.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "after_sale_requests")
public class AfterSaleRequest {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "buyer_id")
    private Long buyerId;

    @Column(name = "seller_id")
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type")
    private AfterSaleType requestType;

    private String reason;

    @Enumerated(EnumType.STRING)
    private AfterSaleStatus status;

    @Column(name = "refund_amount_cent")
    private Integer refundAmountCent;

    @Column(name = "return_carrier_code")
    private String returnCarrierCode;

    @Column(name = "return_tracking_no")
    private String returnTrackingNo;

    @Column(name = "seller_response", columnDefinition = "TEXT")
    private String sellerResponse;

    @Column(name = "deadline_at")
    private LocalDateTime deadlineAt;

    @Column(name = "requested_at") private LocalDateTime requestedAt;
    @Column(name = "handled_at") private LocalDateTime handledAt;
    @Column(name = "created_at") private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long o) { this.orderId = o; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long id) { this.buyerId = id; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long id) { this.sellerId = id; }
    public AfterSaleType getRequestType() { return requestType; }
    public void setRequestType(AfterSaleType t) { this.requestType = t; }
    public String getReason() { return reason; }
    public void setReason(String r) { this.reason = r; }
    public AfterSaleStatus getStatus() { return status; }
    public void setStatus(AfterSaleStatus s) { this.status = s; }
    public Integer getRefundAmountCent() { return refundAmountCent; }
    public void setRefundAmountCent(Integer v) { this.refundAmountCent = v; }
    public String getReturnCarrierCode() { return returnCarrierCode; }
    public void setReturnCarrierCode(String c) { this.returnCarrierCode = c; }
    public String getReturnTrackingNo() { return returnTrackingNo; }
    public void setReturnTrackingNo(String t) { this.returnTrackingNo = t; }
    public String getSellerResponse() { return sellerResponse; }
    public void setSellerResponse(String r) { this.sellerResponse = r; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(LocalDateTime t) { this.deadlineAt = t; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime t) { this.requestedAt = t; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime t) { this.handledAt = t; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t) { this.updatedAt = t; }
}
