package com.secondhand.it;

import com.secondhand.auth.entity.IdentityType;
import com.secondhand.it.support.AbstractIntegrationTest;
import com.secondhand.it.support.TestUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用例 1：用户注册与登录
 *
 * 覆盖流程：
 * - 主成功：新手机号注册获得凭证并持久化 → 正确密码登录成功
 * - 备选  ：邮箱注册（大小写归一化）、修改密码后以新密码登录
 * - 异常  ：重复注册（409）、密码过短（400）、错误密码（401）、
 *           账号不存在（401）、账号被禁用（403）、未携带 token（401）、
 *           无 token 访问 /me（401，已修复：原为 500 NPE）
 *
 * 验证层次：HTTP API（AuthController）→ 业务（AuthService）→ 数据库（User/UserIdentity 持久化）
 * 模块间调用：AdminUserController 禁用账号 → 影响 AuthService 登录校验
 */
@DisplayName("用例1：用户注册与登录")
class Uc1AuthIT extends AbstractIntegrationTest {

    @Nested
    @DisplayName("主成功流程")
    class MainFlow {

        @Test
        @DisplayName("注册：新手机号返回201与token，身份与用户落库")
        void registerPersistsIdentityAndUser() throws Exception {
            TestUser user = registerUser();

            // 数据库层断言：UserIdentity + User 已持久化
            var identity = identityRepo
                    .findByIdentityTypeAndIdentifier(IdentityType.PHONE, user.phone())
                    .orElseThrow(() -> new AssertionError("注册身份未持久化到数据库"));
            var dbUser = userRepo.findById(user.userId())
                    .orElseThrow(() -> new AssertionError("注册用户未持久化到数据库"));

            // 密码必须 BCrypt 加密存储（非明文）
            org.junit.jupiter.api.Assertions.assertTrue(
                    dbUser.getPasswordHash().startsWith("$2"),
                    "密码应以 BCrypt 哈希存储");
            org.junit.jupiter.api.Assertions.assertEquals(
                    identity.getUser().getId(), dbUser.getId());
            org.junit.jupiter.api.Assertions.assertEquals("USER", dbUser.getRole().name());
        }

        @Test
        @DisplayName("注册响应：201 + accessToken 非空 + role=USER")
        void registerResponseShape() throws Exception {
            String phone = uniquePhone();
            doPost("/api/auth/register", null, """
                    {"identityType":"PHONE","identifier":"%s","password":"%s"}
                    """.formatted(phone, DEFAULT_PASSWORD))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                    .andExpect(jsonPath("$.data.userId").isNumber())
                    .andExpect(jsonPath("$.data.role").value("USER"));
        }

        @Test
        @DisplayName("登录：已注册手机号+正确密码返回token，/me 可获取当前用户")
        void loginThenFetchMe() throws Exception {
            TestUser user = registerUser();

            String token = loginToken(user.phone(), user.password());
            org.junit.jupiter.api.Assertions.assertFalse(token.isBlank(), "登录应返回 accessToken");

            // token 有效性验证：携带 token 访问受保护接口 /api/auth/me
            doGet("/api/auth/me", token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.userId").value(user.userId()));
        }
    }

    @Nested
    @DisplayName("备选流程")
    class AlternateFlow {

        @Test
        @DisplayName("邮箱注册：大写邮箱注册后小写邮箱可登录（标识归一化）")
        void emailRegisterNormalizedLogin() throws Exception {
            String emailPrefix = "it.user." + System.nanoTime();
            // 大写邮箱注册
            doPost("/api/auth/register", null, """
                    {"identityType":"EMAIL","identifier":"%s@Test.COM","password":"%s"}
                    """.formatted(emailPrefix, DEFAULT_PASSWORD))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));

            // 归一化后（小写）的邮箱能正常登录
            doPost("/api/auth/login", null, """
                    {"identityType":"EMAIL","identifier":"%s@test.com","password":"%s"}
                    """.formatted(emailPrefix, DEFAULT_PASSWORD))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())));
        }

        @Test
        @DisplayName("修改密码：旧密码验证后新密码生效，旧密码失效")
        void changePasswordFlow() throws Exception {
            TestUser user = registerUser();

            // 修改密码（需认证 + 旧密码校验）
            doPost("/api/auth/password/change", user.token(), """
                    {"oldPassword":"%s","newPassword":"new-pass-654321"}
                    """.formatted(user.password()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // 旧密码登录失败
            doPost("/api/auth/login", null, """
                    {"identityType":"PHONE","identifier":"%s","password":"%s"}
                    """.formatted(user.phone(), user.password()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

            // 新密码登录成功
            doPost("/api/auth/login", null, """
                    {"identityType":"PHONE","identifier":"%s","password":"new-pass-654321"}
                    """.formatted(user.phone()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())));
        }
    }

    @Nested
    @DisplayName("异常流程")
    class ExceptionFlow {

        @Test
        @DisplayName("重复注册：同一手机号二次注册返回409 IDENTITY_EXISTS")
        void duplicateRegisterReturnsConflict() throws Exception {
            String phone = uniquePhone();
            String body = """
                    {"identityType":"PHONE","identifier":"%s","password":"%s"}
                    """.formatted(phone, DEFAULT_PASSWORD);

            doPost("/api/auth/register", null, body)
                    .andExpect(status().isCreated());

            doPost("/api/auth/register", null, body)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("IDENTITY_EXISTS"));
        }

        @Test
        @DisplayName("注册校验失败：密码长度不足6位返回400 VALIDATION_ERROR")
        void shortPasswordReturnsValidation() throws Exception {
            doPost("/api/auth/register", null, """
                    {"identityType":"PHONE","identifier":"%s","password":"123"}
                    """.formatted(uniquePhone()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("登录失败：错误密码返回401 INVALID_CREDENTIALS")
        void wrongPasswordReturnsUnauthorized() throws Exception {
            TestUser user = registerUser();

            doPost("/api/auth/login", null, """
                    {"identityType":"PHONE","identifier":"%s","password":"totally-wrong"}
                    """.formatted(user.phone()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("登录失败：未注册账号返回401")
        void unknownAccountReturnsUnauthorized() throws Exception {
            doPost("/api/auth/login", null, """
                    {"identityType":"PHONE","identifier":"%s","password":"whatever-123"}
                    """.formatted(uniquePhone()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("账号禁用：管理员禁用后登录返回403 FORBIDDEN（模块间调用）")
        void disabledAccountLoginForbidden() throws Exception {
            TestUser user = registerUser();

            // 管理员通过管理模块禁用该用户（AdminUserController → UserRepository）
            doPut("/api/admin/users/%d/disable?disabled=true".formatted(user.userId()),
                    adminToken(), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DISABLED"));

            // 数据库状态已变更
            org.junit.jupiter.api.Assertions.assertEquals("DISABLED",
                    userRepo.findById(user.userId()).orElseThrow().getStatus().name());

            // 禁用后登录被拒
            doPost("/api/auth/login", null, """
                    {"identityType":"PHONE","identifier":"%s","password":"%s"}
                    """.formatted(user.phone(), user.password()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("认证缺失：未携带token访问受保护接口返回401（已修复：补充了AuthenticationEntryPoint）")
        void protectedEndpointWithoutTokenRejected() throws Exception {
            // /api/orders/bought 需要认证；未携带 token 被安全链拒绝，
            // 已配置 RestAuthenticationEntryPoint，返回 401 + 统一 JSON 错误体（原为无 body 的 403）
            doGet("/api/orders/bought", null)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("获取当前用户未认证：无token访问/api/auth/me返回401（已修复：原为500 NPE）")
        void meWithoutTokenReturnsUnauthorized() throws Exception {
            // /api/auth/me 不再随 /api/auth/** 整体放行，落入 authenticated()；
            // 修复前：permitAll 放行后 @AuthenticationPrincipal 注入 null → NPE → 500
            doGet("/api/auth/me", null)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
    }
}
