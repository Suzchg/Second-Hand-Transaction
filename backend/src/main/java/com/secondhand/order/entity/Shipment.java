package com.secondhand.order.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "shipments")
public class Shipment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "carrier_code")
    private String carrierCode;

    @Column(name = "tracking_no")
    private String trackingNo;

    @Enumerated(EnumType.STRING)
    private ShipmentStatus status;

    @Column(name = "created_at") private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long o) { this.orderId = o; }
    public String getCarrierCode() { return carrierCode; }
    public void setCarrierCode(String c) { this.carrierCode = c; }
    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String t) { this.trackingNo = t; }
    public ShipmentStatus getStatus() { return status; }
    public void setStatus(ShipmentStatus s) { this.status = s; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t) { this.updatedAt = t; }
}
