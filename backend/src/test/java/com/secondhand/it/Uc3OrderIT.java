package com.secondhand.it;

import com.secondhand.it.support.AbstractIntegrationTest;
import com.secondhand.it.support.TestUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用例 3：商品购买（下单 + 支付）
 *
 * 覆盖流程：
 * - 主成功：买家下单→WAIT_PAY 落库并扣减库存；支付→WAIT_DELIVER 且订单事件链完整
 * - 备选  ：未支付订单取消→库存恢复并自动重新上架
 * - 异常  ：购买自己的商品（403）、售罄/下架商品下单（409）、商品不存在（404）、
 *           重复支付（409）、非买家支付（404）、已支付订单取消（409）、
 *           收货信息缺失时支付（400）
 *
 * 验证层次：OrderController → OrderService（调用 ProductService）→ 双 Repository 落库
 */
@DisplayName("用例3：商品购买（下单+支付）")
class Uc3OrderIT extends AbstractIntegrationTest {

    @Nested
    @DisplayName("主成功流程")
    class MainFlow {

        @Test
        @DisplayName("下单：订单WAIT_PAY落库、库存扣减、订单事件记录")
        void createOrderPersistsAndDecrementsStock() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "下单主流程商品", 12800);

            doPost("/api/orders", buyer.token(), """
                    {"productId":%d,"receiverName":"张三","receiverPhone":"13800002222",
                     "receiverAddress":"上海市杨浦区五角场100号"}
                    """.formatted(productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("WAIT_PAY"))
                    .andExpect(jsonPath("$.data.amountCent").value(12800));

            // 数据库层断言
            var order = orderRepo.findAll().stream()
                    .filter(o -> o.getProductId() == productId)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("订单未持久化到数据库"));
            org.junit.jupiter.api.Assertions.assertEquals(buyer.userId(), order.getBuyerId());
            org.junit.jupiter.api.Assertions.assertEquals(seller.userId(), order.getSellerId());
            org.junit.jupiter.api.Assertions.assertEquals("WAIT_PAY", order.getStatus().name());

            // 库存扣减（1 → 0），售罄自动下架
            var product = productRepo.findById(productId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(0, product.getQuantity());
            org.junit.jupiter.api.Assertions.assertEquals("OFF_SALE", product.getStatus().name());

            // 订单事件已记录（订单创建事件）
            var events = orderEventRepo.findByOrderIdOrderByIdAsc(order.getId());
            org.junit.jupiter.api.Assertions.assertFalse(events.isEmpty(),
                    "下单后应产生订单事件");
            org.junit.jupiter.api.Assertions.assertEquals("WAIT_PAY",
                    events.get(events.size() - 1).getToStatus());
        }

        @Test
        @DisplayName("支付：订单进入WAIT_DELIVER、支付时间与事件落库")
        void payOrderTransitionsToWaitDeliver() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "支付主流程商品", 9900);
            long orderId = placeOrder(buyer.token(), productId);

            doPost("/api/orders/%d/pay".formatted(orderId), buyer.token(), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("WAIT_DELIVER"));

            var order = orderRepo.findById(orderId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertNotNull(order.getPaidAt(), "支付时间应落库");
            org.junit.jupiter.api.Assertions.assertEquals("WAIT_DELIVER", order.getStatus().name());

            // 事件链：创建 → 支付
            var events = orderEventRepo.findByOrderIdOrderByIdAsc(orderId);
            org.junit.jupiter.api.Assertions.assertEquals(2, events.size());
            org.junit.jupiter.api.Assertions.assertEquals("WAIT_DELIVER",
                    events.get(1).getToStatus());
        }
    }

    @Nested
    @DisplayName("备选流程")
    class AlternateFlow {

        @Test
        @DisplayName("取消订单：未支付订单取消后库存恢复并自动重新上架")
        void cancelOrderRestoresStockAndRelists() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "取消流程商品", 7200);
            long orderId = placeOrder(buyer.token(), productId);

            // 下单后库存归零、自动下架
            org.junit.jupiter.api.Assertions.assertEquals("OFF_SALE",
                    productRepo.findById(productId).orElseThrow().getStatus().name());

            doPost("/api/orders/%d/cancel".formatted(orderId), buyer.token(), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"));

            // 数据库断言：订单已取消，库存恢复，商品重新上架
            org.junit.jupiter.api.Assertions.assertEquals("CANCELLED",
                    orderRepo.findById(orderId).orElseThrow().getStatus().name());
            var product = productRepo.findById(productId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(1, product.getQuantity());
            org.junit.jupiter.api.Assertions.assertEquals("ON_SALE", product.getStatus().name());

            // 取消事件已追加
            var events = orderEventRepo.findByOrderIdOrderByIdAsc(orderId);
            org.junit.jupiter.api.Assertions.assertEquals("CANCELLED",
                    events.get(events.size() - 1).getToStatus());
        }

        @Test
        @DisplayName("订单详情：买卖双方均可查看（权限内聚）")
        void orderDetailVisibleToBothParties() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "详情查询商品", 5500);
            long orderId = placeOrder(buyer.token(), productId);

            doGet("/api/orders/" + orderId, buyer.token())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.order.id").value(orderId))
                    .andExpect(jsonPath("$.data.canPay").value(true))
                    .andExpect(jsonPath("$.data.canShip").value(false));

            doGet("/api/orders/" + orderId, seller.token())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.canPay").value(false));
        }
    }

    @Nested
    @DisplayName("异常流程")
    class ExceptionFlow {

        @Test
        @DisplayName("购买自己的商品：返回403 FORBIDDEN")
        void buyOwnProductForbidden() throws Exception {
            TestUser seller = registerUser();
            long productId = createProduct(seller.token(), "自有商品", 3000);

            doPost("/api/orders", seller.token(), """
                    {"productId":%d,"receiverName":"李四","receiverPhone":"13800003333",
                     "receiverAddress":"广州市天河区1号"}
                    """.formatted(productId))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

            // 未产生订单，库存未扣减
            org.junit.jupiter.api.Assertions.assertTrue(
                    orderRepo.findAll().stream().noneMatch(o -> o.getProductId() == productId));
            org.junit.jupiter.api.Assertions.assertEquals(1,
                    productRepo.findById(productId).orElseThrow().getQuantity());
        }

        @Test
        @DisplayName("售罄商品下单：返回409 CONFLICT 且不产生订单")
        void buySoldOutProductConflict() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer1 = registerUser();
            TestUser buyer2 = registerUser();
            long productId = createProduct(seller.token(), "库存唯一商品", 2000);

            // buyer1 买走唯一库存
            placeOrder(buyer1.token(), productId);

            // buyer2 再买 → 售罄
            doPost("/api/orders", buyer2.token(), """
                    {"productId":%d,"receiverName":"王五","receiverPhone":"13800004444",
                     "receiverAddress":"深圳市南山区1号"}
                    """.formatted(productId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));

            org.junit.jupiter.api.Assertions.assertTrue(
                    orderRepo.findAll().stream()
                            .filter(o -> o.getProductId() == productId)
                            .count() == 1, "售罄后不应再产生新订单");
        }

        @Test
        @DisplayName("已下架商品下单：返回409 CONFLICT")
        void buyOffSaleProductConflict() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "已下架商品", 4000);

            // 卖家主动下架
            doPut("/api/products/" + productId, seller.token(), """
                    {"status":"OFF_SALE"}
                    """).andExpect(status().isOk());

            doPost("/api/orders", buyer.token(), """
                    {"productId":%d,"receiverName":"赵六","receiverPhone":"13800005555",
                     "receiverAddress":"杭州市西湖区1号"}
                    """.formatted(productId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        }

        @Test
        @DisplayName("商品不存在下单：返回404 NOT_FOUND")
        void buyNonexistentProductReturns404() throws Exception {
            TestUser buyer = registerUser();
            doPost("/api/orders", buyer.token(), """
                    {"productId":999999999,"receiverName":"钱七","receiverPhone":"13800006666",
                     "receiverAddress":"成都市高新区1号"}
                    """)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("重复支付：返回409 CONFLICT")
        void payTwiceConflict() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "重复支付商品", 6000);
            long orderId = placeOrder(buyer.token(), productId);
            payOrder(buyer.token(), orderId);

            doPost("/api/orders/%d/pay".formatted(orderId), buyer.token(), null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        }

        @Test
        @DisplayName("非买家支付他人订单：返回404（订单对该用户不可见）")
        void payOthersOrderReturns404() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            TestUser stranger = registerUser();
            long productId = createProduct(seller.token(), "他人订单商品", 8000);
            long orderId = placeOrder(buyer.token(), productId);

            doPost("/api/orders/%d/pay".formatted(orderId), stranger.token(), null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

            // 原订单状态未被篡改
            org.junit.jupiter.api.Assertions.assertEquals("WAIT_PAY",
                    orderRepo.findById(orderId).orElseThrow().getStatus().name());
        }

        @Test
        @DisplayName("已支付订单取消：返回409（仅待支付可取消）")
        void cancelPaidOrderConflict() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "已支付取消商品", 9000);
            long orderId = placeOrder(buyer.token(), productId);
            payOrder(buyer.token(), orderId);

            doPost("/api/orders/%d/cancel".formatted(orderId), buyer.token(), null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));

            org.junit.jupiter.api.Assertions.assertEquals("WAIT_DELIVER",
                    orderRepo.findById(orderId).orElseThrow().getStatus().name());
        }

        @Test
        @DisplayName("收货信息缺失支付：返回400（议价订单需先补填收货信息）")
        void payWithoutReceiverInfoBad() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "议价订单商品", 12000);

            // 买家报价 → 卖家接受 → 生成无收货信息的订单（议价链路）
            long offerId = data(doPost("/api/products/%d/offers".formatted(productId),
                    buyer.token(), """
                    {"offeredPriceCent":10000,"message":"便宜点"}
                    """)).path("id").asLong();
            long orderId = data(doPost("/api/offers/%d/accept".formatted(offerId),
                    seller.token(), null)).path("id").asLong();

            // 未补收货信息直接支付 → 400
            doPost("/api/orders/%d/pay".formatted(orderId), buyer.token(), null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));

            // 补填收货信息后支付成功（备选链路闭环）
            doPut("/api/orders/%d/receiver".formatted(orderId), buyer.token(), """
                    {"receiverName":"补填收货人","receiverPhone":"13800007777",
                     "receiverAddress":"南京市玄武区1号"}
                    """)
                    .andExpect(status().isOk());
            doPost("/api/orders/%d/pay".formatted(orderId), buyer.token(), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("WAIT_DELIVER"));
        }
    }
}
