package com.secondhand.auth.dto;

/**
 * 认证成功响应 DTO。
 */
public record AuthResponse(
        String accessToken,
        long userId,
        String nickname,
        String role,
        String avatarUrl
) {}
