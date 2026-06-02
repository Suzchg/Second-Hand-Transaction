package com.secondhand.order.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_events")
public class OrderEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status")
    private String toStatus;

    private String note;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long o) { this.orderId = o; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String s) { this.fromStatus = s; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String s) { this.toStatus = s; }
    public String getNote() { return note; }
    public void setNote(String n) { this.note = n; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime t) { this.createdAt = t; }
}
