package com.secondhand.auth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 用户登录标识实体。
 * 一个用户可以有多个登录方式（手机号 + 邮箱）。
 */
@Entity
@Table(name = "user_identities",
       uniqueConstraints = @UniqueConstraint(columnNames = {"identity_type", "identifier"}))
public class UserIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属用户 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    /** 标识类型 */
    @Enumerated(EnumType.STRING)
    @Column(name = "identity_type", nullable = false, length = 16)
    private IdentityType identityType;

    /** 标识值（手机号或邮箱地址） */
    @Column(nullable = false, length = 128)
    private String identifier;

    /** 是否已验证 */
    @Column(nullable = false)
    private Boolean verified;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ---- getters & setters ----

    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public IdentityType getIdentityType() { return identityType; }
    public void setIdentityType(IdentityType identityType) { this.identityType = identityType; }

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }

    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
