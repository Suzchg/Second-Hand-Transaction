package com.secondhand.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondhand.auth.entity.IdentityType;
import com.secondhand.auth.entity.Role;
import com.secondhand.auth.entity.User;
import com.secondhand.auth.entity.UserIdentity;
import com.secondhand.auth.entity.UserStatus;
import com.secondhand.auth.repository.UserIdentityRepository;
import com.secondhand.auth.repository.UserRepository;
import com.secondhand.auth.security.JwtService;
import com.secondhand.product.category.entity.Category;
import com.secondhand.product.category.repository.CategoryRepository;
import com.secondhand.product.entity.Product;
import com.secondhand.product.entity.ProductCondition;
import com.secondhand.product.entity.ProductStatus;
import com.secondhand.product.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.MySQLContainer;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * 集成测试基类。
 *
 * 设计要点：
 * - 使用 Testcontainers 启动真实 MySQL 8.0 容器，与生产环境一致
 * - 静态容器在 JVM 内共享，避免每个子类都启动一个新容器（节省启动时间）
 * - 通过 @DynamicPropertySource 把容器连接信息注入到 Spring 上下文
 * - 提供 JWT 令牌生成、用户/商品构造、MockMvc 鉴权请求等便利方法
 * - 限流规避：测试用 X-Forwarded-For 模拟不同 IP，避免误触发 RateLimit
 *
 * 子类只需 @Testcontainers + 继承本类即可获得完整测试基础设施。
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationIT {

    // ============ Testcontainers MySQL 8.0 ============

    private static final MySQLContainer<?> mysql;

    static {
        mysql = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("secondhand_it")
                .withUsername("secondhand")
                .withPassword("secondhand-it-password")
                .withReuse(true);
        mysql.start();
        // JVM 退出时自动停止容器
        Runtime.getRuntime().addShutdownHook(new Thread(mysql::stop));
    }

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
        // 测试期间用 create-drop，保证每个测试类启动时表结构干净
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.sql.init.mode", () -> "never");
        // 关闭 JPA open-in-view（与生产一致）
        registry.add("spring.jpa.open-in-view", () -> "false");
    }

    // ============ 注入的 Spring Beans ============

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected JwtService jwtService;
    @Autowired protected UserRepository userRepo;
    @Autowired protected UserIdentityRepository identityRepo;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected ProductRepository productRepo;
    @Autowired protected CategoryRepository categoryRepo;

    // ============ 测试数据生成器 ============

    /** 唯一性计数器，避免重复 phone/nickname */
    private static final AtomicInteger phoneSeq = new AtomicInteger(1_390_000_000);
    private static final Map<String, Long> userCache = new ConcurrentHashMap<>();

    /**
     * 在 DB 直接插入一个用户（不走 HTTP /api/auth/register，规避 RateLimit）。
     * 返回用户 ID。
     */
    protected long createUser(String phone, String password, Role role) {
        // 命中缓存，避免同一 phone 重复插入（DB 唯一约束会失败）
        Long cached = userCache.get(phone);
        if (cached != null) return cached;

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setNickname("测试" + phone);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(role);
        user.setPhone(phone);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user = userRepo.save(user);

        // 同步生成昵称：用户 + (111110 + id)
        user.setNickname("用户" + (111110 + user.getId()));
        userRepo.save(user);

        UserIdentity identity = new UserIdentity();
        identity.setUser(user);
        identity.setIdentityType(IdentityType.PHONE);
        identity.setIdentifier(phone);
        identity.setVerified(true);
        identity.setCreatedAt(now);
        identity.setUpdatedAt(now);
        identityRepo.save(identity);

        userCache.put(phone, user.getId());
        return user.getId();
    }

    /** 创建普通用户（USER 角色） */
    protected long createUser(String phonePrefix) {
        return createUser(phonePrefix, "Test1234", Role.USER);
    }

    /** 生成下一个唯一手机号（每次调用递增） */
    protected String nextPhone() {
        return String.valueOf(phoneSeq.getAndIncrement());
    }

    /** 生成一个 USER 用户的完整测试上下文（含手机号、密码、userId、JWT 令牌） */
    protected TestUser createTestUser() {
        return createTestUser(Role.USER);
    }

    protected TestUser createTestUser(Role role) {
        String phone = nextPhone();
        String password = "Test1234";
        long userId = createUser(phone, password, role);
        String token = jwtService.createAccessToken(userId, role.name());
        return new TestUser(userId, phone, password, role.name(), token);
    }

    /**
     * 直接构造一个商品（DB 插入，不走 HTTP 创建接口）。
     * 用于订单、售后、举报等测试的快速前置数据准备。
     */
    protected long createProduct(long sellerId, String title, int priceCent, int quantity) {
        return createProduct(sellerId, title, priceCent, quantity, ProductStatus.ON_SALE);
    }

    protected long createProduct(long sellerId, String title, int priceCent,
                                  int quantity, ProductStatus status) {
        LocalDateTime now = LocalDateTime.now();
        Product p = new Product();
        p.setSellerId(sellerId);
        p.setTitle(title);
        p.setPriceCent(priceCent);
        p.setCoverImageUrl("https://example.com/cover/" + System.nanoTime() + ".jpg");
        p.setDescription("测试商品描述：" + title);
        p.setQuantity(quantity);
        p.setStatus(status);
        p.setCondition(ProductCondition.NINE_TENTHS);
        p.setFreeShipping(true);
        p.setShippingFeeCent(0);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        // categoryId 留空，避免与 CategoryService 的种子数据产生耦合
        return productRepo.save(p).getId();
    }

    /** 直接构造一个分类（一级分类） */
    protected long createCategory(String name) {
        LocalDateTime now = LocalDateTime.now();
        Category c = new Category();
        c.setName(name);
        c.setParentId(null);
        c.setSortOrder(100);
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        return categoryRepo.save(c).getId();
    }

    // ============ MockMvc 鉴权请求辅助方法 ============

    /** GET 请求，带 Bearer Token */
    protected MockHttpServletRequestBuilder authGet(String token, String url) {
        return get(url).header("Authorization", bearer(token))
                .header("X-Forwarded-For", "10.0.0." + (token == null ? 0 : Math.abs(token.hashCode() % 250)));
    }

    /** POST 请求，带 Bearer Token，无 Body（用于收藏、点赞等无 body 端点） */
    protected MockHttpServletRequestBuilder authPost(String token, String url) {
        return authPost(token, url, java.util.Map.of());
    }

    /** POST 请求，带 Bearer Token + JSON Body */
    protected MockHttpServletRequestBuilder authPost(String token, String url, Object body) {
        return postJson(url, body).header("Authorization", bearer(token))
                .header("X-Forwarded-For", "10.0.0." + Math.abs(token.hashCode() % 250));
    }

    /** POST 请求（不带 Token），用于公开端点 */
    protected MockHttpServletRequestBuilder postJson(String url, Object body) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(toJson(body));
    }

    /** PUT 请求，带 Bearer Token + JSON Body */
    protected MockHttpServletRequestBuilder authPut(String token, String url, Object body) {
        return put(url).contentType(MediaType.APPLICATION_JSON).content(toJson(body))
                .header("Authorization", bearer(token))
                .header("X-Forwarded-For", "10.0.0." + Math.abs(token.hashCode() % 250));
    }

    /** DELETE 请求，带 Bearer Token */
    protected MockHttpServletRequestBuilder authDelete(String token, String url) {
        return delete(url).header("Authorization", bearer(token))
                .header("X-Forwarded-For", "10.0.0." + Math.abs(token.hashCode() % 250));
    }

    private static String bearer(String token) {
        return token == null ? "" : "Bearer " + token;
    }

    // ============ JSON 辅助方法 ============

    protected String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 从 MvcResult 解析统一响应的 data 节点 */
    protected JsonNode parseData(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data");
    }

    /** 从 MvcResult 解析整个响应 */
    protected JsonNode parseResponse(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** 提取 data.id（Long） */
    protected long extractId(MvcResult result) throws Exception {
        return parseData(result).path("id").asLong();
    }

    /** 提取 data 中的指定字段（字符串） */
    protected String extractField(MvcResult result, String field) throws Exception {
        JsonNode node = parseData(result).path(field);
        return node.isMissingNode() ? null : node.asText();
    }

    // ============ 测试用户上下文对象 ============

    /** 封装一个测试用户的完整上下文：userId + 令牌 + 手机号 */
    protected record TestUser(long userId, String phone, String password, String role, String token) {}
}
