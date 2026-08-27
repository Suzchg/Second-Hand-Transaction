package com.secondhand.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 场景 5：出价议价（出价 + 接受/拒绝/撤销）。
 */
@DisplayName("场景 5：出价议价（出价 + 接受/拒绝/撤销）")
class OfferE2eTest extends E2eTestBase {

    @Test
    @DisplayName("买家出价、卖家接受，按议价生成订单并支付")
    void offerAcceptSuccess() {
        String seller = registerUser();
        String buyer = registerUser();
        long pid = data(post("/api/products", productBody("议价商品", 5000), seller)).get("id").asLong();

        JsonNode offer = data(post("/api/products/" + pid + "/offers",
                Map.of("offeredPriceCent", 3500, "message", "3500卖吗"), buyer));
        assertEquals("PENDING", offer.get("status").asText(), "出价后应为待回复");
        long offerId = offer.get("id").asLong();

        JsonNode order = data(post("/api/offers/" + offerId + "/accept", null, seller));
        assertEquals(3500, order.get("amountCent").asInt(), "订单金额应为议价金额");
        assertEquals("WAIT_PAY", order.get("status").asText());
        long oid = order.get("id").asLong();

        // 接受报价生成的订单无收货信息，买家补填后支付
        put("/api/orders/" + oid + "/receiver",
                Map.of("receiverName", "张三", "receiverPhone", "13900000000", "receiverAddress", "XX省XX市XX区"), buyer);
        JsonNode paid = data(post("/api/orders/" + oid + "/pay", null, buyer));
        assertEquals("WAIT_DELIVER", paid.get("status").asText(), "补填收货信息并支付后应待发货");
    }

    @Test
    @DisplayName("卖家拒绝报价")
    void offerReject() {
        String seller = registerUser();
        String buyer = registerUser();
        long pid = data(post("/api/products", productBody("拒绝议价商品"), seller)).get("id").asLong();
        long offerId = data(post("/api/products/" + pid + "/offers",
                Map.of("offeredPriceCent", 100, "message", "太贵了"), buyer)).get("id").asLong();

        JsonNode rejected = data(post("/api/offers/" + offerId + "/reject", null, seller));
        assertEquals("REJECTED", rejected.get("status").asText(), "拒绝后报价应变为已拒绝");
    }

    @Test
    @DisplayName("买家撤销报价")
    void offerCancel() {
        String seller = registerUser();
        String buyer = registerUser();
        long pid = data(post("/api/products", productBody("撤销报价商品"), seller)).get("id").asLong();
        long offerId = data(post("/api/products/" + pid + "/offers",
                Map.of("offeredPriceCent", 2000, "message", "考虑一下"), buyer)).get("id").asLong();

        JsonNode cancelled = data(post("/api/offers/" + offerId + "/cancel", null, buyer));
        assertEquals("CANCELLED", cancelled.get("status").asText(), "撤销后报价应变为已取消");
    }
}
