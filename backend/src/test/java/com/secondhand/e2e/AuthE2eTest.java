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
 * 用例 1：用户注册与登录。
 */
@DisplayName("场景 1：用户注册与登录")
class AuthE2eTest extends E2eTestBase {

    @Test
    @DisplayName("注册成功返回身份凭证（JWT）")
    void registerSuccess() {
        String phone = nextPhone();
        ResponseEntity<String> resp = post("/api/auth/register",
                Map.of("identityType", "PHONE", "identifier", phone, "password", "pass123456"), null);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode(), "注册应返回 201");
        JsonNode data = data(resp);
        assertTrue(data.get("accessToken").asText().length() > 10, "注册成功应返回 JWT");
        assertEquals("USER", data.get("role").asText(), "普通注册用户角色应为 USER");
    }

    @Test
    @DisplayName("重复注册应返回 409 IDENTITY_EXISTS")
    void registerDuplicate() {
        String phone = nextPhone();
        post("/api/auth/register",
                Map.of("identityType", "PHONE", "identifier", phone, "password", "pass123456"), null);
        ResponseEntity<String> resp = post("/api/auth/register",
                Map.of("identityType", "PHONE", "identifier", phone, "password", "pass123456"), null);

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode(), "重复注册应返回 409");
        assertEquals("IDENTITY_EXISTS", errorCode(resp));
    }

    @Test
    @DisplayName("注册校验失败应返回 400 VALIDATION_ERROR")
    void registerValidationFails() {
        ResponseEntity<String> resp = post("/api/auth/register",
                Map.of("identityType", "PHONE", "identifier", "123", "password", "1"), null);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode(), "非法参数应返回 400");
        assertEquals("VALIDATION_ERROR", errorCode(resp));
    }

    @Test
    @DisplayName("密码错误登录应返回 401 INVALID_CREDENTIALS")
    void loginWrongPassword() {
        String phone = nextPhone();
        post("/api/auth/register",
                Map.of("identityType", "PHONE", "identifier", phone, "password", "pass123456"), null);

        ResponseEntity<String> resp = post("/api/auth/login",
                Map.of("identityType", "PHONE", "identifier", phone, "password", "wrongpass"), null);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode(), "密码错误应返回 401");
        assertEquals("INVALID_CREDENTIALS", errorCode(resp));
    }

    @Test
    @DisplayName("账号禁用后登录应返回 403 FORBIDDEN")
    void loginDisabledAccount() {
        String phone = nextPhone();
        JsonNode reg = data(post("/api/auth/register",
                Map.of("identityType", "PHONE", "identifier", phone, "password", "pass123456"), null));
        long uid = reg.get("userId").asLong();

        // 管理员禁用该账号
        ResponseEntity<String> dis = put("/api/admin/users/" + uid + "/disable?disabled=true", null, adminToken());
        assertEquals(HttpStatus.OK, dis.getStatusCode(), "管理员禁用应成功");

        ResponseEntity<String> resp = post("/api/auth/login",
                Map.of("identityType", "PHONE", "identifier", phone, "password", "pass123456"), null);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(), "禁用账号登录应返回 403");
        assertEquals("FORBIDDEN", errorCode(resp));
    }
}
