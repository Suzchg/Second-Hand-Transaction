package com.secondhand.report.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
public class Report {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false, length = 32)
    private ReportReason reasonType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReportStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handle_note", columnDefinition = "TEXT")
    private String handleNote;

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long v) { this.productId = v; }
    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long v) { this.reporterId = v; }
    public ReportReason getReasonType() { return reasonType; }
    public void setReasonType(ReportReason v) { this.reasonType = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus v) { this.status = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime v) { this.handledAt = v; }
    public Long getHandledBy() { return handledBy; }
    public void setHandledBy(Long v) { this.handledBy = v; }
    public String getHandleNote() { return handleNote; }
    public void setHandleNote(String v) { this.handleNote = v; }
}
