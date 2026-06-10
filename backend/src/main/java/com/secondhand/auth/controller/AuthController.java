package com.secondhand.auth.controller;

import com.secondhand.admin.OnlineUserTracker;
import com.secondhand.auth.dto.AuthResponse;
import com.secondhand.auth.dto.ChangePasswordRequest;
import com.secondhand.auth.dto.LoginRequest;
import com.secondhand.auth.dto.RegisterRequest;
import com.secondhand.auth.security.AuthPrincipal;
import com.secondhand.auth.service.AuthService;
import com.secondhand.common.ApiResponse;
import com.secondhand.common.ratelimit.RateLimit;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 认证 REST 控制器。
 *
 * 端点概览：
 * - POST /api/auth/register       注册
 * - POST /api/auth/login          登录
 * - GET  /api/auth/me             获取当前用户信息（需认证）
 * - POST /api/auth/password/change 修改密码（需认证）
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final OnlineUserTracker onlineUserTracker;

    public AuthController(AuthService authService, OnlineUserTracker onlineUserTracker) {
        this.authService = authService;
        this.onlineUserTracker = onlineUserTracker;
    }

    /** 用户注册 */
    @RateLimit(maxRequests = 5, windowSeconds = 60)
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest req) {
        AuthResponse result = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(result));
    }

    /** 用户登录 */
    @RateLimit(maxRequests = 10, windowSeconds = 60)
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest req) {
        AuthResponse result = authService.login(req);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 获取当前登录用户信息 */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse>> me(
            @AuthenticationPrincipal AuthPrincipal principal) {
        AuthResponse result = authService.getUserInfo(principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /** 心跳（维持在线状态） */
    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<Void>> heartbeat(
            @AuthenticationPrincipal AuthPrincipal principal) {
        onlineUserTracker.heartbeat(principal.userId());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** 修改密码 */
    @PostMapping("/password/change")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(principal.userId(), req);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
