package com.secondhand.it;

import com.secondhand.it.support.AbstractIntegrationTest;
import com.secondhand.it.support.TestUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用例 5：出价议价（出价 + 接受/拒绝）
 *
 * 覆盖流程：
 * - 主成功：买家出价PENDING→卖家接受→按议价价生成订单（库存扣减、报价单关联订单）→补收货信息→支付完成成交
 * - 备选  ：卖家拒绝报价→REJECTED；买家撤回报价→CANCELLED；卖家报价列表可见
 * - 异常  ：给自己商品报价（403）、无效报价金额（400）、售罄商品报价（409）、
 *           非卖家接受报价（403）、重复接受已接受报价（409）、撤回非待处理报价（409）
 *
 * 验证层次：OfferController → OfferService（调用 ProductService + OrderService）跨模块下单
 */
@DisplayName("用例5：出价议价")
class Uc5OfferIT extends AbstractIntegrationTest {

    @Nested
    @DisplayName("主成功流程")
    class MainFlow {

        @Test
        @DisplayName("议价成交：出价→接受→按报价生成订单→补收货信息→支付")
        void offerAcceptedGeneratesOrderWithOfferPrice() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "议价主流程商品", 20000);

            // 1. 买家出价
            long offerId = data(doPost("/api/products/%d/offers".formatted(productId),
                    buyer.token(), """
                    {"offeredPriceCent":15000,"message":"150元收，可以吗"}
                    """))
                    .path("id").asLong();

            // 报价落库且为待处理状态
            var offer = offerRepo.findById(offerId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals("PENDING", offer.getStatus().name());
            org.junit.jupiter.api.Assertions.assertEquals(15000, offer.getOfferedPriceCent());

            // 2. 卖家接受 → 按报价生成订单
            doPost("/api/offers/%d/accept".formatted(offerId), seller.token(), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("WAIT_PAY"))
                    .andExpect(jsonPath("$.data.amountCent").value(15000));

            long orderId = offerRepo.findById(offerId).orElseThrow().getOrderId();
            org.junit.jupiter.api.Assertions.assertNotNull(orderId, "报价应关联生成的订单");

            // 数据库断言：订单按议价价生成，库存扣减
            var order = orderRepo.findById(orderId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(15000, order.getAmountCent());
            org.junit.jupiter.api.Assertions.assertEquals(buyer.userId(), order.getBuyerId());
            org.junit.jupiter.api.Assertions.assertEquals("ACCEPTED",
                    offerRepo.findById(offerId).orElseThrow().getStatus().name());
            org.junit.jupiter.api.Assertions.assertEquals(0,
                    productRepo.findById(productId).orElseThrow().getQuantity());

            // 3. 议价订单补填收货信息后支付 → 成交
            doPut("/api/orders/%d/receiver".formatted(orderId), buyer.token(), """
                    {"receiverName":"议价收货人","receiverPhone":"13800008888",
                     "receiverAddress":"武汉市洪山区1号"}
                    """).andExpect(status().isOk());
            doPost("/api/orders/%d/pay".formatted(orderId), buyer.token(), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("WAIT_DELIVER"));
        }
    }

    @Nested
    @DisplayName("备选流程")
    class AlternateFlow {

        @Test
        @DisplayName("卖家拒绝报价：报价状态REJECTED，商品保持可售")
        void sellerRejectsOffer() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "拒绝报价商品", 10000);

            long offerId = data(doPost("/api/products/%d/offers".formatted(productId),
                    buyer.token(), """
                    {"offeredPriceCent":5000,"message":"五折收"}
                    """)).path("id").asLong();

            doPost("/api/offers/%d/reject".formatted(offerId), seller.token(), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REJECTED"));

            // 未生成订单，库存不变
            org.junit.jupiter.api.Assertions.assertNull(
                    offerRepo.findById(offerId).orElseThrow().getOrderId());
            org.junit.jupiter.api.Assertions.assertEquals(1,
                    productRepo.findById(productId).orElseThrow().getQuantity());
        }

        @Test
        @DisplayName("买家撤回报价：报价状态CANCELLED")
        void buyerCancelsPendingOffer() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "撤回报价商品", 9000);

            long offerId = data(doPost("/api/products/%d/offers".formatted(productId),
                    buyer.token(), """
                    {"offeredPriceCent":8000,"message":"考虑一下"}
                    """)).path("id").asLong();

            doPost("/api/offers/%d/cancel".formatted(offerId), buyer.token(), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"));

            org.junit.jupiter.api.Assertions.assertEquals("CANCELLED",
                    offerRepo.findById(offerId).orElseThrow().getStatus().name());
        }

        @Test
        @DisplayName("卖家报价列表：展示商品收到的待处理报价（需登录）")
        void sellerSeesOffersForProduct() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "报价列表商品", 11000);

            doPost("/api/products/%d/offers".formatted(productId), buyer.token(), """
                    {"offeredPriceCent":9500,"message":"95元可以吗"}
                    """).andExpect(status().isOk());

            doGet("/api/products/%d/offers".formatted(productId), seller.token())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                    .andExpect(jsonPath("$.data[0].offeredPriceCent").value(9500));
        }
    }

    @Nested
    @DisplayName("异常流程")
    class ExceptionFlow {

        @Test
        @DisplayName("给自己商品报价：返回403 FORBIDDEN")
        void offerOwnProductForbidden() throws Exception {
            TestUser seller = registerUser();
            long productId = createProduct(seller.token(), "自报价商品", 8000);

            doPost("/api/products/%d/offers".formatted(productId), seller.token(), """
                    {"offeredPriceCent":100,"message":"自买自卖"}
                    """)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("无效报价金额：0/负数/缺失返回400")
        void invalidOfferPriceRejected() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "无效报价商品", 8000);

            doPost("/api/products/%d/offers".formatted(productId), buyer.token(), """
                    {"offeredPriceCent":0,"message":"零元购"}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

            doPost("/api/products/%d/offers".formatted(productId), buyer.token(), """
                    {"offeredPriceCent":-100,"message":"负价"}
                    """)
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("售罄商品报价：返回409 CONFLICT")
        void offerSoldOutProductConflict() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer1 = registerUser();
            TestUser buyer2 = registerUser();
            long productId = createProduct(seller.token(), "售罄报价商品", 7000);
            placeOrder(buyer1.token(), productId); // 买走唯一库存

            doPost("/api/products/%d/offers".formatted(productId), buyer2.token(), """
                    {"offeredPriceCent":6000,"message":"还有吗"}
                    """)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        }

        @Test
        @DisplayName("非卖家接受报价：返回403 FORBIDDEN")
        void acceptByNonSellerForbidden() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            TestUser stranger = registerUser();
            long productId = createProduct(seller.token(), "他人报价商品", 6000);

            long offerId = data(doPost("/api/products/%d/offers".formatted(productId),
                    buyer.token(), """
                    {"offeredPriceCent":5000,"message":"求带走"}
                    """)).path("id").asLong();

            doPost("/api/offers/%d/accept".formatted(offerId), stranger.token(), null)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

            // 报价状态未被篡改
            org.junit.jupiter.api.Assertions.assertEquals("PENDING",
                    offerRepo.findById(offerId).orElseThrow().getStatus().name());
        }

        @Test
        @DisplayName("重复接受已接受的报价：返回409 CONFLICT")
        void acceptTwiceConflict() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "重复接受商品", 6000);

            long offerId = data(doPost("/api/products/%d/offers".formatted(productId),
                    buyer.token(), """
                    {"offeredPriceCent":5500,"message":"冲"}
                    """)).path("id").asLong();

            doPost("/api/offers/%d/accept".formatted(offerId), seller.token(), null)
                    .andExpect(status().isOk());

            doPost("/api/offers/%d/accept".formatted(offerId), seller.token(), null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        }

        @Test
        @DisplayName("撤回非待处理报价：返回409 CONFLICT")
        void cancelNonPendingOfferConflict() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "撤回已拒商品", 6000);

            long offerId = data(doPost("/api/products/%d/offers".formatted(productId),
                    buyer.token(), """
                    {"offeredPriceCent":5000,"message":"再议"}
                    """)).path("id").asLong();

            // 卖家先拒绝 → 报价变为 REJECTED
            doPost("/api/offers/%d/reject".formatted(offerId), seller.token(), null)
                    .andExpect(status().isOk());

            // 买家再撤回 → 409
            doPost("/api/offers/%d/cancel".formatted(offerId), buyer.token(), null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        }
    }
}
