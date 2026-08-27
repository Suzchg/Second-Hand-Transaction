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
 * 集成测试 · 用例 3+4：商品购买（下单+支付） + 卖家发货
 *
 * 覆盖范围：
 * - 模块间调用：OrderController → OrderService → ProductService → ProductRepository
 *              → OrderRepository → OrderEventRepository → ShipmentRepository
 *              → LogisticsService → LogisticsProvider（Mock）
 * - 数据库访问：订单、商品（库存扣减/恢复）、Shipment、OrderEvent 四张表
 * - 对外接口：
 *   - POST /api/orders （下单）
 *   - POST /api/orders/{id}/pay （支付）
 *   - POST /api/orders/{id}/ship （发货）
 *   - POST /api/orders/{id}/confirm （确认收货）
 *   - POST /api/orders/{id}/cancel （取消）
 *   - GET  /api/orders/{id} （详情）
 *   - GET  /api/orders/sold、/api/orders/bought
 *   - GET  /api/shipments/{orderId}/track （物流轨迹，公开）
 *   - POST /api/orders/process-settlements （结算）
 *
 * 状态机：
 *   WAIT_PAY → WAIT_DELIVER → WAIT_RECEIVE → COMPLETED → SETTLED
 *   WAIT_PAY → CANCELLED（库存恢复）
 *
 * 用例流程覆盖：
 * - 主成功流程：下单→支付→发货→确认收货→（结算）
 * - 备选流程：下单后取消（库存恢复）、物流轨迹查询
 * - 异常流程：买自己商品、商品已下架、重复支付、未支付就发货、确认收货状态错误、未填收货信息就支付
 */
@Testcontainers
@DisplayName("用例3+4：商品购买与卖家发货")
class OrderFlowIntegrationIT extends AbstractIntegrationIT {

    @Autowired OrderRepository orderRepo;

    private static final String RECEIVER_NAME = "李收件人";
    private static final String RECEIVER_PHONE = "13900008888";
    private static final String RECEIVER_ADDR = "北京市海淀区中关村大街1号";

    // ==================== 主成功流程 ====================

    @Test
    @DisplayName("主成功 · 下单→支付→发货→确认收货 全链路状态机推进")
    void shouldCompleteHappyPath() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long productId = createProduct(seller.userId(), "MacBook Pro 14 M3", 1299900, 1);

        // 1. 下单
        long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                        Map.of(
                                "productId", productId,
                                "receiverName", RECEIVER_NAME,
                                "receiverPhone", RECEIVER_PHONE,
                                "receiverAddress", RECEIVER_ADDR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAIT_PAY"))
                .andExpect(jsonPath("$.data.amountCent").value(1299900))
                .andExpect(jsonPath("$.data.buyerId").value(buyer.userId()))
                .andExpect(jsonPath("$.data.sellerId").value(seller.userId()))
                .andReturn());

        // 2. 支付
        mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/pay",
                        Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAIT_DELIVER"))
                .andExpect(jsonPath("$.data.paidAt", notNullValue()));

        // 3. 卖家发货
        mockMvc.perform(authPost(seller.token(), "/api/orders/" + orderId + "/ship",
                        Map.of(
                                "carrierCode", "SF",
                                "trackingNo", "SF1234567890")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.carrierCode").value("SF"))
                .andExpect(jsonPath("$.data.trackingNo").value("SF1234567890"))
                .andExpect(jsonPath("$.data.status").value("CREATED"));

        // 验证订单已进入 WAIT_RECEIVE
        mockMvc.perform(authGet(buyer.token(), "/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.status").value("WAIT_RECEIVE"))
                .andExpect(jsonPath("$.data.canConfirm").value(true))
                .andExpect(jsonPath("$.data.fundsInEscrow").value(true));

        // 4. 买家确认收货
        mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/confirm", Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt", notNullValue()));

        // 5. 手动触发结算（绕开 7 天等待）
        mockMvc.perform(authPost(buyer.token(), "/api/orders/process-settlements", Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", greaterThanOrEqualTo(0)));
    }

    // ==================== 备选流程 ====================

    @Test
    @DisplayName("备选 · 下单后取消订单，库存恢复")
    void shouldCancelAndRestoreStock() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long productId = createProduct(seller.userId(), "可取消订单商品", 50000, 1);

        // 下单（库存减1，从1→0，自动 OFF_SALE）
        long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                        Map.of(
                                "productId", productId,
                                "receiverName", RECEIVER_NAME,
                                "receiverPhone", RECEIVER_PHONE,
                                "receiverAddress", RECEIVER_ADDR)))
                .andExpect(status().isOk())
                .andReturn());

        // 验证库存已被扣减（商品状态变 OFF_SALE）
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(jsonPath("$.data.status").value("OFF_SALE"))
                .andExpect(jsonPath("$.data.quantity").value(0));

        // 取消订单
        mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/cancel", Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // 库存恢复（从0→1），状态恢复为 ON_SALE
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.quantity").value(1));
    }

    @Test
    @DisplayName("备选 · 公开物流轨迹查询（Mock Provider 返回四段轨迹）")
    void shouldTrackShipmentPublicly() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long productId = createProduct(seller.userId(), "物流测试商品", 10000, 1);

        // 走完下单+支付+发货
        long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                        Map.of(
                                "productId", productId,
                                "receiverName", RECEIVER_NAME,
                                "receiverPhone", RECEIVER_PHONE,
                                "receiverAddress", RECEIVER_ADDR)))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/pay", Map.of()))
                .andExpect(status().isOk());

        mockMvc.perform(authPost(seller.token(), "/api/orders/" + orderId + "/ship",
                        Map.of("carrierCode", "YT", "trackingNo", "YT" + System.nanoTime())))
                .andExpect(status().isOk());

        // 公开查询物流轨迹（无需鉴权）
        mockMvc.perform(get("/api/shipments/" + orderId + "/track"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.carrierCode").value("YT"))
                .andExpect(jsonPath("$.data.trackingNo", startsWith("YT")))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                .andExpect(jsonPath("$.data.timeline", hasSize(4)));
    }

    @Test
    @DisplayName("备选 · 卖家查看卖出订单 / 买家查看购入订单")
    void shouldListSoldAndBought() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "卖出商品", 8000, 1);

        long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                        Map.of(
                                "productId", pid,
                                "receiverName", "x",
                                "receiverPhone", "x",
                                "receiverAddress", "x")))
                .andExpect(status().isOk()).andReturn());

        // 买家看到 1 条 bought 订单
        mockMvc.perform(authGet(buyer.token(), "/api/orders/bought"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(orderId));

        // 卖家看到 1 条 sold 订单
        mockMvc.perform(authGet(seller.token(), "/api/orders/sold"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)));

        // 按状态过滤：WAIT_PAY 应该返回 1 条
        mockMvc.perform(authGet(buyer.token(), "/api/orders/bought").param("status", "WAIT_PAY"))
                .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    @DisplayName("备选 · 买家更新订单收货信息")
    void shouldUpdateReceiverInfo() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "改地址商品", 5000, 1);

        long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                        Map.of(
                                "productId", pid,
                                "receiverName", "原名",
                                "receiverPhone", "13900000000",
                                "receiverAddress", "原地址")))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(authPut(buyer.token(), "/api/orders/" + orderId + "/receiver",
                        Map.of(
                                "receiverName", "新名",
                                "receiverPhone", "13900001111",
                                "receiverAddress", "新地址")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiverName").value("新名"))
                .andExpect(jsonPath("$.data.receiverPhone").value("13900001111"))
                .andExpect(jsonPath("$.data.receiverAddress").value("新地址"));
    }

    // ==================== 异常流程 ====================

    @Test
    @DisplayName("异常 · 买家不能购买自己的商品返回 403")
    void shouldRejectBuyOwnProduct() throws Exception {
        TestUser seller = createTestUser();
        long pid = createProduct(seller.userId(), "自购商品", 5000, 1);

        mockMvc.perform(authPost(seller.token(), "/api/orders",
                        Map.of(
                                "productId", pid,
                                "receiverName", "x",
                                "receiverPhone", "x",
                                "receiverAddress", "x")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("异常 · 商品已下架，下单返回 409")
    void shouldRejectOrderForOffSaleProduct() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "下架商品", 5000, 1,
                com.secondhand.product.entity.ProductStatus.OFF_SALE);

        mockMvc.perform(authPost(buyer.token(), "/api/orders",
                        Map.of(
                                "productId", pid,
                                "receiverName", "x",
                                "receiverPhone", "x",
                                "receiverAddress", "x")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("异常 · 商品库存为0，下单返回 409")
    void shouldRejectOrderForSoldOutProduct() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "售罄商品", 5000, 0,
                com.secondhand.product.entity.ProductStatus.ON_SALE);

        mockMvc.perform(authPost(buyer.token(), "/api/orders",
                        Map.of(
                                "productId", pid,
                                "receiverName", "x",
                                "receiverPhone", "x",
                                "receiverAddress", "x")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("异常 · 商品不存在，下单返回 404")
    void shouldRejectOrderForNonExistentProduct() throws Exception {
        TestUser buyer = createTestUser();
        mockMvc.perform(authPost(buyer.token(), "/api/orders",
                        Map.of(
                                "productId", 99999999,
                                "receiverName", "x",
                                "receiverPhone", "x",
                                "receiverAddress", "x")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("异常 · 重复支付已支付订单返回 409")
    void shouldRejectDoublePay() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "双付商品", 5000, 1);

        long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                        Map.of(
                                "productId", pid,
                                "receiverName", "x",
                                "receiverPhone", "x",
                                "receiverAddress", "x")))
                .andExpect(status().isOk()).andReturn());

        // 第一次支付成功
        mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/pay", Map.of()))
                .andExpect(status().isOk());

        // 第二次支付应失败
        mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/pay", Map.of()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("异常 · 卖家在未支付状态下发货返回 409")
    void shouldRejectShipBeforePay() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "未付先发", 5000, 1);

        long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                        Map.of(
                                "productId", pid,
                                "receiverName", "x",
                                "receiverPhone", "x",
                                "receiverAddress", "x")))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(authPost(seller.token(), "/api/orders/" + orderId + "/ship",
                        Map.of("carrierCode", "SF", "trackingNo", "x")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("异常 · 已完成订单不能确认收货返回 409")
    void shouldRejectConfirmCompletedOrder() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "完成态", 5000, 1);

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

        // 再次确认收货应失败
        mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/confirm", Map.of()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("异常 · 未支付状态下取消订单外，无法取消已支付订单返回 409")
    void shouldRejectCancelPaidOrder() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "付款取消", 5000, 1);

        long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                        Map.of("productId", pid,
                                "receiverName", "x", "receiverPhone", "x", "receiverAddress", "x")))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/pay", Map.of()))
                .andExpect(status().isOk());

        // 已支付状态下不能取消
        mockMvc.perform(authPost(buyer.token(), "/api/orders/" + orderId + "/cancel", Map.of()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("异常 · 非买家/卖家访问他人订单详情返回 403")
    void shouldRejectAccessOthersOrder() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        TestUser other = createTestUser();
        long pid = createProduct(seller.userId(), "他单", 5000, 1);

        long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                        Map.of("productId", pid,
                                "receiverName", "x", "receiverPhone", "x", "receiverAddress", "x")))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(authGet(other.token(), "/api/orders/" + orderId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("异常 · 物流查询订单不存在返回 404")
    void shouldRejectTrackNonExistentOrder() throws Exception {
        mockMvc.perform(get("/api/shipments/99999999/track"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("异常 · 物流查询订单存在但未发货返回 404")
    void shouldRejectTrackBeforeShip() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "未发先查", 5000, 1);

        long orderId = extractId(mockMvc.perform(authPost(buyer.token(), "/api/orders",
                        Map.of("productId", pid,
                                "receiverName", "x", "receiverPhone", "x", "receiverAddress", "x")))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(get("/api/shipments/" + orderId + "/track"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("异常 · 下单时缺少必填收货信息返回 400")
    void shouldRejectOrderWithMissingReceiver() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "校验测试", 5000, 1);

        // 缺少 receiverAddress
        mockMvc.perform(authPost(buyer.token(), "/api/orders",
                        Map.of(
                                "productId", pid,
                                "receiverName", "x",
                                "receiverPhone", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
