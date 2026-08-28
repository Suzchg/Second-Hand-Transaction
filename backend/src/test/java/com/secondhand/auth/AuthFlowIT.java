package com.secondhand.auth;

import com.secondhand.testutil.ApiIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;

/** 场景 1：注册、登录与认证权限的 API 和数据库一致性测试。 */
class AuthFlowIT extends ApiIntegrationTestBase {
    @Autowired PasswordEncoder passwords;

    @Test
    void registerPersistsIdentityAndHashedPasswordThenLoginAuthorizesApi() throws Exception {
        // 1.1：验证注册身份、密码哈希落库，以及登录令牌可访问当前用户接口。
        Actor u = user();
        assertThat(count("select count(*) from user_identities where user_id=? and identifier=?", u.id(), u.identifier())).isEqualTo(1);
        String hash = jdbc.queryForObject("select password_hash from users where id=?", String.class, u.id());
        assertThat(hash).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, hash)).isTrue();
        Actor loggedIn = actor(ok("POST", "/api/auth/login", credentials("PHONE", u.identifier(), PASSWORD), null), u.identifier());
        assertThat(loggedIn.id()).isEqualTo(u.id());
        assertThat(ok("GET", "/api/auth/me", null, loggedIn).path("userId").asLong()).isEqualTo(u.id());
    }

    @Test
    void emailAlternativeNormalizesAndRejectsDuplicateWithoutOrphanUser() throws Exception {
        // 1.2：验证邮箱归一化和重复注册回滚，避免产生孤立用户。
        String email = "IT" + nextPhone() + "@Example.COM";
        Actor u = actor(success(call("POST", "/api/auth/register", credentials("EMAIL", " " + email + " ", PASSWORD), null), 201), email);
        assertThat(jdbc.queryForObject("select email from users where id=?", String.class, u.id())).isEqualTo(email.toLowerCase());
        assertThat(ok("POST", "/api/auth/login", credentials("EMAIL", email.toLowerCase(), PASSWORD), null).path("userId").asLong()).isEqualTo(u.id());
        long users = count("select count(*) from users"), identities = count("select count(*) from user_identities");
        error("POST", "/api/auth/register", credentials("EMAIL", email.toLowerCase(), PASSWORD), null, 409, "IDENTITY_EXISTS");
        assertThat(count("select count(*) from users")).isEqualTo(users);
        assertThat(count("select count(*) from user_identities")).isEqualTo(identities);
    }

    @Test
    void duplicatePhoneDoesNotCreateExtraUserOrIdentity() throws Exception {
        // 1.3：重复手机号注册应被拒绝，用户表和身份表都不能新增记录。
        Actor u = user();
        long users = count("select count(*) from users"), identities = count("select count(*) from user_identities");
        error("POST", "/api/auth/register", credentials("PHONE", u.identifier(), PASSWORD), null, 409, "IDENTITY_EXISTS");
        assertThat(count("select count(*) from users")).isEqualTo(users);
        assertThat(count("select count(*) from user_identities")).isEqualTo(identities);
    }

    @ParameterizedTest
    @CsvSource({"PHONE,123", "INVALID,valid-password"})
    void invalidRegistrationDoesNotWriteDatabase(String type, String password) throws Exception {
        // 1.4～1.5：分别验证短密码、非法身份类型在参数校验阶段被拦截。
        String phone = nextPhone(); long users = count("select count(*) from users");
        error("POST", "/api/auth/register", credentials(type, phone, password), null, 400, "VALIDATION_ERROR");
        assertThat(count("select count(*) from users")).isEqualTo(users);
        assertThat(count("select count(*) from user_identities where identifier=?", phone)).isZero();
    }

    @Test
    void wrongPasswordAndUnknownIdentityCannotLogin() throws Exception {
        // 1.6：错误密码和未知账号都不能登录，也不能改变现有账号状态。
        Actor u = user();
        error("POST", "/api/auth/login", credentials("PHONE", u.identifier(), "wrong-password"), null, 401, "INVALID_CREDENTIALS");
        error("POST", "/api/auth/login", credentials("PHONE", nextPhone(), PASSWORD), null, 401, "INVALID_CREDENTIALS");
        assertThat(count("select count(*) from users where id=? and status='ACTIVE'", u.id())).isEqualTo(1);
    }

    @Test
    void adminDisableAndEnableControlsLogin() throws Exception {
        // 1.7：验证管理员禁用、重新启用账号后，数据库状态与登录结果一致。
        Actor u = user(), admin = admin();
        ok("PUT", "/api/admin/users/" + u.id() + "/disable?disabled=true", null, admin);
        assertThat(count("select count(*) from users where id=? and status='DISABLED'", u.id())).isEqualTo(1);
        error("POST", "/api/auth/login", credentials("PHONE", u.identifier(), PASSWORD), null, 403, "FORBIDDEN");
        ok("PUT", "/api/admin/users/" + u.id() + "/disable?disabled=false", null, admin);
        assertThat(ok("POST", "/api/auth/login", credentials("PHONE", u.identifier(), PASSWORD), null).path("userId").asLong()).isEqualTo(u.id());
    }

    @Test
    void anonymousAndInvalidJwtCannotCreateProduct() throws Exception {
        // 1.8：验证匿名和非法 JWT 请求被安全过滤器拒绝，商品表无副作用。
        long products = count("select count(*) from products");
        assertThat(call("POST", "/api/products", productBody(1), null).getResponse().getStatus()).isEqualTo(403);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/products")
                .contentType("application/json").header("Authorization", "Bearer invalid-token")
                .content(mapper.writeValueAsBytes(productBody(1))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        assertThat(count("select count(*) from products")).isEqualTo(products);
    }
}
