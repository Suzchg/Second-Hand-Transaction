package com.secondhand.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 场景 3：商品购买（下单 + 支付）。
 */
@DisplayName("场景 3：商品购买（下单 + 支付）")
class PurchaseE2eTest extends E2eTestBase {

    @Test
    @DisplayName("买家下单并支付成功，订单进入待发货")
    void purchaseSuccess() {
        String seller = registerUser();
        String buyer = registerUser();
        long pid = data(post("/api/products", productBody("待售商品"), seller)).get("id").asLong();

        JsonNode order = data(post("/api/orders", orderBody(pid), buyer));
        assertEquals("WAIT_PAY", order.get("status").asText(), "下单后应为待支付");
        long oid = order.get("id").asLong();

        JsonNode paid = data(post("/api/orders/" + oid + "/pay", null, buyer));
        assertEquals("WAIT_DELIVER", paid.get("status").asText(), "支付后应进入待发货");
    }

    @Test
    @DisplayName("购买自己的商品应返回 403")
    void purchaseOwnProductForbidden() {
        String seller = registerUser();
        long pid = data(post("/api/products", productBody("自有商品"), seller)).get("id").asLong();

        ResponseEntity<String> resp = post("/api/orders", orderBody(pid), seller);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertEquals("FORBIDDEN", errorCode(resp));
    }

    @Test
    @DisplayName("重复支付（状态不符）应返回 409")
    void payTwiceConflict() {
        String seller = registerUser();
        String buyer = registerUser();
        long pid = data(post("/api/products", productBody("重复支付商品"), seller)).get("id").asLong();
        long oid = data(post("/api/orders", orderBody(pid), buyer)).get("id").asLong();
        post("/api/orders/" + oid + "/pay", null, buyer);

        ResponseEntity<String> resp = post("/api/orders/" + oid + "/pay", null, buyer);
        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode(), "重复支付应返回 409");
        assertEquals("CONFLICT", errorCode(resp));
    }
}
