package com.secondhand.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.testutil.MySqlTestSupport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E 测试基类：启动完整 Spring Boot 上下文（随机端口），并封装 HTTP 调用与常用断言辅助。
 * 各场景测试类继承本类，共享同一个 Spring 上下文（自动缓存复用，仅启动一次）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
abstract class E2eTestBase extends MySqlTestSupport {

    protected static final ObjectMapper MAPPER = new ObjectMapper();
    protected static final AtomicLong SEQ = new AtomicLong(0);

    protected static final String ADMIN_PHONE = "13800000000";
    protected static final String ADMIN_PASSWORD = "admin123";

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    protected int port;

    // =====================================================================
    //  业务辅助
    // =====================================================================

    /** 注册一个新用户（唯一手机号），返回其访问令牌 */
    protected String registerUser() {
        String phone = nextPhone();
        JsonNode data = data(post("/api/auth/register",
                Map.of("identityType", "PHONE", "identifier", phone, "password", "pass123456"), null));
        return data.get("accessToken").asText();
    }

    /** 获取管理员令牌（系统启动时自动创建：13800000000 / admin123） */
    protected String adminToken() {
        JsonNode data = data(post("/api/auth/login",
                Map.of("identityType", "PHONE", "identifier", ADMIN_PHONE, "password", ADMIN_PASSWORD), null));
        return data.get("accessToken").asText();
    }

    /** 售后场景上下文：卖家 token、买家 token、已完成订单 ID */
    protected record Ctx(String sellerToken, String buyerToken, long orderId) {}

    /** 走通「下单→支付→发货→确认收货」，返回完成态订单上下文 */
    protected Ctx prepareCompletedOrder() {
        String seller = registerUser();
        String buyer = registerUser();
        long pid = data(post("/api/products", productBody("售后商品"), seller)).get("id").asLong();
        long oid = data(post("/api/orders", orderBody(pid), buyer)).get("id").asLong();
        post("/api/orders/" + oid + "/pay", null, buyer);
        post("/api/orders/" + oid + "/ship",
                Map.of("carrierCode", "SF", "trackingNo", "SHIP" + oid), seller);
        assertEquals("COMPLETED", data(post("/api/orders/" + oid + "/confirm", null, buyer))
                .get("status").asText(), "确认收货后订单应为已完成");
        return new Ctx(seller, buyer, oid);
    }

    protected Map<String, Object> productBody(String title) {
        return productBody(title, 1000);
    }

    protected Map<String, Object> productBody(String title, int priceCent) {
        return Map.of("title", title, "priceCent", priceCent, "description", "二手闲置，功能正常",
                "condition", "NINE_TENTHS", "freeShipping", true);
    }

    protected Map<String, Object> orderBody(long productId) {
        return Map.of("productId", productId, "receiverName", "张三",
                "receiverPhone", "13900000000", "receiverAddress", "XX省XX市XX区XX路1号");
    }

    protected String nextPhone() {
        return "139" + (10000000L + SEQ.incrementAndGet());
    }

    // =====================================================================
    //  HTTP 调用辅助（JDK HttpClient，避免 HttpURLConnection 对 401 的流式重试问题）
    // =====================================================================

    protected ResponseEntity<String> request(String path, String method, Object body, String token) {
        try {
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "application/json")
                    // 每个请求使用独立来源 IP，规避注册/登录接口的 IP 限流
                    .header("X-Forwarded-For", "e2e-" + SEQ.incrementAndGet());
            if (token != null) {
                rb.header("Authorization", "Bearer " + token);
            }
            String json = writeJson(body);
            rb.method(method, json == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json));
            HttpResponse<String> resp = HTTP.send(rb.build(), HttpResponse.BodyHandlers.ofString());
            return new ResponseEntity<>(resp.body(), HttpStatusCode.valueOf(resp.statusCode()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected ResponseEntity<String> get(String path, String token) {
        return request(path, "GET", null, token);
    }

    protected ResponseEntity<String> post(String path, Object body, String token) {
        return request(path, "POST", body, token);
    }

    protected ResponseEntity<String> put(String path, Object body, String token) {
        return request(path, "PUT", body, token);
    }

    // =====================================================================
    //  断言辅助
    // =====================================================================

    protected JsonNode read(ResponseEntity<String> resp) {
        try {
            return MAPPER.readTree(resp.getBody());
        } catch (Exception e) {
            throw new RuntimeException("响应解析失败: " + resp.getBody(), e);
        }
    }

    /** 断言 success=true 并返回 data 节点 */
    protected JsonNode data(ResponseEntity<String> resp) {
        JsonNode root = read(resp);
        assertTrue(root.get("success").asBoolean(),
                "预期成功，实际响应: " + resp.getStatusCode() + " " + resp.getBody());
        return root.get("data");
    }

    protected String errorCode(ResponseEntity<String> resp) {
        return read(resp).get("error").get("code").asText();
    }

    private String writeJson(Object body) {
        try {
            return body == null ? null : MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
