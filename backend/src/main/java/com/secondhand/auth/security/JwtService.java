package com.secondhand.auth.security;

import com.secondhand.auth.dto.AuthResponse;

/**
 * JWT 令牌服务接口。
 */
public interface JwtService {

    String createAccessToken(long userId);

    String createAccessToken(long userId, String role);

    long parseUserId(String token);

    String parseRole(String token);
}
