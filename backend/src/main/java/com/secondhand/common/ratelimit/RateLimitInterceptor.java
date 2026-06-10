package com.secondhand.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 IP + 方法签名的滑动窗口限流拦截器。
 * 每个窗口维护请求计数和时间戳，超出限制立即返回 429。
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final class Window {
        volatile long resetAt;
        volatile int count;
        Window(long resetAt) { this.resetAt = resetAt; }
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod hm)) return true;

        RateLimit annotation = hm.getMethod().getAnnotation(RateLimit.class);
        if (annotation == null) return true;

        String key = buildKey(request, hm);
        int max = annotation.maxRequests();
        int windowSec = annotation.windowSeconds();
        long now = System.currentTimeMillis();

        Window w = windows.compute(key, (k, old) -> {
            if (old == null || now > old.resetAt) {
                return new Window(now + windowSec * 1000L);
            }
            return old;
        });

        synchronized (w) {
            if (now > w.resetAt) {
                w.resetAt = now + windowSec * 1000L;
                w.count = 0;
            }
            if (w.count >= max) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                    "{\"success\":false,\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"请求过于频繁，请稍后再试\"}}");
                return false;
            }
            w.count++;
            return true;
        }
    }

    private String buildKey(HttpServletRequest request, HandlerMethod hm) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return ip + "::" + hm.getMethod().getDeclaringClass().getSimpleName()
                + "." + hm.getMethod().getName();
    }
}
