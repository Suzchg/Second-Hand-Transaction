package com.secondhand.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 场景 2：商品发布与编辑。
 */
@DisplayName("场景 2：商品发布与编辑")
class ProductE2eTest extends E2eTestBase {

    @Test
    @DisplayName("卖家发布商品成功并进入在售状态")
    void publishProductSuccess() {
        String seller = registerUser();
        ResponseEntity<String> resp = post("/api/products", productBody("测试商品-九成新手机"), seller);

        assertEquals(HttpStatus.OK, resp.getStatusCode(), "发布商品应成功");
        JsonNode data = data(resp);
        assertEquals("ON_SALE", data.get("status").asText(), "商品应处于在售状态");
        assertTrue(data.get("id").asLong() > 0, "商品应返回有效 id");
        assertEquals("测试商品-九成新手机", data.get("title").asText());
    }

    @Test
    @DisplayName("发布商品校验失败应返回 400")
    void publishProductValidationFails() {
        String seller = registerUser();
        // 缺少必填 description
        ResponseEntity<String> resp = post("/api/products",
                Map.of("title", "无描述商品", "priceCent", 1000), seller);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("VALIDATION_ERROR", errorCode(resp));
    }

    @Test
    @DisplayName("编辑他人商品应返回 403 FORBIDDEN")
    void editOthersProductForbidden() {
        String seller = registerUser();
        String other = registerUser();
        long pid = data(post("/api/products", productBody("他人商品"), seller)).get("id").asLong();

        ResponseEntity<String> resp = put("/api/products/" + pid,
                Map.of("title", "恶意篡改", "priceCent", 1), other);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(), "编辑他人商品应返回 403");
        assertEquals("FORBIDDEN", errorCode(resp));
    }

    @Test
    @DisplayName("卖家编辑自己的商品成功")
    void editOwnProductSuccess() {
        String seller = registerUser();
        long pid = data(post("/api/products", productBody("原始标题"), seller)).get("id").asLong();

        ResponseEntity<String> resp = put("/api/products/" + pid,
                Map.of("title", "更新后的标题", "priceCent", 2500), seller);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("更新后的标题", data(resp).get("title").asText());
        assertEquals(2500, data(resp).get("priceCent").asInt());
    }
}
