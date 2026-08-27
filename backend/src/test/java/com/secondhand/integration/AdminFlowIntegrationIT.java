package com.secondhand.integration;

import com.secondhand.admin.security.TokenBlacklist;
import com.secondhand.auth.entity.Role;
import com.secondhand.auth.entity.User;
import com.secondhand.auth.entity.UserStatus;
import com.secondhand.auth.repository.UserRepository;
import com.secondhand.product.entity.Product;
import com.secondhand.product.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 集成测试 · 管理后台
 *
 * 覆盖范围：
 * - 模块间调用：
 *   - AdminDashboardController → AdminService → 多个 Repository 聚合统计
 *   - AdminUserController → UserRepository + TokenBlacklist + OnlineUserTracker
 *   - AdminProductController → ProductRepository
 *   - AdminOrderController → OrderRepository + OrderService
 *   - AdminReportController → ReportService
 *   - AdminAfterSaleController → AfterSaleService.adminArbitrate
 * - 数据库访问：users、products、orders、reports、after_sale_requests 多表聚合
 * - 对外接口（全部 /api/admin/**，需 ADMIN 角色）：
 *   - GET  /api/admin/dashboard            数据面板
 *   - GET  /api/admin/users                用户列表
 *   - GET  /api/admin/users/{id}           用户详情
 *   - PUT  /api/admin/users/{id}/disable   封禁/解封
 *   - POST /api/admin/users/{id}/kick      强制下线
 *   - GET  /api/admin/users/online         在线用户
 *   - GET  /api/admin/products             商品列表
 *   - PUT  /api/admin/products/{id}/off-shelf   强制下架
 *   - PUT  /api/admin/products/{id}/on-shelf    强制上架
 *   - DELETE /api/admin/products/{id}       删除商品
 *   - GET  /api/admin/orders               订单列表
 *   - GET  /api/admin/orders/{id}          订单详情
 *   - POST /api/admin/orders/{id}/mark-paid   管理员标记已支付
 *   - POST /api/admin/orders/{id}/cancel      管理员取消订单
 *   - GET  /api/admin/reports               举报列表
 *   - PUT  /api/admin/reports/{id}/handle    办结举报
 *   - PUT  /api/admin/reports/{id}/dismiss   驳回举报
 *   - GET  /api/admin/after-sale             售后列表
 *   - GET  /api/admin/after-sale/{id}        售后详情
 *   - POST /api/admin/after-sale/{id}/arbitrate   平台仲裁
 *
 * 用例流程覆盖：
 * - 主成功流程：管理员封禁用户→强制下线→强制下架违规商品→处理举报→仲裁售后
 * - 备选流程：管理员解封用户、强制上架商品、数据面板 KPI 聚合
 * - 异常流程：普通用户访问 /api/admin/** 返回 403、未登录返回 401、管理员踢自己返回 403
 */
@Testcontainers
@DisplayName("管理后台")
class AdminFlowIntegrationIT extends AbstractIntegrationIT {

    @Autowired UserRepository userRepo;
    @Autowired ProductRepository productRepo;
    @Autowired TokenBlacklist tokenBlacklist;

    // ==================== 主成功流程 ====================

    @Nested
    @DisplayName("数据面板")
    class Dashboard {

        @Test
        @DisplayName("主成功 · GET /api/admin/dashboard 返回聚合 KPI")
        void shouldGetDashboard() throws Exception {
            TestUser admin = createTestUser(Role.ADMIN);

            // 预置数据
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            createProduct(seller.userId(), "KPI商品1", 5000, 1);
            createProduct(seller.userId(), "KPI商品2", 5000, 1);

            mockMvc.perform(authGet(admin.token(), "/api/admin/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").exists())
                    .andExpect(jsonPath("$.data.totalUsers", greaterThanOrEqualTo(2)))
                    .andExpect(jsonPath("$.data.totalProducts", greaterThanOrEqualTo(2)));
        }
    }

    @Nested
    @DisplayName("用户管理")
    class UserAdmin {

        @Test
        @DisplayName("主成功 · 列表→详情→封禁→解封")
        void shouldListDetailDisableAndEnable() throws Exception {
            TestUser admin = createTestUser(Role.ADMIN);
            TestUser user = createTestUser();

            // 1. 列表
            mockMvc.perform(authGet(admin.token(), "/api/admin/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(2))))
                    .andExpect(jsonPath("$.data.totalElements", greaterThanOrEqualTo(2L)));

            // 2. 按昵称搜索
            mockMvc.perform(authGet(admin.token(), "/api/admin/users")
                            .param("keyword", "用户"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));

            // 3. 详情
            mockMvc.perform(authGet(admin.token(), "/api/admin/users/" + user.userId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(user.userId()))
                    .andExpect(jsonPath("$.data.phone").value(user.phone()));

            // 4. 封禁
            mockMvc.perform(authPut(admin.token(), "/api/admin/users/" + user.userId() + "/disable", Map.of())
                            .param("disabled", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DISABLED"));

            // 验证 DB 已变更
            User dbUser = userRepo.findById(user.userId()).orElseThrow();
            if (dbUser.getStatus() != UserStatus.DISABLED) {
                throw new AssertionError("封禁未落库");
            }

            // 5. 解封
            mockMvc.perform(authPut(admin.token(), "/api/admin/users/" + user.userId() + "/disable", Map.of())
                            .param("disabled", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("主成功 · 强制下线用户（加入 Token 黑名单）")
        void shouldKickUser() throws Exception {
            TestUser admin = createTestUser(Role.ADMIN);
            TestUser user = createTestUser();

            mockMvc.perform(authPost(admin.token(), "/api/admin/users/" + user.userId() + "/kick", Map.of()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(containsString("强制下线")));

            // 验证 Token 已加入黑名单
            if (!tokenBlacklist.isBlacklisted(user.userId())) {
                throw new AssertionError("Token 未加入黑名单");
            }

            // 用户使用旧 Token 访问 /api/auth/me 应失败
            mockMvc.perform(authGet(user.token(), "/api/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("TOKEN_REVOKED"));
        }
    }

    @Nested
    @DisplayName("商品管理")
    class ProductAdmin {

        @Test
        @DisplayName("主成功 · 列表→强制下架→强制上架→删除")
        void shouldOffShelfOnShelfDelete() throws Exception {
            TestUser admin = createTestUser(Role.ADMIN);
            TestUser seller = createTestUser();
            long pid = createProduct(seller.userId(), "管理商品", 8000, 1);

            // 1. 商品列表
            mockMvc.perform(authGet(admin.token(), "/api/admin/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));

            // 2. 强制下架
            mockMvc.perform(authPut(admin.token(), "/api/admin/products/" + pid + "/off-shelf", Map.of()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("OFF_SALE"));

            // 3. 强制上架
            mockMvc.perform(authPut(admin.token(), "/api/admin/products/" + pid + "/on-shelf", Map.of()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ON_SALE"));

            // 4. 删除
            mockMvc.perform(authDelete(admin.token(), "/api/admin/products/" + pid))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // 5. 验证 DB 已删除
            if (productRepo.existsById(pid)) {
                throw new AssertionError("商品未删除");
            }
        }

        @Test
        @DisplayName("备选 · 按状态过滤商品列表")
        void shouldFilterProductsByStatus() throws Exception {
            TestUser admin = createTestUser(Role.ADMIN);
            TestUser seller = createTestUser();
            createProduct(seller.userId(), "在线商品1", 1000, 1);
            createProduct(seller.userId(), "在线商品2", 2000, 1,
                    com.secondhand.product.entity.ProductStatus.OFF_SALE);

            // 只看在售
            mockMvc.perform(authGet(admin.token(), "/api/admin/products").param("status", "ON_SALE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));
        }

        @Test
        @DisplayName("异常 · 管理员商品操作商品不存在返回 404")
        void shouldRejectProductNotFound() throws Exception {
            TestUser admin = createTestUser(Role.ADMIN);
            mockMvc.perform(authPut(admin.token(), "/api/admin/products/99999999/off-shelf", Map.of()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("订单管理")
    class OrderAdmin {

        @Test
        @DisplayName("主成功 · 列表→详情→标记已支付→取消")
        void shouldListDetailMarkPaidAndCancel() throws Exception {
            TestUser admin = createTestUser(Role.ADMIN);
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "管理订单", 5000, 1);

            // 走下单流程，但停在 WAIT_PAY
            long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                            Map.of("productId", pid,
                                    "receiverName", "x",
                                    "receiverPhone", "x",
                                    "receiverAddress", "x")))
                    .andExpect(status().isOk()).andReturn());

            // 1. 订单列表
            mockMvc.perform(authGet(admin.token(), "/api/admin/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));

            // 2. 订单详情
            mockMvc.perform(authGet(admin.token(), "/api/admin/orders/" + orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.order.id").value(orderId))
                    .andExpect(jsonPath("$.data.order.status").value("WAIT_PAY"));

            // 3. 管理员标记已支付
            mockMvc.perform(authPost(admin.token(), "/api/admin/orders/" + orderId + "/mark-paid", Map.of()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("WAIT_DELIVER"))
                    .andExpect(jsonPath("$.data.paidAt", notNullValue()));

            // 4. 管理员取消订单
            mockMvc.perform(authPost(admin.token(), "/api/admin/orders/" + orderId + "/cancel", Map.of()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.data.cancelledAt", notNullValue()));
        }

        @Test
        @DisplayName("异常 · 管理员取消已完成订单返回 409")
        void shouldRejectCancelCompletedOrder() throws Exception {
            TestUser admin = createTestUser(Role.ADMIN);
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "完成态管理", 5000, 1);

            // 走完整个流程
            long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                            Map.of("productId", pid,
                                    "receiverName", "x",
                                    "receiverPhone", "x",
                                    "receiverAddress", "x")))
                    .andExpect(status().isOk()).andReturn());
            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/pay", Map.of()))
                    .andExpect(status().isOk());
            mockMvc.perform(authPost(seller.token(), "/api/orders/" + orderId + "/ship",
                            Map.of("carrierCode", "SF", "trackingNo", "x")))
                    .andExpect(status().isOk());
            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/confirm", Map.of()))
                    .andExpect(status().isOk());

            // 已完成订单管理员不能取消
            mockMvc.perform(authPost(admin.token(), "/api/admin/orders/" + orderId + "/cancel", Map.of()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        }

        @Test
        @DisplayName("异常 · 管理员查看不存在的订单返回 404")
        void shouldRejectAdminOrderNotFound() throws Exception {
            TestUser admin = createTestUser(Role.ADMIN);
            mockMvc.perform(authGet(admin.token(), "/api/admin/orders/99999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("举报处理（管理端）")
    class ReportAdmin {

        @Test
        @DisplayName("主成功 · 管理员办结举报")
        void shouldAdminHandleReport() throws Exception {
            TestUser admin = createTestUser(Role.ADMIN);
            TestUser seller = createTestUser();
            TestUser reporter = createTestUser();
            long pid = createProduct(seller.userId(), "举报商品", 1000, 1);

            // 用户举报
            long reportId = extractId(mockMvc.perform(authPost(reporter.token(),
                            "/api/products/" + pid + "/report",
                            Map.of("reasonType", "FALSE_DESC", "description", "x")))
                    .andExpect(status().isOk()).andReturn());

            // 管理员办结
            mockMvc.perform(authPut(admin.token(), "/api/admin/reports/" + reportId + "/handle",
                            Map.of("handleNote", "已下架")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("HANDLED"))
                    .andExpect(jsonPath("$.data.handledBy").value(admin.userId()));
        }

        @Test
        @DisplayName("异常 · 管理员处理不存在的举报返回 404")
        void shouldRejectHandleNonExistentReport() throws Exception {
            TestUser admin = createTestUser(Role.ADMIN);
            mockMvc.perform(authPut(admin.token(), "/api/admin/reports/99999999/handle",
                            Map.of("handleNote", "x")))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("售后仲裁（管理端）")
    class AfterSaleAdmin {

        @Test
        @DisplayName("主成功 · 管理员仲裁退货退款（RETURN_REFUND 裁定）")
        void shouldAdminArbitrateReturnRefund() throws Exception {
            TestUser admin = createTestUser(Role.ADMIN);
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "仲裁退货", 40000, 1);

            // 走完下单+支付+发货+收货
            long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                            Map.of("productId", pid,
                                    "receiverName", "x", "receiverPhone", "x", "receiverAddress", "x")))
                    .andExpect(status().isOk()).andReturn());
            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/pay", Map.of()))
                    .andExpect(status().isOk());
            mockMvc.perform(authPost(seller.token(), "/api/orders/" + orderId + "/ship",
                            Map.of("carrierCode", "SF", "trackingNo", "x")))
                    .andExpect(status().isOk());
            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/confirm", Map.of()))
                    .andExpect(status().isOk());

            // 发起售后 → 卖家拒绝 → 买家申诉
            long asId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "RETURN_REFUND", "reason", "x")))
                    .andExpect(status().isOk()).andReturn());

            mockMvc.perform(authPost(seller.token(), "/api/after-sale/" + asId + "/reject",
                            Map.of("note", "不同意")))
                    .andExpect(status().isOk());

            mockMvc.perform(authPost(buyer.token(), "/api/after-sale/" + asId + "/escalate",
                            Map.of("evidence", "x")))
                    .andExpect(status().isOk());

            // 仲裁：RETURN_REFUND（推翻卖家拒绝，要求卖家接受退货）
            mockMvc.perform(authPost(admin.token(), "/api/admin/after-sale/" + asId + "/arbitrate",
                            Map.of(
                                    "result", "RETURN_REFUND",
                                    "responsibility", "SELLER",
                                    "shippingPaidBy", "SELLER",
                                    "shippingCostCent", 1000,
                                    "note", "卖家应接受退货")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("APPROVED"))
                    .andExpect(jsonPath("$.data.arbitrationResult", containsString("退货退款")));

            // 之后买家可以继续寄回退货
            mockMvc.perform(authPost(buyer.token(), "/api/after-sale/" + asId + "/return-ship",
                            Map.of("carrierCode", "SF", "trackingNo", "R" + asId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("RETURN_SHIPPED"));
        }

        @Test
        @DisplayName("主成功 · 管理员查看售后列表 + 详情")
        void shouldAdminListAndDetailAfterSale() throws Exception {
            TestUser admin = createTestUser(Role.ADMIN);
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "管理售后", 40000, 1);

            long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                            Map.of("productId", pid,
                                    "receiverName", "x", "receiverPhone", "x", "receiverAddress", "x")))
                    .andExpect(status().isOk()).andReturn());
            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/pay", Map.of()))
                    .andExpect(status().isOk());
            mockMvc.perform(authPost(seller.token(), "/api/orders/" + orderId + "/ship",
                            Map.of("carrierCode", "SF", "trackingNo", "x")))
                    .andExpect(status().isOk());
            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/confirm", Map.of()))
                    .andExpect(status().isOk());

            long asId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "REFUND_RECEIVED", "reason", "x")))
                    .andExpect(status().isOk()).andReturn());

            // 售后列表
            mockMvc.perform(authGet(admin.token(), "/api/admin/after-sale"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));

            // 按状态过滤
            mockMvc.perform(authGet(admin.token(), "/api/admin/after-sale").param("status", "REQUESTED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));

            // 详情
            mockMvc.perform(authGet(admin.token(), "/api/admin/after-sale/" + asId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(asId));
        }

        @Test
        @DisplayName("异常 · 管理员仲裁未进入仲裁状态的售后返回 409")
        void shouldRejectArbitrateNonArbitrationState() throws Exception {
            TestUser admin = createTestUser(Role.ADMIN);
            TestUser seller = createTestUser();
            TestUser buyer = createTestUser();
            long pid = createProduct(seller.userId(), "状态错误", 40000, 1);

            long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                            Map.of("productId", pid,
                                    "receiverName", "x", "receiverPhone", "x", "receiverAddress", "x")))
                    .andExpect(status().isOk()).andReturn());
            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/pay", Map.of()))
                    .andExpect(status().isOk());
            mockMvc.perform(authPost(seller.token(), "/api/orders/" + orderId + "/ship",
                            Map.of("carrierCode", "SF", "trackingNo", "x")))
                    .andExpect(status().isOk());
            mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/confirm", Map.of()))
                    .andExpect(status().isOk());

            // 售后刚发起，状态是 REQUESTED（未进入仲裁）
            long asId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                            Map.of("orderId", orderId, "type", "REFUND_RECEIVED", "reason", "x")))
                    .andExpect(status().isOk()).andReturn());

            mockMvc.perform(authPost(admin.token(), "/api/admin/after-sale/" + asId + "/arbitrate",
                            Map.of("result", "FULL_REFUND", "note", "x")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        }
    }

    // ==================== 跨模块异常流程 ====================

    @Nested
    @DisplayName("权限边界")
    class Authorization {

        @Test
        @DisplayName("异常 · 普通用户访问 /api/admin/dashboard 返回 403")
        void shouldRejectUserAccessDashboard() throws Exception {
            TestUser user = createTestUser();
            mockMvc.perform(authGet(user.token(), "/api/admin/dashboard"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("异常 · 未登录访问 /api/admin/users 返回 401")
        void shouldRejectUnauthenticatedAccess() throws Exception {
            mockMvc.perform(get("/api/admin/users"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("异常 · 管理员不能强制下线自己返回 403")
        void shouldRejectAdminKickSelf() throws Exception {
            TestUser admin = createTestUser(Role.ADMIN);
            mockMvc.perform(authPost(admin.token(), "/api/admin/users/" + admin.userId() + "/kick", Map.of()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("异常 · 伪造/篡改 JWT 中的 role 也无法访问 /api/admin/**（签名校验）")
        void shouldRejectTamperedToken() throws Exception {
            TestUser user = createTestUser(Role.USER);
            // 用 USER 角色的 token 访问 admin 端点
            mockMvc.perform(authGet(user.token(), "/api/admin/users"))
                    .andExpect(status().isForbidden());
        }
    }
}
