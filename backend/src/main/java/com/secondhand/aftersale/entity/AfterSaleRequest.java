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
    @Column(name = "request_type", nullable = false, length = 32)
    private AfterSaleType requestType;

    /** 售后原因（买家填写） */
    @Column(columnDefinition = "TEXT")
    private String reason;

    /** 责任归属：BUYER / SELLER / LOGISTICS */
    @Column(length = 16)
    private String responsibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AfterSaleStatus status;

    /** 退款金额（分），仅退款/部分退款时使用 */
    @Column(name = "refund_amount_cent")
    private Integer refundAmountCent;

    /** 退货快递公司 */
    @Column(name = "return_carrier_code", length = 32)
    private String returnCarrierCode;

    /** 退货快递单号 */
    @Column(name = "return_tracking_no", length = 64)
    private String returnTrackingNo;

    /** 卖家回复/拒绝原因 */
    @Column(name = "seller_response", columnDefinition = "TEXT")
    private String sellerResponse;

    /** 买家举证材料（JSON 数组，含图片URL、视频URL等） */
    @Column(name = "buyer_evidence", columnDefinition = "TEXT")
    private String buyerEvidence;

    /** 卖家举证材料 */
    @Column(name = "seller_evidence", columnDefinition = "TEXT")
    private String sellerEvidence;

    /** 平台仲裁结果 */
    @Column(name = "arbitration_result", columnDefinition = "TEXT")
    private String arbitrationResult;

    /** 运费承担方：BUYER / SELLER / PLATFORM */
    @Column(name = "shipping_paid_by", length = 16)
    private String shippingPaidBy;

    /** 运费金额（分），平台裁定的运费 */
    @Column(name = "shipping_cost_cent")
    private Integer shippingCostCent;

    /** 当前阶段截止时间（卖家审核/买家寄件/卖家确认收货） */
    @Column(name = "deadline_at")
    private LocalDateTime deadlineAt;

    /** 确认收货时间（用于计算售后窗口期） */
    @Column(name = "order_completed_at")
    private LocalDateTime orderCompletedAt;

    @Column(name = "requested_at") private LocalDateTime requestedAt;
    @Column(name = "handled_at") private LocalDateTime handledAt;
    @Column(name = "returned_at") private LocalDateTime returnedAt;
    @Column(name = "refunded_at") private LocalDateTime refundedAt;
    @Column(name = "closed_at") private LocalDateTime closedAt;
    @Column(name = "created_at") private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;

    // ---- getters / setters ----

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long v) { this.orderId = v; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long v) { this.buyerId = v; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long v) { this.sellerId = v; }
    public AfterSaleType getRequestType() { return requestType; }
    public void setRequestType(AfterSaleType v) { this.requestType = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public String getResponsibility() { return responsibility; }
    public void setResponsibility(String v) { this.responsibility = v; }
    public AfterSaleStatus getStatus() { return status; }
    public void setStatus(AfterSaleStatus v) { this.status = v; }
    public Integer getRefundAmountCent() { return refundAmountCent; }
    public void setRefundAmountCent(Integer v) { this.refundAmountCent = v; }
    public String getReturnCarrierCode() { return returnCarrierCode; }
    public void setReturnCarrierCode(String v) { this.returnCarrierCode = v; }
    public String getReturnTrackingNo() { return returnTrackingNo; }
    public void setReturnTrackingNo(String v) { this.returnTrackingNo = v; }
    public String getSellerResponse() { return sellerResponse; }
    public void setSellerResponse(String v) { this.sellerResponse = v; }
    public String getBuyerEvidence() { return buyerEvidence; }
    public void setBuyerEvidence(String v) { this.buyerEvidence = v; }
    public String getSellerEvidence() { return sellerEvidence; }
    public void setSellerEvidence(String v) { this.sellerEvidence = v; }
    public String getArbitrationResult() { return arbitrationResult; }
    public void setArbitrationResult(String v) { this.arbitrationResult = v; }
    public String getShippingPaidBy() { return shippingPaidBy; }
    public void setShippingPaidBy(String v) { this.shippingPaidBy = v; }
    public Integer getShippingCostCent() { return shippingCostCent; }
    public void setShippingCostCent(Integer v) { this.shippingCostCent = v; }
    public LocalDateTime getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(LocalDateTime v) { this.deadlineAt = v; }
    public LocalDateTime getOrderCompletedAt() { return orderCompletedAt; }
    public void setOrderCompletedAt(LocalDateTime v) { this.orderCompletedAt = v; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime v) { this.requestedAt = v; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime v) { this.handledAt = v; }
    public LocalDateTime getReturnedAt() { return returnedAt; }
    public void setReturnedAt(LocalDateTime v) { this.returnedAt = v; }
    public LocalDateTime getRefundedAt() { return refundedAt; }
    public void setRefundedAt(LocalDateTime v) { this.refundedAt = v; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime v) { this.closedAt = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
