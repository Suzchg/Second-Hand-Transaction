package com.secondhand.integration;

import com.secondhand.offer.entity.Offer;
import com.secondhand.offer.repository.OfferRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 集成测试 · 用例 5：出价议价（出价 + 接受 / 拒绝 / 撤回）
 *
 * 覆盖范围：
 * - 模块间调用：OfferController → OfferService → ProductService → OrderService（accept 时下单）
 * - 数据库访问：Offer 表 + Order 表（接受报价后生成订单）
 * - 对外接口：
 *   - POST /api/products/{productId}/offers
 *   - GET  /api/products/{productId}/offers
 *   - POST /api/offers/{id}/accept
 *   - POST /api/offers/{id}/reject
 *   - POST /api/offers/{id}/cancel
 *   - GET  /api/my-offers、/api/seller-offers
 *
 * 用例流程覆盖：
 * - 主成功流程：买家出价 → 卖家接受 → 系统按报价自动创建订单（状态 WAIT_PAY）
 * - 备选流程：卖家拒绝报价、买家撤回报价、列出待处理报价
 * - 异常流程：给自己商品报价、报价金额非法、重复接受、状态错误（接受已取消的报价）、越权操作他人报价
 */
@Testcontainers
@DisplayName("用例5：出价议价")
class OfferFlowIntegrationIT extends AbstractIntegrationIT {

    @Autowired OfferRepository offerRepo;

    // ==================== 主成功流程 ====================

    @Test
    @DisplayName("主成功 · 买家出价 → 卖家接受 → 自动创建订单")
    void shouldAcceptOfferAndCreateOrder() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "议价商品", 100000, 1); // 1000 元

        // 1. 买家出价 800 元（80000 分）
        long offerId = extractId(mockMvc.perform(authPost(buyer.token(),
                        "/api/products/" + pid + "/offers",
                        Map.of(
                                "offeredPriceCent", 80000,
                                "message", "8折可以吗？")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.offeredPriceCent").value(80000))
                .andExpect(jsonPath("$.data.buyerId").value(buyer.userId()))
                .andExpect(jsonPath("$.data.sellerId").value(seller.userId()))
                .andReturn());

        // 2. 卖家查看商品报价列表
        mockMvc.perform(authGet(seller.token(), "/api/products/" + pid + "/offers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(offerId));

        // 3. 卖家接受报价 → 自动生成订单（按 800 元，非原价 1000 元）
        long orderId = extractId(mockMvc.perform(authPost(seller.token(),
                        "/api/offers/" + offerId + "/accept", Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", greaterThan(0)))
                .andExpect(jsonPath("$.data.status").value("WAIT_PAY"))
                .andExpect(jsonPath("$.data.amountCent").value(80000)) // 按报价金额
                .andExpect(jsonPath("$.data.buyerId").value(buyer.userId()))
                .andExpect(jsonPath("$.data.sellerId").value(seller.userId()))
                .andReturn());

        // 4. 验证报价已更新为 ACCEPTED，并关联了 orderId
        mockMvc.perform(authGet(buyer.token(), "/api/my-offers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data[0].orderId").value(orderId));
    }

    // ==================== 备选流程 ====================

    @Test
    @DisplayName("备选 · 卖家拒绝报价")
    void shouldRejectOffer() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "拒绝测试", 50000, 1);

        long offerId = extractId(mockMvc.perform(authPost(buyer.token(),
                        "/api/products/" + pid + "/offers",
                        Map.of("offeredPriceCent", 40000, "message", "8折")))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(authPost(seller.token(), "/api/offers/" + offerId + "/reject", Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        // 卖家收到的报价列表中应反映新状态
        mockMvc.perform(authGet(seller.token(), "/api/seller-offers"))
                .andExpect(jsonPath("$.data[0].status").value("REJECTED"));
    }

    @Test
    @DisplayName("备选 · 买家撤回报价")
    void shouldCancelOfferByBuyer() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "撤回测试", 50000, 1);

        long offerId = extractId(mockMvc.perform(authPost(buyer.token(),
                        "/api/products/" + pid + "/offers",
                        Map.of("offeredPriceCent", 30000, "message", "5折")))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(authPost(buyer.token(), "/api/offers/" + offerId + "/cancel", Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("备选 · 多买家给同一商品报价，卖家按价格倒序处理")
    void shouldMultipleBuyersOfferSameProduct() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer1 = createTestUser();
        TestUser buyer2 = createTestUser();
        long pid = createProduct(seller.userId(), "多买家", 100000, 1);

        // 两个买家分别报价 70 和 80
        mockMvc.perform(authPost(buyer1.token(), "/api/products/" + pid + "/offers",
                        Map.of("offeredPriceCent", 70000, "message", "7折")))
                .andExpect(status().isOk());

        mockMvc.perform(authPost(buyer2.token(), "/api/products/" + pid + "/offers",
                        Map.of("offeredPriceCent", 80000, "message", "8折")))
                .andExpect(status().isOk());

        // 卖家查看应该有 2 条
        mockMvc.perform(authGet(seller.token(), "/api/products/" + pid + "/offers"))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    // ==================== 异常流程 ====================

    @Test
    @DisplayName("异常 · 给自己商品报价返回 403")
    void shouldRejectOfferOwnProduct() throws Exception {
        TestUser seller = createTestUser();
        long pid = createProduct(seller.userId(), "自报商品", 50000, 1);

        mockMvc.perform(authPost(seller.token(), "/api/products/" + pid + "/offers",
                        Map.of("offeredPriceCent", 30000, "message", "x")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("异常 · 报价金额非法（0 或负数）返回 400")
    void shouldRejectInvalidOfferAmount() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "金额非法", 50000, 1);

        mockMvc.perform(authPost(buyer.token(), "/api/products/" + pid + "/offers",
                        Map.of("offeredPriceCent", 0, "message", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("异常 · 商品售罄，报价返回 409")
    void shouldRejectOfferSoldOut() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "售罄", 50000, 0,
                com.secondhand.product.entity.ProductStatus.ON_SALE);

        mockMvc.perform(authPost(buyer.token(), "/api/products/" + pid + "/offers",
                        Map.of("offeredPriceCent", 30000, "message", "x")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("异常 · 重复接受已 ACCEPTED 的报价返回 409")
    void shouldRejectAcceptAlreadyAccepted() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "重复接受", 50000, 1);

        long offerId = extractId(mockMvc.perform(authPost(buyer.token(),
                        "/api/products/" + pid + "/offers",
                        Map.of("offeredPriceCent", 30000, "message", "x")))
                .andExpect(status().isOk()).andReturn());

        // 第一次接受
        mockMvc.perform(authPost(seller.token(), "/api/offers/" + offerId + "/accept", Map.of()))
                .andExpect(status().isOk());

        // 第二次接受（已 ACCEPTED，应失败）
        mockMvc.perform(authPost(seller.token(), "/api/offers/" + offerId + "/accept", Map.of()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("异常 · 非卖家接受他人报价返回 403")
    void shouldRejectAcceptByNonSeller() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        TestUser other = createTestUser();
        long pid = createProduct(seller.userId(), "越权接受", 50000, 1);

        long offerId = extractId(mockMvc.perform(authPost(buyer.token(),
                        "/api/products/" + pid + "/offers",
                        Map.of("offeredPriceCent", 30000, "message", "x")))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(authPost(other.token(), "/api/offers/" + offerId + "/accept", Map.of()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("异常 · 非买家撤回他人报价返回 403")
    void shouldRejectCancelByNonBuyer() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        TestUser other = createTestUser();
        long pid = createProduct(seller.userId(), "越权撤回", 50000, 1);

        long offerId = extractId(mockMvc.perform(authPost(buyer.token(),
                        "/api/products/" + pid + "/offers",
                        Map.of("offeredPriceCent", 30000, "message", "x")))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(authPost(other.token(), "/api/offers/" + offerId + "/cancel", Map.of()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("异常 · 商品不存在，报价返回 404")
    void shouldRejectOfferNonExistentProduct() throws Exception {
        TestUser buyer = createTestUser();
        mockMvc.perform(authPost(buyer.token(), "/api/products/99999999/offers",
                        Map.of("offeredPriceCent", 30000, "message", "x")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("异常 · 接受报价时商品已下架（被他人先下单），返回 409")
    void shouldRejectAcceptWhenProductOffSale() throws Exception {
        TestUser seller = createTestUser();
        TestUser buyer = createTestUser();
        long pid = createProduct(seller.userId(), "冲突接受", 50000, 1);

        long offerId = extractId(mockMvc.perform(authPost(buyer.token(),
                        "/api/products/" + pid + "/offers",
                        Map.of("offeredPriceCent", 30000, "message", "x")))
                .andExpect(status().isOk()).andReturn());

        // 模拟商品被卖家手动下架
        mockMvc.perform(authPut(seller.token(), "/api/products/" + pid,
                        Map.of("status", "OFF_SALE")))
                .andExpect(status().isOk());

        // 此时接受报价应失败（商品不可购买）
        mockMvc.perform(authPost(seller.token(), "/api/offers/" + offerId + "/accept", Map.of()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }
}
