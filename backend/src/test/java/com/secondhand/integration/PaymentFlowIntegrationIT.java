package com.secondhand.integration;

import com.secondhand.order.entity.Order;
import com.secondhand.order.entity.OrderStatus;
import com.secondhand.order.repository.OrderRepository;
import com.secondhand.payment.MockPaymentService;
import com.secondhand.payment.PaymentService;
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
 * 集成测试 · 补充用例：独立支付端点
 *
 * 覆盖范围：
 * - 模块间调用：PaymentController → PaymentService（MockPaymentService）→ OrderRepository
 *              → MockPaymentService.mockPay（模拟回调）→ 订单状态联动
 * - 数据库访问：orders 表（支付完成后状态推进）
 * - 对外接口：
 *   - POST /api/payments                   （创建支付单）
 *   - GET  /api/payments/{paymentNo}        （查询支付状态）
 *   - POST /api/payments/{paymentNo}/mock-pay （模拟支付成功）
 *
 * 用例流程覆盖：
 * - 主成功流程：下单 → 创建支付单 → 查询支付单（WAIT_PAY）→ mock-pay → 订单推进到 WAIT_DELIVER
 * - 备选流程：指定微信支付 / 支付宝，未指定时默认支付宝
 * - 异常流程：未登录访问、订单不存在、非买家创建他人订单支付、支付不存在的订单
 *
 * 说明：与 OrderFlowIntegrationIT 的 /api/orders/{id}/pay 不同，本测试聚焦于
 * 独立支付单系统的创建、查询、Mock 回调链路，二者互为补充。
 */
@Testcontainers
@DisplayName("补充用例：独立支付端点")
class PaymentFlowIntegrationIT extends AbstractIntegrationIT {

    @Autowired OrderRepository orderRepo;
    @Autowired PaymentService paymentService;
    @Autowired MockPaymentService mockPaymentService;

    private long createOrder(TestUser buyer, TestUser seller, long productId) throws Exception {
        return extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                        Map.of(
                                "productId", productId,
                                "receiverName", "支付测试",
                                "receiverPhone", "13900000000",
                                "receiverAddress", "北京")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAIT_PAY"))
                .andReturn());
    }

    // ==================== 主成功流程 ====================

    @Test
    @DisplayName("主成功 · 创建支付单→查询→Mock支付→订单推进到待发货")
    void shouldCreateQueryAndMockPay() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "支付测试商品", 8800, 1);
        long orderId = createOrder(buyer, seller, pid);

        // 1. 创建支付单
        String paymentNo = extractField(mockMvc.perform(authPost(buyer.token(), "/api/payments",
                        Map.of("orderId", orderId, "method", "ALIPAY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentNo", startsWith("PAY-")))
                .andExpect(jsonPath("$.data.status").value("WAIT_PAY"))
                .andReturn(), "paymentNo");

        // 2. 查询支付状态（应仍为 WAIT_PAY）
        mockMvc.perform(authGet(buyer.token(), "/api/payments/" + paymentNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("WAIT_PAY"));

        // 3. 模拟支付成功（Mock 回调）
        mockMvc.perform(authPost(buyer.token(),
                                "/api/payments/" + paymentNo + "/mock-pay?orderId=" + orderId,
                        Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("支付成功"));

        // 4. 验证订单状态已推进到 WAIT_DELIVER
        Order o = orderRepo.findById(orderId).orElseThrow();
        if (o.getStatus() != OrderStatus.WAIT_DELIVER) {
            throw new AssertionError("Mock 支付后订单状态未推进，实际=" + o.getStatus());
        }

        // 5. 再次查询支付单状态应为 PAID
        mockMvc.perform(authGet(buyer.token(), "/api/payments/" + paymentNo))
                .andExpect(jsonPath("$.data").value("PAID"));
    }

    // ==================== 备选流程 ====================

    @Test
    @DisplayName("备选 · 不指定支付方式默认走支付宝")
    void shouldDefaultToAlipayWhenMethodMissing() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "默认支付方式商品", 5000, 1);
        long orderId = createOrder(buyer, seller, pid);

        mockMvc.perform(authPost(buyer.token(), "/api/payments",
                        Map.of("orderId", orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentNo", startsWith("PAY-")))
                .andExpect(jsonPath("$.data.status").value("WAIT_PAY"));
    }

    @Test
    @DisplayName("备选 · 微信支付方式可创建支付单")
    void shouldCreateWechatPayment() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "微信支付商品", 5000, 1);
        long orderId = createOrder(buyer, seller, pid);

        mockMvc.perform(authPost(buyer.token(), "/api/payments",
                        Map.of("orderId", orderId, "method", "WECHAT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentNo", startsWith("PAY-")))
                .andExpect(jsonPath("$.data.status").value("WAIT_PAY"));
    }

    @Test
    @DisplayName("备选 · 查询不存在的支付单返回 CLOSED")
    void shouldReturnClosedForUnknownPaymentNo() throws Exception {
        TestUser user = createTestUser();
        mockMvc.perform(authGet(user.token(), "/api/payments/PAY-NOT-EXIST-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("CLOSED"));
    }

    // ==================== 异常流程 ====================

    @Test
    @DisplayName("异常 · 未登录创建支付单返回 401")
    void shouldRejectCreatePaymentWithoutAuth() throws Exception {
        mockMvc.perform(postJson("/api/payments", Map.of("orderId", 1, "method", "ALIPAY")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("异常 · 订单不存在时创建支付单返回 404")
    void shouldRejectPaymentForNonExistentOrder() throws Exception {
        TestUser buyer = createTestUser();
        mockMvc.perform(authPost(buyer.token(), "/api/payments",
                        Map.of("orderId", 99999999, "method", "ALIPAY")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("异常 · 非买家创建他人订单支付单返回 403")
    void shouldRejectPaymentByNonBuyer() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        TestUser other = createTestUser();
        long pid = createProduct(seller.userId(), "他人订单", 5000, 1);
        long orderId = createOrder(buyer, seller, pid);

        mockMvc.perform(authPost(other.token(), "/api/payments",
                        Map.of("orderId", orderId, "method", "ALIPAY")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("异常 · 未登录查询支付单返回 401")
    void shouldRejectQueryPaymentWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/payments/PAY-ABCDEF123"))
                .andExpect(status().isUnauthorized());
    }
}
