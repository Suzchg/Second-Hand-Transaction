package com.secondhand.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 场景 4：卖家发货。
 */
@DisplayName("场景 4：卖家发货")
class ShipE2eTest extends E2eTestBase {

    @Test
    @DisplayName("卖家发货成功，生成运单并进入待收货")
    void shipSuccess() {
        String seller = registerUser();
        String buyer = registerUser();
        long pid = data(post("/api/products", productBody("发货商品"), seller)).get("id").asLong();
        long oid = data(post("/api/orders", orderBody(pid), buyer)).get("id").asLong();
        post("/api/orders/" + oid + "/pay", null, buyer);

        JsonNode shipment = data(post("/api/orders/" + oid + "/ship",
                Map.of("carrierCode", "SF", "trackingNo", "SF123456789"), seller));
        assertNotNull(shipment.get("trackingNo"), "发货应生成运单");
        assertEquals("SF123456789", shipment.get("trackingNo").asText());

        JsonNode order = data(get("/api/orders/" + oid, buyer));
        assertEquals("WAIT_RECEIVE", order.get("order").get("status").asText(), "发货后应进入待收货");
    }

    @Test
    @DisplayName("未支付订单发货应返回 409（状态不符）")
    void shipWithoutPayConflict() {
        String seller = registerUser();
        String buyer = registerUser();
        long pid = data(post("/api/products", productBody("未支付商品"), seller)).get("id").asLong();
        long oid = data(post("/api/orders", orderBody(pid), buyer)).get("id").asLong();

        ResponseEntity<String> resp = post("/api/orders/" + oid + "/ship",
                Map.of("carrierCode", "SF", "trackingNo", "SF987654321"), seller);

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode(), "未支付发货应返回 409");
        assertEquals("CONFLICT", errorCode(resp));
    }
}
