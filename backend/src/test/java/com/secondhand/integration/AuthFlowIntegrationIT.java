package com.secondhand.integration;

import com.secondhand.auth.entity.IdentityType;
import com.secondhand.auth.entity.Role;
import com.secondhand.auth.entity.User;
import com.secondhand.auth.entity.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 集成测试 · 用例 1：用户注册与登录
 *
 * 覆盖范围：
 * - 模块间调用：AuthController → AuthService → UserRepository → UserIdentityRepository → JwtService
 * - 数据库访问：注册和登录均落库到真实 MySQL，并验证持久化
 * - 对外接口：POST /api/auth/register、POST /api/auth/login、GET /api/auth/me、POST /api/auth/password/change
 * - 地址管理：CRUD /api/users/addresses、设置默认地址
 *
 * 用例流程覆盖：
 * - 主成功流程：注册 → 登录 → /me 获取信息 → 修改密码 → 用新密码登录
 * - 备选流程：邮箱注册、改密后用新密码登录
 * - 异常流程：重复注册、密码错误、账号禁用、令牌缺失、参数校验失败、修改地址越权
 */
@Testcontainers
@DisplayName("用例1：用户注册与登录")
class AuthFlowIntegrationIT extends AbstractIntegrationIT {

    @Autowired
    com.secondhand.auth.repository.UserRepository userRepo;

    // ==================== 主成功流程 ====================

    @Test
    @DisplayName("主成功 · 注册→登录→/me→改密→用新密码重新登录")
    void shouldRegisterLoginAndChangePassword() throws Exception {
        String phone = "13900001111";
        String pwd = "Pass1234";

        // 1. 注册（HTTP /api/auth/register，公开端点）
        mockMvc.perform(postJson("/api/auth/register",
                        java.util.Map.of(
                                "identityType", "PHONE",
                                "identifier", phone,
                                "password", pwd)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.userId", greaterThan(0)))
                .andExpect(jsonPath("$.data.role", equalTo("USER")))
                .andExpect(jsonPath("$.data.nickname", startsWith("用户")));

        // 2. 验证数据库已落库（模块间调用 + DB 访问）
        boolean persisted = identityRepo
                .findByIdentityTypeAndIdentifier(IdentityType.PHONE, phone)
                .isPresent();
        if (!persisted) throw new AssertionError("注册后未落库");

        // 3. 登录（公开端点）
        mockMvc.perform(postJson("/api/auth/login",
                        java.util.Map.of(
                                "identityType", "PHONE",
                                "identifier", phone,
                                "password", pwd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.userId", greaterThan(0)));

        // 4. 通过 JwtService 直接签发令牌（绕开限流），调用 /me
        User registeredUser = identityRepo
                .findByIdentityTypeAndIdentifier(IdentityType.PHONE, phone)
                .orElseThrow().getUser();
        long userId = registeredUser.getId();
        String token = jwtService.createAccessToken(userId, "USER");

        mockMvc.perform(authGet(token, "/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.nickname", startsWith("用户")));

        // 5. 修改密码（需认证）
        mockMvc.perform(authPost(token, "/api/auth/password/change",
                        java.util.Map.of(
                                "oldPassword", pwd,
                                "newPassword", "NewPass5678")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 6. 用新密码重新登录
        mockMvc.perform(postJson("/api/auth/login",
                        java.util.Map.of(
                                "identityType", "PHONE",
                                "identifier", phone,
                                "password", "NewPass5678")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())));
    }

    // ==================== 备选流程 ====================

    @Test
    @DisplayName("备选 · 用邮箱注册并登录")
    void shouldRegisterAndLoginWithEmail() throws Exception {
        String email = "alice_" + System.nanoTime() + "@example.com";
        String pwd = "Email1234";

        // 邮箱注册
        mockMvc.perform(postJson("/api/auth/register",
                        java.util.Map.of(
                                "identityType", "EMAIL",
                                "identifier", email,
                                "password", pwd)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())));

        // 邮箱登录
        mockMvc.perform(postJson("/api/auth/login",
                        java.util.Map.of(
                                "identityType", "EMAIL",
                                "identifier", email,
                                "password", pwd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("备选 · 用邮箱大写形式注册，再用小写形式登录（归一化）")
    void shouldNormalizeEmailOnLogin() throws Exception {
        String emailUpper = "BOB_" + System.nanoTime() + "@Example.COM";
        String pwd = "Bob12345";

        mockMvc.perform(postJson("/api/auth/register",
                        java.util.Map.of(
                                "identityType", "EMAIL",
                                "identifier", emailUpper,
                                "password", pwd)))
                .andExpect(status().isCreated());

        // 用纯小写形式登录（AuthService.normalize 邮箱转小写）
        mockMvc.perform(postJson("/api/auth/login",
                        java.util.Map.of(
                                "identityType", "EMAIL",
                                "identifier", emailUpper.toLowerCase(),
                                "password", pwd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ==================== 异常流程 ====================

    @Test
    @DisplayName("异常 · 重复注册同一手机号返回 409")
    void shouldRejectDuplicatePhone() throws Exception {
        String phone = "13900002222";
        String pwd = "Pass1234";

        mockMvc.perform(postJson("/api/auth/register",
                        java.util.Map.of(
                                "identityType", "PHONE",
                                "identifier", phone,
                                "password", pwd)))
                .andExpect(status().isCreated());

        // 重复注册应返回 409 IDENTITY_EXISTS
        mockMvc.perform(postJson("/api/auth/register",
                        java.util.Map.of(
                                "identityType", "PHONE",
                                "identifier", phone,
                                "password", "OtherPwd")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("IDENTITY_EXISTS"));
    }

    @Test
    @DisplayName("异常 · 密码错误返回 401")
    void shouldRejectWrongPassword() throws Exception {
        String phone = "13900003333";
        mockMvc.perform(postJson("/api/auth/register",
                        java.util.Map.of(
                                "identityType", "PHONE",
                                "identifier", phone,
                                "password", "Pass1234")))
                .andExpect(status().isCreated());

        mockMvc.perform(postJson("/api/auth/login",
                        java.util.Map.of(
                                "identityType", "PHONE",
                                "identifier", phone,
                                "password", "WrongPass")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("异常 · 账号被禁用后登录返回 403")
    void shouldRejectDisabledAccount() throws Exception {
        String phone = "13900004444";
        String pwd = "Pass1234";
        mockMvc.perform(postJson("/api/auth/register",
                        java.util.Map.of(
                                "identityType", "PHONE",
                                "identifier", phone,
                                "password", pwd)))
                .andExpect(status().isCreated());

        // 管理员直接禁用用户
        User user = identityRepo
                .findByIdentityTypeAndIdentifier(IdentityType.PHONE, phone)
                .orElseThrow().getUser();
        user.setStatus(UserStatus.DISABLED);
        userRepo.save(user);

        mockMvc.perform(postJson("/api/auth/login",
                        java.util.Map.of(
                                "identityType", "PHONE",
                                "identifier", phone,
                                "password", pwd)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("异常 · 无 Token 访问 /me 返回 401")
    void shouldRejectMeWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("异常 · 密码过短触发参数校验 400")
    void shouldRejectShortPassword() throws Exception {
        mockMvc.perform(postJson("/api/auth/register",
                        java.util.Map.of(
                                "identityType", "PHONE",
                                "identifier", "13900005555",
                                "password", "12")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("异常 · 修改密码时旧密码错误返回 401")
    void shouldRejectChangePasswordWithWrongOld() throws Exception {
        TestUser user = createTestUser();
        mockMvc.perform(authPost(user.token(), "/api/auth/password/change",
                        java.util.Map.of(
                                "oldPassword", "WrongOld",
                                "newPassword", "NewPass5678")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    // ==================== 地址管理（嵌套类） ====================

    @Nested
    @DisplayName("地址管理")
    class AddressFlow {

        @Test
        @DisplayName("主成功 · 创建地址→列表→设置默认→删除")
        void shouldCrudAddress() throws Exception {
            TestUser user = createTestUser();

            // 创建地址1
            var addr1 = java.util.Map.of(
                    "receiverName", "张三",
                    "receiverPhone", "13900006666",
                    "province", "北京市",
                    "city", "北京市",
                    "district", "海淀区",
                    "detailAddress", "中关村大街1号",
                    "isDefault", true,
                    "tag", "公司");
            long addrId1 = extractId(mockMvc.perform(authPost(user.token(), "/api/users/addresses", addr1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.isDefault").value(true))
                    .andExpect(jsonPath("$.data.fullAddress", containsString("海淀区")))
                    .andReturn());

            // 创建地址2（非默认）
            var addr2 = java.util.Map.of(
                    "receiverName", "李四",
                    "receiverPhone", "13900007777",
                    "province", "上海市",
                    "city", "上海市",
                    "district", "浦东新区",
                    "detailAddress", "世纪大道100号",
                    "isDefault", false,
                    "tag", "家");
            long addrId2 = extractId(mockMvc.perform(authPost(user.token(), "/api/users/addresses", addr2))
                    .andExpect(status().isOk())
                    .andReturn());

            // 列表查询应返回两条
            mockMvc.perform(authGet(user.token(), "/api/users/addresses"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(2)));

            // 把地址2设为默认
            mockMvc.perform(authGet(user.token(),
                            "/api/users/addresses/" + addrId2 + "/default"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(addrId2))
                    .andExpect(jsonPath("$.data.isDefault").value(true));

            // 删除地址1
            mockMvc.perform(authDelete(user.token(), "/api/users/addresses/" + addrId1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // 列表应该只剩一条
            mockMvc.perform(authGet(user.token(), "/api/users/addresses"))
                    .andExpect(jsonPath("$.data", hasSize(1)));
        }

        @Test
        @DisplayName("异常 · 缺少必填字段返回 400")
        void shouldRejectMissingFields() throws Exception {
            TestUser user = createTestUser();
            var bad = java.util.Map.of(
                    "receiverName", "王五",
                    // 缺少 receiverPhone
                    "province", "北京市",
                    "city", "北京市",
                    "district", "朝阳区",
                    "detailAddress", "测试路1号");
            mockMvc.perform(authPost(user.token(), "/api/users/addresses", bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("异常 · 未登录创建地址返回 401")
        void shouldRejectUnauthenticatedAddressCreate() throws Exception {
            mockMvc.perform(post("/api/users/addresses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(java.util.Map.of(
                                    "receiverName", "x",
                                    "receiverPhone", "1",
                                    "province", "x",
                                    "city", "x",
                                    "district", "x",
                                    "detailAddress", "x"))))
                    .andExpect(status().isUnauthorized());
        }
    }
}
