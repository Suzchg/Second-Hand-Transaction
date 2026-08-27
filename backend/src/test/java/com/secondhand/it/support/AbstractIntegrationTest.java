package com.secondhand.it.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.aftersale.repository.AfterSaleRepository;
import com.secondhand.auth.repository.UserIdentityRepository;
import com.secondhand.auth.repository.UserRepository;
import com.secondhand.offer.repository.OfferRepository;
import com.secondhand.order.repository.OrderEventRepository;
import com.secondhand.order.repository.OrderRepository;
import com.secondhand.order.repository.ShipmentRepository;
import com.secondhand.product.repository.ProductRepository;
import com.secondhand.report.repository.ReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 集成测试公共基类。
 *
 * 职责：
 * 1. 启动完整 Spring Boot 上下文（真实 Controller → Service → Repository → 数据库 链路）；
 * 2. 通过 MockMvc 以 HTTP 方式调用对外 REST API（/api/**）；
 * 3. 注入各模块 Repository，用于直接断言数据库持久化结果；
 * 4. 双数据库方案：系统属性 it.db（默认 h2）。
 *    - h2    ：H2 内存库（MySQL 兼容模式），配置见 application-it.yml；
 *    - mysql ：Testcontainers + MySQL 8.0 单例容器（见 SharedMySqlContainer）；
 *              Docker 不可用时自动降级 H2 并打印告警。
 */
@ActiveProfiles("it")
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    protected static final String DEFAULT_PASSWORD = "pass-123456";
    /** AdminInitializer 在应用启动时自动创建的管理员账号 */
    protected static final String ADMIN_PHONE = "13800000000";
    protected static final String ADMIN_PASSWORD = "admin123";

    private static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);
    private static final AtomicLong PHONE_SEQ = new AtomicLong();

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;

    // ==== 各模块 Repository（用于数据库访问断言） ====
    @Autowired protected UserRepository userRepo;
    @Autowired protected UserIdentityRepository identityRepo;
    @Autowired protected ProductRepository productRepo;
    @Autowired protected OrderRepository orderRepo;
    @Autowired protected OrderEventRepository orderEventRepo;
    @Autowired protected ShipmentRepository shipmentRepo;
    @Autowired protected OfferRepository offerRepo;
    @Autowired protected AfterSaleRepository afterSaleRepo;
    @Autowired protected ReportRepository reportRepo;

    /** 双数据库切换入口 */
    @DynamicPropertySource
    static void dataSourceSwitch(DynamicPropertyRegistry registry) {
        String mode = System.getProperty("it.db", "h2");
        if ("mysql".equalsIgnoreCase(mode)) {
            if (SharedMySqlContainer.dockerAvailable()) {
                SharedMySqlContainer.register(registry);
            } else {
                log.warn("[IT] it.db=mysql 但当前环境 Docker 不可用，自动降级为 H2 内存库");
            }
        }
        // h2 模式无需动态注册：数据源配置来自 application-it.yml
    }

    // ==================================================================
    // HTTP 请求辅助
    // ==================================================================

    protected ResultActions doGet(String url, String token) throws Exception {
        MockHttpServletRequestBuilder b = get(url);
        if (token != null) b.header("Authorization", "Bearer " + token);
        return mockMvc.perform(b);
    }

    protected ResultActions doPost(String url, String token, String jsonBody) throws Exception {
        MockHttpServletRequestBuilder b = post(url)
                .contentType(MediaType.APPLICATION_JSON);
        if (jsonBody != null) b.content(jsonBody);
        if (token != null) b.header("Authorization", "Bearer " + token);
        return mockMvc.perform(b);
    }

    protected ResultActions doPut(String url, String token, String jsonBody) throws Exception {
        MockHttpServletRequestBuilder b = put(url)
                .contentType(MediaType.APPLICATION_JSON);
        if (jsonBody != null) b.content(jsonBody);
        if (token != null) b.header("Authorization", "Bearer " + token);
        return mockMvc.perform(b);
    }

    /** 读取响应体字符串（UTF-8） */
    protected String content(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** 解析响应体 JSON 根节点 */
    protected JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(content(result));
    }

    /** 解析响应体的 data 节点 */
    protected JsonNode data(ResultActions result) throws Exception {
        return json(result).path("data");
    }

    // ==================================================================
    // 业务链路搭建辅助（每一步都走真实 HTTP API，即模块间真实调用链）
    // ==================================================================

    /** 已注册测试用户（含 token） */
    // TestUser / CompletedOrderFixture 定义为 support 包顶层记录
    /** 生成全局唯一手机号 */
    protected static String uniquePhone() {
        return String.format("13988%06d", PHONE_SEQ.incrementAndGet());
    }

    /** 注册一个全新用户并返回其 token（用例1主流程复用入口） */
    protected TestUser registerUser() throws Exception {
        String phone = uniquePhone();
        return registerUser(phone, DEFAULT_PASSWORD);
    }

    /** 按指定手机号/密码注册 */
    protected TestUser registerUser(String phone, String password) throws Exception {
        ResultActions ra = doPost("/api/auth/register", null, """
                {"identityType":"PHONE","identifier":"%s","password":"%s"}
                """.formatted(phone, password))
                .andExpect(status -> { /* 由调用方断言 */ });
        JsonNode d = data(ra);
        return new TestUser(d.path("userId").asLong(), d.path("accessToken").asText(), phone, password);
    }

    /** 登录并返回 accessToken */
    protected String loginToken(String phone, String password) throws Exception {
        ResultActions ra = doPost("/api/auth/login", null, """
                {"identityType":"PHONE","identifier":"%s","password":"%s"}
                """.formatted(phone, password));
        return data(ra).path("accessToken").asText();
    }

    /** 管理员 token（AdminInitializer 启动时自动创建） */
    protected String adminToken() throws Exception {
        return loginToken(ADMIN_PHONE, ADMIN_PASSWORD);
    }

    /** 卖家发布一件在售商品，返回商品 ID（用例2主流程复用入口） */
    protected long createProduct(String sellerToken, String title, int priceCent) throws Exception {
        ResultActions ra = doPost("/api/products", sellerToken, """
                {"title":"%s","priceCent":%d,"description":"集成测试商品描述","quantity":1,
                 "condition":"LIKE_NEW","freeShipping":true,"shippingFeeCent":0}
                """.formatted(title, priceCent));
        return data(ra).path("id").asLong();
    }

    /** 买家以商品原价下单，返回订单 ID（订单状态 WAIT_PAY） */
    protected long placeOrder(String buyerToken, long productId) throws Exception {
        ResultActions ra = doPost("/api/orders", buyerToken, """
                {"productId":%d,"receiverName":"测试收货人","receiverPhone":"13800001111",
                 "receiverAddress":"北京市海淀区中关村大街1号"}
                """.formatted(productId));
        return data(ra).path("id").asLong();
    }

    /** 买家支付订单（WAIT_PAY → WAIT_DELIVER），返回订单 ID */
    protected long payOrder(String buyerToken, long orderId) throws Exception {
        doPost("/api/orders/" + orderId + "/pay", buyerToken, null);
        return orderId;
    }

    /** 卖家发货（WAIT_DELIVER → WAIT_RECEIVE），返回订单 ID */
    protected long shipOrder(String sellerToken, long orderId) throws Exception {
        doPost("/api/orders/" + orderId + "/ship", sellerToken,
                """
                {"carrierCode":"SF","trackingNo":"SF%09d"}""".formatted(orderId));
        return orderId;
    }

    /** 买家确认收货（WAIT_RECEIVE → COMPLETED），返回订单 ID */
    protected long confirmOrder(String buyerToken, long orderId) throws Exception {
        doPost("/api/orders/" + orderId + "/confirm", buyerToken, null);
        return orderId;
    }

    /**
     * 搭建"已完成的订单"完整链路：注册买卖家 → 发布商品 → 下单 → 支付 → 发货 → 确认收货。
     * 返回该链路的所有关键句柄（售后/评价等用例的前置状态）。
     */
    protected CompletedOrderFixture completedOrder() throws Exception {
        TestUser seller = registerUser();
        TestUser buyer = registerUser();
        long productId = createProduct(seller.token(), "售后链路测试商品", 88000);
        long orderId = placeOrder(buyer.token(), productId);
        payOrder(buyer.token(), orderId);
        shipOrder(seller.token(), orderId);
        confirmOrder(buyer.token(), orderId);
        return new CompletedOrderFixture(seller, buyer, productId, orderId);
    }
}
