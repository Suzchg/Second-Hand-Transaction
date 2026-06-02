package com.secondhand.admin.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT Token 黑名单（内存实现）。
 *
 * 管理员踢人时将被踢用户的 userId 加入黑名单，
 * JwtAuthFilter 在验证 token 后检查此黑名单。
 *
 * key = userId, value = 黑名单过期时间戳
 */
@Component
public class TokenBlacklist {

    private final Map<Long, Long> blacklist = new ConcurrentHashMap<>();

    /** 将用户加入黑名单 */
    public void add(Long userId) {
        // 黑名单有效期 = JWT 过期时间（2小时 = 7200000ms）
        blacklist.put(userId, System.currentTimeMillis() + 7200000);
    }

    /** 检查用户是否在黑名单中（自动清理过期条目） */
    public boolean isBlacklisted(Long userId) {
        Long expireAt = blacklist.get(userId);
        if (expireAt == null) return false;
        if (System.currentTimeMillis() > expireAt) {
            blacklist.remove(userId);
            return false;
        }
        return true;
    }

    /** 移除黑名单（如有需要恢复的场景） */
    public void remove(Long userId) {
        blacklist.remove(userId);
    }

    /** 清理所有过期条目 */
    public void cleanup() {
        long now = System.currentTimeMillis();
        blacklist.entrySet().removeIf(e -> e.getValue() < now);
    }
}
