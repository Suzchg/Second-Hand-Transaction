package com.secondhand.testutil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/** 共用完整后端链路，不替换业务组件、不包裹测试事务，验证请求结束后的真实落库结果。 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiIntegrationTestBase extends MySqlTestSupport {
    protected static final String PASSWORD = "integration-pass-123";
    private static final AtomicLong SEQUENCE = new AtomicLong();
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected JdbcTemplate jdbc;

    /** 测试用户及其真实登录凭证。 */
    protected record Actor(long id, String token, String identifier) {}
    /** 一笔交易的买卖双方、商品和订单，供后续步骤复用。 */
    protected record Trade(Actor seller, Actor buyer, long productId, long orderId) {}

    protected String nextPhone() {
        // 使用递增手机号，避免不同用例重复注册。
        return "139" + (10000000 + SEQUENCE.incrementAndGet());
    }

    protected MvcResult call(String method, String path, Object body, Actor actor) throws Exception {
        // 通过 MockMvc 进入真实过滤器和控制器；actor 为空时模拟未登录请求。
        var request = request(HttpMethod.valueOf(method), path).contentType("application/json")
                .with(req -> {
                    // 模拟不同来源的客户端，保留生产限流组件且避免业务用例互相限流。
                    long client = SEQUENCE.incrementAndGet();
                    req.setRemoteAddr("198.18." + ((client / 254) % 256) + "." + (client % 254 + 1));
                    return req;
                });
        if (body != null) request.content(mapper.writeValueAsBytes(body));
        if (actor != null) request.header("Authorization", "Bearer " + actor.token());
        return mvc.perform(request).andReturn();
    }

    protected JsonNode ok(String method, String path, Object body, Actor actor) throws Exception {
        // 普通成功接口统一断言 HTTP 200；注册接口另行断言 201。
        return success(call(method, path, body, actor), 200);
    }

    protected JsonNode success(MvcResult result, int status) throws Exception {
        // 显式按 UTF-8 解码中文响应，并校验成功标记和数据节点。
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).as(body).isEqualTo(status);
        JsonNode root = mapper.readTree(body);
        assertThat(root.path("success").asBoolean()).as(body).isTrue();
        assertThat(root.has("data")).as(body).isTrue();
        return root.get("data");
    }

    protected void error(String method, String path, Object body, Actor actor,
                         int status, String code) throws Exception {
        // 业务失败必须同时匹配 HTTP 状态、错误码和非空错误消息。
        MvcResult result = call(method, path, body, actor);
        String response = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).as(response).isEqualTo(status);
        JsonNode root = mapper.readTree(response);
        assertThat(root.path("success").asBoolean()).as(response).isFalse();
        assertThat(root.at("/error/code").asText()).isEqualTo(code);
        assertThat(root.at("/error/message").asText()).isNotBlank();
    }

    protected Actor user() throws Exception {
        // 走注册接口创建普通用户，不直接插入数据库或伪造 JWT。
        String phone = nextPhone();
        JsonNode data = success(call("POST", "/api/auth/register", credentials("PHONE", phone, PASSWORD), null), 201);
        assertThat(data.path("role").asText()).isEqualTo("USER");
        return actor(data, phone);
    }

    protected Actor admin() throws Exception {
        // 使用测试库初始化的管理员登录，获取真实管理员令牌。
        JsonNode data = ok("POST", "/api/auth/login", credentials("PHONE", "13800000000", "admin123"), null);
        assertThat(data.path("role").asText()).isEqualTo("ADMIN");
        return actor(data, "13800000000");
    }

    protected Actor actor(JsonNode data, String identifier) {
        // 登录凭证和用户 ID 必须有效，后续权限断言才有意义。
        assertThat(data.path("accessToken").asText()).isNotBlank();
        assertThat(data.path("userId").asLong()).isPositive();
        return new Actor(data.path("userId").asLong(), data.path("accessToken").asText(), identifier);
    }

    protected Map<String, Object> credentials(String type, String identifier, String password) {
        // 注册和登录共用同一组身份参数。
        return Map.of("identityType", type, "identifier", identifier, "password", password);
    }

    protected Map<String, Object> productBody(int quantity) {
        // 默认商品 1000 分、九成新、免邮；标题保持唯一。
        return Map.of("title", "API商品" + SEQUENCE.incrementAndGet(), "priceCent", 1000,
                "description", "二手闲置，功能正常", "quantity", quantity, "condition", "NINE_TENTHS",
                "freeShipping", true);
    }

    protected long product(Actor seller, int quantity) throws Exception {
        // 通过发布接口创建商品，返回后续请求使用的 ID。
        return ok("POST", "/api/products", productBody(quantity), seller).path("id").asLong();
    }

    protected Map<String, Object> receiver() {
        // 提供完整收件信息，避免正常交易被地址校验拦截。
        return Map.of("receiverName", "测试买家", "receiverPhone", "13900000000",
                "receiverAddress", "测试省测试市测试路1号");
    }

    protected Map<String, Object> orderBody(long productId) {
        // 在收件信息中补入本次要购买的商品。
        var body = new java.util.HashMap<>(receiver());
        body.put("productId", productId);
        return body;
    }

    protected Trade trade() throws Exception {
        // 准备两名独立用户和一件商品，真实下单后停留在待付款状态。
        Actor seller = user(), buyer = user();
        long productId = product(seller, 1);
        long orderId = ok("POST", "/api/orders", orderBody(productId), buyer).path("id").asLong();
        return new Trade(seller, buyer, productId, orderId);
    }

    protected Trade paidTrade() throws Exception {
        // 在下单前置上完成支付，并确认订单进入待发货状态。
        Trade t = trade();
        assertThat(ok("POST", "/api/orders/" + t.orderId() + "/pay", null, t.buyer())
                .path("status").asText()).isEqualTo("WAIT_DELIVER");
        return t;
    }

    protected Map<String, Object> shipping(long orderId) {
        // 用订单 ID 构造运单号，便于核对物流接口与数据库是否对应。
        return Map.of("carrierCode", "SF", "trackingNo", "IT-SF-" + orderId);
    }

    protected Trade completedTrade() throws Exception {
        // 完成支付、发货和收货，为售后用例准备有效订单。
        Trade t = paidTrade();
        ok("POST", "/api/orders/" + t.orderId() + "/ship", shipping(t.orderId()), t.seller());
        assertThat(ok("POST", "/api/orders/" + t.orderId() + "/confirm", null, t.buyer())
                .path("status").asText()).isEqualTo("COMPLETED");
        return t;
    }

    protected long count(String sql, Object... args) {
        // 用独立 JDBC 查询验证记录数量或关联关系，不依赖 JPA 缓存。
        return jdbc.queryForObject(sql, Long.class, args);
    }

    protected void orderStatus(long id, String status) {
        // 断言数据库中的订单状态已经提交。
        assertThat(jdbc.queryForObject("select status from orders where id=?", String.class, id)).isEqualTo(status);
    }

    protected void stock(long id, int quantity, String status) {
        // 同时核对库存数量和上下架状态，防止只改其中一个字段。
        assertThat(jdbc.queryForObject("select quantity from products where id=?", Integer.class, id)).isEqualTo(quantity);
        assertThat(jdbc.queryForObject("select status from products where id=?", String.class, id)).isEqualTo(status);
    }
}
