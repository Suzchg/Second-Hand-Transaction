package com.secondhand.auth.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtServiceImpl 单元测试。
 * 直接构造 JwtServiceImpl（不依赖 Spring 上下文），验证 JWT 签发、解析、
 * 过期拒绝、篡改/错误密钥拒绝等核心规则。
 */
@DisplayName("JwtServiceImpl 单元测试")
class JwtServiceImplTest {

    /** HMAC-SHA256 要求密钥 >= 32 字节，这里用 64 字节 */
    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("签发的 token 可解析出 userId")
    void shouldRoundTripUserId() {
        JwtServiceImpl service = new JwtServiceImpl(SECRET, 30);
        String token = service.createAccessToken(42L, "USER");

        assertEquals(42L, service.parseUserId(token));
    }

    @Test
    @DisplayName("签发的 token 可解析出 role")
    void shouldRoundTripRole() {
        JwtServiceImpl service = new JwtServiceImpl(SECRET, 30);
        String token = service.createAccessToken(42L, "ADMIN");

        assertEquals("ADMIN", service.parseRole(token));
    }

    @Test
    @DisplayName("过期 token 解析应抛出 JwtException")
    void shouldRejectExpiredToken() {
        // 负的过期分钟数 => token 一经签发即为过期
        JwtServiceImpl service = new JwtServiceImpl(SECRET, -1);
        String token = service.createAccessToken(1L, "USER");

        assertThrows(JwtException.class, () -> service.parseUserId(token));
    }

    @Test
    @DisplayName("被篡改（错误密钥）的 token 解析应抛出 JwtException")
    void shouldRejectTamperedToken() {
        JwtServiceImpl signer = new JwtServiceImpl(SECRET, 30);
        JwtServiceImpl attacker = new JwtServiceImpl(
                "another-secret-key-0123456789abcdef0123456789abcdef", 30);

        String token = signer.createAccessToken(1L, "USER");

        // 用不同密钥解析 => 签名校验失败
        assertThrows(JwtException.class, () -> attacker.parseUserId(token));
    }
}
