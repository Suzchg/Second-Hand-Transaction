package com.secondhand.common.ratelimit;

import java.lang.annotation.*;

/**
 * 接口限流注解。
 * 基于 IP 的滑动窗口计数，超出限制返回 429 Too Many Requests。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 时间窗口内允许的最大请求数，默认 30 */
    int maxRequests() default 30;

    /** 时间窗口（秒），默认 60 */
    int windowSeconds() default 60;
}
