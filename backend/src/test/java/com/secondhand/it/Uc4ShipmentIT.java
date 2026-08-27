package com.secondhand.it;

import com.secondhand.it.support.AbstractIntegrationTest;
import com.secondhand.it.support.TestUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用例 4：卖家发货
 *
 * 覆盖流程：
 * - 主成功：已支付订单发货→生成物流运单（Shipment 落库）→订单WAIT_RECEIVE；
 *           物流轨迹公开查询（LogisticsService → MockLogisticsProvider 策略）
 * - 备选  ：买家确认收货→订单COMPLETED（资金托管），订单详情进入可售后状态
 * - 异常  ：未支付订单发货（409）、买家尝试发货（404）、重复发货（409）、
 *           承运单号缺失（400）、发货前确认收货（409）
 *
 * 验证层次：OrderController → OrderService → Shipment/Order/OrderEvent 三表落库
 */
@DisplayName("用例4：卖家发货")
class Uc4ShipmentIT extends AbstractIntegrationTest {

    @Nested
    @DisplayName("主成功流程")
    class MainFlow {

        @Test
        @DisplayName("发货：生成运单落库，订单进入WAIT_RECEIVE并记录事件")
        void shipCreatesShipmentAndMovesToWaitReceive() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "发货主流程商品", 15800);
            long orderId = placeOrder(buyer.token(), productId);
            payOrder(buyer.token(), orderId);

            doPost("/api/orders/%d/ship".formatted(orderId), seller.token(), """
                    {"carrierCode":"SF","trackingNo":"SF1234567890"}
                    """)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.carrierCode").value("SF"))
                    .andExpect(jsonPath("$.data.trackingNo").value("SF1234567890"));

            // 数据库层断言：运单已生成
            var shipment = shipmentRepo.findByOrderId(orderId)
                    .orElseThrow(() -> new AssertionError("运单未持久化到数据库"));
            org.junit.jupiter.api.Assertions.assertEquals("SF", shipment.getCarrierCode());
            org.junit.jupiter.api.Assertions.assertEquals("SF1234567890", shipment.getTrackingNo());
            org.junit.jupiter.api.Assertions.assertEquals("CREATED", shipment.getStatus().name());

            // 订单状态与事件
            var order = orderRepo.findById(orderId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals("WAIT_RECEIVE", order.getStatus().name());
            org.junit.jupiter.api.Assertions.assertNotNull(order.getShippedAt());
            var events = orderEventRepo.findByOrderIdOrderByIdAsc(orderId);
            org.junit.jupiter.api.Assertions.assertEquals("WAIT_RECEIVE",
                    events.get(events.size() - 1).getToStatus());
        }

        @Test
        @DisplayName("物流轨迹：公开接口返回模拟轨迹（无需登录，Mock策略）")
        void trackShipmentReturnsMockTrace() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "物流轨迹商品", 3000);
            long orderId = placeOrder(buyer.token(), productId);
            payOrder(buyer.token(), orderId);
            shipOrder(seller.token(), orderId);

            doGet("/api/shipments/%d/track".formatted(orderId), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("DELIVERED"))
                    .andExpect(jsonPath("$.data.points").isArray())
                    .andExpect(jsonPath("$.data.points[0].desc").exists())
                    .andExpect(jsonPath("$.data.points.length()").value(4));
        }
    }

    @Nested
    @DisplayName("备选流程")
    class AlternateFlow {

        @Test
        @DisplayName("确认收货：订单COMPLETED、完成时间落库、进入可售后状态")
        void confirmReceiptCompletesOrder() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "确认收货商品", 20000);
            long orderId = placeOrder(buyer.token(), productId);
            payOrder(buyer.token(), orderId);
            shipOrder(seller.token(), orderId);

            doPost("/api/orders/%d/confirm".formatted(orderId), buyer.token(), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"));

            var order = orderRepo.findById(orderId).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", order.getStatus().name());
            org.junit.jupiter.api.Assertions.assertNotNull(order.getCompletedAt());

            // 订单详情：买家在7天售后期内可发起售后，资金处于托管状态
            doGet("/api/orders/" + orderId, buyer.token())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.canApplyAfterSale").value(true))
                    .andExpect(jsonPath("$.data.fundsInEscrow").value(true));
        }
    }

    @Nested
    @DisplayName("异常流程")
    class ExceptionFlow {

        @Test
        @DisplayName("未支付订单发货：返回409 CONFLICT")
        void shipUnpaidOrderConflict() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "未支付发货商品", 4000);
            long orderId = placeOrder(buyer.token(), productId);

            doPost("/api/orders/%d/ship".formatted(orderId), seller.token(), """
                    {"carrierCode":"SF","trackingNo":"SF000"}
                    """)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));

            // 未生成运单
            org.junit.jupiter.api.Assertions.assertTrue(shipmentRepo.findByOrderId(orderId).isEmpty());
        }

        @Test
        @DisplayName("买家尝试发货他人订单：返回404（订单对买家不可见为卖家维度）")
        void shipByBuyerReturns404() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "买家发货商品", 5000);
            long orderId = placeOrder(buyer.token(), productId);
            payOrder(buyer.token(), orderId);

            doPost("/api/orders/%d/ship".formatted(orderId), buyer.token(), """
                    {"carrierCode":"SF","trackingNo":"SF111"}
                    """)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("重复发货：已发货订单再次发货返回409")
        void shipTwiceConflict() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "重复发货商品", 6000);
            long orderId = placeOrder(buyer.token(), productId);
            payOrder(buyer.token(), orderId);
            shipOrder(seller.token(), orderId);

            doPost("/api/orders/%d/ship".formatted(orderId), seller.token(), """
                    {"carrierCode":"YTO","trackingNo":"YT222"}
                    """)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        }

        @Test
        @DisplayName("发货参数缺失：承运商/单号为空返回400")
        void shipWithBlankCarrierRejected() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "参数缺失商品", 7000);
            long orderId = placeOrder(buyer.token(), productId);
            payOrder(buyer.token(), orderId);

            doPost("/api/orders/%d/ship".formatted(orderId), seller.token(), """
                    {"carrierCode":"","trackingNo":""}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("发货前确认收货：返回409 CONFLICT")
        void confirmBeforeShipmentConflict() throws Exception {
            TestUser seller = registerUser();
            TestUser buyer = registerUser();
            long productId = createProduct(seller.token(), "未发货确认商品", 8000);
            long orderId = placeOrder(buyer.token(), productId);
            payOrder(buyer.token(), orderId);

            doPost("/api/orders/%d/confirm".formatted(orderId), buyer.token(), null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("CONFLICT"));
        }
    }
}
