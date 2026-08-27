package com.secondhand.integration;

import com.secondhand.order.entity.Order;
import com.secondhand.order.entity.OrderStatus;
import com.secondhand.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 集成测试 · 补充用例：跨模块端到端（E2E）全链路
 *
 * 覆盖范围：
 * - 模块间调用：把 auth → product → favorite → comment → offer → order → payment
 *              → shipment → logistics → rating → aftersale → report → admin
 *              等十余个模块串成一条端到端业务流，验证真实场景下多个 Service 之间的协作
 * - 数据库访问：贯穿 users / products / favorites / comments / offers / orders /
 *              shipments / ratings / after_sale_requests / reports 共 10 张表
 * - 对外接口：上述各模块的核心 REST 端点
 *
 * 设计思路：
 * - 单一用例的 IT 测试已经覆盖了主成功/备选/异常流程
 * - 本测试聚焦"跨用例联动"：验证一个完整业务闭环
 * - 不重复断言所有字段，只断言关键节点和状态推进
 *
 * 用例流程覆盖：
 * - 主成功流程 1：买家视角完整闭环（注册→浏览→收藏→评论→出价→支付→收货→评价→售后）
 * - 主成功流程 2：卖家视角完整闭环（注册→发布→收报价→接受→发货→被评价→被举报）
 * - 主成功流程 3：管理员视角完整闭环（看板→举报处理→售后仲裁→用户禁用）
 */
@Testcontainers
@DisplayName("补充用例：跨模块端到端（E2E）全链路")
class CrossModuleE2EIntegrationIT extends AbstractIntegrationIT {

    @Autowired OrderRepository orderRepo;

    // ==================== 主成功流程 1：买家视角完整闭环 ====================

    @Test
    @DisplayName("E2E · 买家视角：注册→浏览→收藏→评论→出价→接受→支付→收货→评价")
    void shouldCompleteBuyerEndToEndFlow() throws Exception {
        // 1. 卖家与买家各自注册（用基类直接构造，绕开 HTTP 限流）
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();

        // 2. 卖家发布商品
        long productId = extractId(mockMvc.perform(authPost(seller.token(), "/api/products",
                        Map.of(
                                "title", "E2E 测试商品",
                                "priceCent", 299900,
                                "coverImageUrl", "https://cdn.example.com/e2e.jpg",
                                "description", "E2E 测试：九成新国行 iPhone",
                                "quantity", 1,
                                "condition", "NINE_TENTHS",
                                "freeShipping", true,
                                "shippingFeeCent", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andReturn());

        // 3. 买家公开浏览商品
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("E2E 测试商品"));

        // 4. 买家收藏
        mockMvc.perform(authPost(buyer.token(), "/api/products/" + productId + "/favorite"))
                .andExpect(status().isOk());

        mockMvc.perform(authGet(buyer.token(), "/api/users/favorites"))
                .andExpect(jsonPath("$.data.content", hasSize(1)));

        // 5. 买家发表评论
        mockMvc.perform(authPost(buyer.token(), "/api/products/" + productId + "/comments",
                        Map.of("content", "E2E 评论：还在吗？")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/" + productId + "/comments"))
                .andExpect(jsonPath("$.data", hasSize(1)));

        // 6. 买家出价（议价）
        long offerId = extractId(mockMvc.perform(authPost(buyer.token(),
                        "/api/products/" + productId + "/offers",
                        Map.of("offeredPriceCent", 250000, "message", "能 2500 出吗？")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn());

        // 7. 卖家接受报价 → 自动生成订单
        long orderId = extractId(mockMvc.perform(authPost(seller.token(),
                        "/api/offers/" + offerId + "/accept", Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAIT_PAY"))
                .andReturn());

        // 8. 买家补全收货信息（接受报价时生成的订单 receiver 字段为空）
        mockMvc.perform(authPut(buyer.token(), "/api/orders/" + orderId + "/receiver",
                        Map.of(
                                "receiverName", "E2E收件人",
                                "receiverPhone", "13900001111",
                                "receiverAddress", "北京市E2E街道1号")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiverName").value("E2E收件人"));

        // 9. 买家支付订单
        mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/pay", Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAIT_DELIVER"));

        // 10. 卖家发货
        mockMvc.perform(authPost(seller.token(), "/api/orders/" + orderId + "/ship",
                        Map.of("carrierCode", "SF", "trackingNo", "SF-E2E-001")))
                .andExpect(status().isOk());

        // 11. 买家确认收货
        mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/confirm", Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // 12. 买家评分
        mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/rate",
                        Map.of("score", 5, "comment", "E2E 全流程很顺畅")))
                .andExpect(status().isOk());

        // 13. 卖家评分统计公开可查
        mockMvc.perform(get("/api/users/" + seller.userId() + "/rating"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ==================== 主成功流程 2：卖家视角完整闭环 ====================

    @Test
    @DisplayName("E2E · 卖家视角：发布→被收藏→被评论→被出价→接受→发货→被评价")
    void shouldCompleteSellerEndToEndFlow() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();

        // 1. 发布两个商品
        long pid1 = extractId(mockMvc.perform(authPost(seller.token(), "/api/products",
                        Map.of(
                                "title", "卖家E2E-商品1",
                                "priceCent", 100000,
                                "coverImageUrl", "https://cdn.example.com/1.jpg",
                                "description", "x", "quantity", 1,
                                "condition", "NEW", "freeShipping", false,
                                "shippingFeeCent", 1000)))
                .andExpect(status().isOk()).andReturn());

        long pid2 = createProduct(seller.userId(), "卖家E2E-商品2（DB直造）", 8000, 1);

        // 2. 卖家查看自己的在售商品
        mockMvc.perform(authGet(seller.token(), "/api/my-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(2))));

        // 3. 买家收藏商品1 + 评论
        mockMvc.perform(authPost(buyer.token(), "/api/products/" + pid1 + "/favorite"))
                .andExpect(status().isOk());

        mockMvc.perform(authPost(buyer.token(), "/api/products/" + pid1 + "/comments",
                        Map.of("content", "卖家E2E 评论")))
                .andExpect(status().isOk());

        // 4. 买家对商品2 出价
        long offerId = extractId(mockMvc.perform(authPost(buyer.token(),
                        "/api/products/" + pid2 + "/offers",
                        Map.of("offeredPriceCent", 7000, "message", "70 块")))
                .andExpect(status().isOk()).andReturn());

        // 5. 卖家查看自己收到的报价
        mockMvc.perform(authGet(seller.token(), "/api/seller-offers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));

        // 6. 卖家接受报价
        long orderId = extractId(mockMvc.perform(authPost(seller.token(),
                        "/api/offers/" + offerId + "/accept", Map.of()))
                .andExpect(status().isOk()).andReturn());

        // 7. 买家补全收货信息 + 支付
        mockMvc.perform(authPut(buyer.token(), "/api/orders/" + orderId + "/receiver",
                        Map.of("receiverName", "x", "receiverPhone", "x", "receiverAddress", "x")))
                .andExpect(status().isOk());
        mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/pay", Map.of()))
                .andExpect(status().isOk());

        // 8. 卖家发货
        mockMvc.perform(authPost(seller.token(), "/api/orders/" + orderId + "/ship",
                        Map.of("carrierCode", "YT", "trackingNo", "YT-E2E-002")))
                .andExpect(status().isOk());

        // 9. 买家确认收货 + 评分
        mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/confirm", Map.of()))
                .andExpect(status().isOk());
        mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/rate",
                        Map.of("score", 4, "comment", "卖家E2E好评")))
                .andExpect(status().isOk());

        // 10. 卖家查看自己卖出的订单
        mockMvc.perform(authGet(seller.token(), "/api/orders/sold"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)));

        // 11. 验证数据库订单状态已 COMPLETED
        Order order = orderRepo.findById(orderId).orElseThrow();
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new AssertionError("E2E 卖家流结束时订单状态应为 COMPLETED，实际=" + order.getStatus());
        }
    }

    // ==================== 主成功流程 3：管理员视角完整闭环 ====================

    @Test
    @DisplayName("E2E · 管理员视角：看板→处理举报→仲裁售后→禁用用户")
    void shouldCompleteAdminEndToEndFlow() throws Exception {
        TestUser admin = createTestUser(com.secondhand.auth.entity.Role.ADMIN);
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        TestUser reporter = createTestUser();

        // 1. 准备一个 COMPLETED 订单，便于发起售后
        long pid = createProduct(seller.userId(), "E2E管理员商品", 5000, 1);
        long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                        Map.of("productId", pid,
                                "receiverName", "x", "receiverPhone", "x", "receiverAddress", "x")))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/pay", Map.of()))
                .andExpect(status().isOk());
        mockMvc.perform(authPost(seller.token(), "/api/orders/" + orderId + "/ship",
                        Map.of("carrierCode", "SF", "trackingNo", "SF-E2E-003")))
                .andExpect(status().isOk());
        mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/confirm", Map.of()))
                .andExpect(status().isOk());

        // 2. 买家发起售后
        long afterSaleId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/after-sale",
                        Map.of(
                                "orderId", orderId,
                                "type", "REFUND_NOT_SHIPPED",
                                "reason", "E2E 管理员流：商品与描述不符",
                                "refundAmountCent", 5000,
                                "buyerEvidence", "买家举证链接")))
                .andExpect(status().isOk()).andReturn());

        // 3. 举报人发起举报（针对该商品）
        long reportId = extractId(mockMvc.perform(authPost(reporter.token(),
                        "/api/products/" + pid + "/report",
                        Map.of(
                                "reasonType", "COUNTERFEIT",
                                "description", "E2E 管理员流：举报假货")))
                .andExpect(status().isOk()).andReturn());

        // 4. 管理员查看仪表板
        mockMvc.perform(authGet(admin.token(), "/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 5. 管理员处理举报（PUT /api/admin/reports/{id}/handle）
        mockMvc.perform(authPut(admin.token(), "/api/admin/reports/" + reportId + "/handle",
                        Map.of("handleNote", "E2E管理员：处理完成")))
                .andExpect(status().isOk());

        // 6. 管理员仲裁售后（POST /api/admin/after-sale/{id}/arbitrate）
        mockMvc.perform(authPost(admin.token(), "/api/admin/after-sale/" + afterSaleId + "/arbitrate",
                        Map.of(
                                "result", "FULL_REFUND",
                                "responsibility", "SELLER",
                                "shippingPaidBy", "SELLER",
                                "shippingCostCent", 0,
                                "partialRefundCent", null,
                                "note", "E2E管理员：全额退款")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));

        // 7. 管理员禁用违规卖家
        mockMvc.perform(authPut(admin.token(),
                        "/api/admin/users/" + seller.userId() + "/disable?disabled=true",
                        Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        // 8. 验证卖家被禁用后无法再登录
        mockMvc.perform(post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "identityType", "PHONE",
                                "identifier", seller.phone(),
                                "password", seller.password())))
                        .header("X-Forwarded-For", "10.0.0.88"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
