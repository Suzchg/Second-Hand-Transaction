package com.secondhand.admin;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 在线用户追踪（内存实现）。
 * 记录每个用户的最后活跃时间。
 */
@Component
public class OnlineUserTracker {

    private final Map<Long, Instant> activeUsers = new ConcurrentHashMap<>();

    /** 记录心跳 */
    public void heartbeat(Long userId) {
        activeUsers.put(userId, Instant.now());
    }

    /** 用户登出时移除 */
    public void remove(Long userId) {
        activeUsers.remove(userId);
    }

    /** 获取最近 N 分钟内有活动的用户 ID */
    public Set<Long> getActiveUserIds(long minutes) {
        Instant threshold = Instant.now().minusSeconds(minutes * 60);
        return activeUsers.entrySet().stream()
                .filter(e -> e.getValue().isAfter(threshold))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /** 获取在线用户数 */
    public int countActive(long minutes) {
        return getActiveUserIds(minutes).size();
    }
}
