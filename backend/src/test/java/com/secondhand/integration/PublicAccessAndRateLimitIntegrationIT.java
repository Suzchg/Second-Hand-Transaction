package com.secondhand.integration;

import com.secondhand.auth.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 集成测试 · 补充用例：公开端点访问 + 限流 + 全局异常 + 管理员权限边界
 *
 * 覆盖范围：
 * - 模块间调用：RateLimitInterceptor → AuthController（@RateLimit 注解）、SecurityConfig（权限路由）
 *              GlobalExceptionHandler（统一异常转换）、AdminUserController（管理员操作）
 * - 数据库访问：users 表（管理员禁用/启用用户、强制下线）
 * - 对外接口：
 *   - 公开 GET 端点：/api/regions、/api/categories、/api/products、/api/users/{id}/public 等
 *   - 健康检查：/actuator/health
 *   - API 文档：/v3/api-docs
 *   - 管理员端点：/api/admin/users（列表/禁用/启用/强制下线/在线列表）
 *
 * 用例流程覆盖：
 * - 主成功流程：公开端点访问、健康检查、API 文档、管理员列表/禁用用户
 * - 备选流程：管理员启用已禁用用户、强制下线其他用户
 * - 异常流程：限流 429、未登录访问受保护端点 401、USER 访问 admin 端点 403、
 *            管理员不能踢自己、请求体格式错误 400、参数类型转换错误 400
 */
@Testcontainers
@DisplayName("补充用例：公开端点 + 限流 + 异常 + 管理员权限")
class PublicAccessAndRateLimitIntegrationIT extends AbstractIntegrationIT {

    private static final String FIXED_IP = "10.99.99.99";

    // ==================== 主成功流程：公开端点 ====================

    @Test
    @DisplayName("主成功 · 公开 GET /api/regions 返回省市区数据")
    void shouldGetRegionsPublicly() throws Exception {
        mockMvc.perform(get("/api/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", not(empty())))
                .andExpect(jsonPath("$.data[0].name", not(blankOrNullString())));
    }

    @Test
    @DisplayName("主成功 · 公开 GET /api/categories 返回分类列表")
    void shouldGetCategoriesPublicly() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", not(empty())));
    }

    @Test
    @DisplayName("主成功 · 公开 GET /api/products 分页列表")
    void shouldListProductsPublicly() throws Exception {
        TestUser seller = createTestUser();
        createProduct(seller.userId(), "公开列表商品", 5000, 1);

        mockMvc.perform(get("/api/products").param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", not(empty())));
    }

    @Test
    @DisplayName("主成功 · 公开 GET /api/users/{id}/public 卖家信息")
    void shouldGetPublicUserInfo() throws Exception {
        TestUser seller = createTestUser();
        createProduct(seller.userId(), "卖家公开信息商品", 1000, 1);

        mockMvc.perform(get("/api/users/" + seller.userId() + "/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(seller.userId()))
                .andExpect(jsonPath("$.data.nickname", not(blankOrNullString())));
    }

    @Test
    @DisplayName("主成功 · 公开 GET /api/users/{id}/products 卖家在售商品")
    void shouldGetSellerProductsPublicly() throws Exception {
        TestUser seller = createTestUser();
        createProduct(seller.userId(), "卖家在售1", 1000, 1);
        createProduct(seller.userId(), "卖家在售2", 2000, 1);

        mockMvc.perform(get("/api/users/" + seller.userId() + "/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(2)));
    }

    @Test
    @DisplayName("主成功 · 健康检查端点 /actuator/health 返回 UP")
    void shouldHealthCheckReturnUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("主成功 · OpenAPI 文档端点 /v3/api-docs 可访问")
    void shouldAccessApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi", not(blankOrNullString())))
                .andExpect(jsonPath("$.paths", not(empty())));
    }

    // ==================== 备选流程：管理员操作 ====================

    @Nested
    @DisplayName("管理员操作")
    class AdminOpsFlow {

        private TestUser createAdmin() {
            return createTestUser(Role.ADMIN);
        }

        @Test
        @DisplayName("备选 · 管理员分页查询用户列表")
        void shouldAdminListUsers() throws Exception {
            TestUser admin = createAdmin();
            TestUser u1 = createTestUser();
            TestUser u2 = createTestUser();

            mockMvc.perform(authGet(admin.token(), "/api/admin/users")
                            .param("page", "0").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content", not(empty())))
                    .andExpect(jsonPath("$.data.totalElements", greaterThanOrEqualTo(2)));
        }

        @Test
        @DisplayName("备选 · 管理员按昵称关键词搜索用户")
        void shouldAdminSearchUsersByKeyword() throws Exception {
            TestUser admin = createAdmin();
            TestUser u = createTestUser();

            mockMvc.perform(authGet(admin.token(), "/api/admin/users")
                            .param("keyword", "用户" + (111110 + u.userId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", not(empty())));
        }

        @Test
        @DisplayName("备选 · 管理员禁用→启用用户")
        void shouldAdminDisableAndEnableUser() throws Exception {
            TestUser admin = createAdmin();
            TestUser target = createTestUser();

            // 禁用
            mockMvc.perform(authPut(admin.token(),
                            "/api/admin/users/" + target.userId() + "/disable?disabled=true",
                            Map.of()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DISABLED"));

            // 启用
            mockMvc.perform(authPut(admin.token(),
                            "/api/admin/users/" + target.userId() + "/disable?disabled=false",
                            Map.of()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("备选 · 管理员强制下线其他用户")
        void shouldAdminKickUser() throws Exception {
            TestUser admin = createAdmin();
            TestUser target = createTestUser();

            mockMvc.perform(authPost(admin.token(),
                            "/api/admin/users/" + target.userId() + "/kick", Map.of()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("用户已被强制下线"));
        }

        @Test
        @DisplayName("备选 · 管理员查看在线用户列表")
        void shouldAdminListOnlineUsers() throws Exception {
            TestUser admin = createAdmin();
            mockMvc.perform(authGet(admin.token(), "/api/admin/users/online"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ==================== 异常流程 ====================

    @Test
    @DisplayName("异常 · 注册限流：5 次/分钟后第 6 次返回 429")
    void shouldRateLimitRegister() throws Exception {
        // 使用同一固定 IP，连续 6 次注册
        for (int i = 0; i < 5; i++) {
            String phone = "1390099" + String.format("%04d", i);
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(Map.of(
                                    "identityType", "PHONE",
                                    "identifier", phone,
                                    "password", "Pass1234")))
                            .header("X-Forwarded-For", FIXED_IP))
                    .andExpect(status().isCreated());
        }

        // 第 6 次应被限流
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "identityType", "PHONE",
                                "identifier", "13900990005",
                                "password", "Pass1234")))
                        .header("X-Forwarded-For", FIXED_IP))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    @DisplayName("异常 · 未登录访问受保护端点返回 401")
    void shouldRejectProtectedEndpointWithoutAuth() throws Exception {
        // /api/users/profile 需要登录
        mockMvc.perform(get("/api/users/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("异常 · USER 角色访问 admin 端点返回 403")
    void shouldRejectAdminEndpointForUserRole() throws Exception {
        TestUser user = createTestUser(); // USER 角色
        mockMvc.perform(authGet(user.token(), "/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("异常 · 管理员强制下线自己返回 403")
    void shouldRejectAdminKickSelf() throws Exception {
        TestUser admin = createTestUser(Role.ADMIN);
        mockMvc.perform(authPost(admin.token(),
                        "/api/admin/users/" + admin.userId() + "/kick", Map.of()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("异常 · 请求体格式错误返回 400 BAD_REQUEST")
    void shouldRejectMalformedJsonBody() throws Exception {
        TestUser user = createTestUser();
        mockMvc.perform(post("/api/users/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{这不是有效的JSON")
                        .header("Authorization", "Bearer " + user.token())
                        .header("X-Forwarded-For", "10.0.0.99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("异常 · 路径参数类型转换错误返回 400")
    void shouldRejectPathParamTypeMismatch() throws Exception {
        // /api/products/{id} 路径变量 id 应为数字，传字符串触发 MethodArgumentTypeMismatchException
        mockMvc.perform(get("/api/products/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("异常 · 不存在的路由返回 404")
    void shouldRejectNonExistentRoute() throws Exception {
        mockMvc.perform(get("/api/non-existent-endpoint"))
                .andExpect(status().isNotFound());
    }
}
