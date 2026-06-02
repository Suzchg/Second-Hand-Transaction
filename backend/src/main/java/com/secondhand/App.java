package com.secondhand;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SecondHand — 二手交易系统启动入口。
 *
 * <p>架构概览（按功能分包的分层架构）：
 * <pre>
 * com.secondhand
 * ├── common/         公共基础设施（ApiResponse, AppException, GlobalExceptionHandler）
 * ├── config/         全局配置（SecurityConfig, SpaController）
 * ├── auth/           认证模块（controller / service / dto / entity / repository / security）
 * ├── product/        商品模块
 * ├── order/          订单模块（含 OrderEvent, Shipment）
 * ├── aftersale/      售后模块
 * └── logistics/      物流模块（策略模式：LogisticsProvider 接口 + Mock 实现）
 * </pre>
 *
 * <p>技术栈：Java 17+ / Spring Boot 3 / Spring Security / JPA / H2 / JWT (jjwt)
 */
@SpringBootApplication
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
