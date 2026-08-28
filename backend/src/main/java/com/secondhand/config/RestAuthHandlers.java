package com.secondhand.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * REST 安全异常处理器。
 *
 * 背景：Spring Security 默认使用 Http403ForbiddenEntryPoint，
 * 未认证访问受保护接口会得到无响应体的 403，无法与"已认证但权限不足"区分。
 *
 * 本处理器将两类安全异常转换为统一 ApiResponse JSON 格式：
 * - 未认证（无 token / token 无效）→ 401 UNAUTHORIZED
 * - 已认证但权限不足（角色不够）→ 403 FORBIDDEN
 *
 * 响应体格式与 ApiResponse.fail / GlobalExceptionHandler 保持一致，
 * 写法参考 JwtAuthFilter 黑名单分支。
 */
public class RestAuthHandlers {

    private RestAuthHandlers() {
    }

    /** 未认证（无 token / token 无效）→ HTTP 401 */
    @Component
    public static class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response,
                             AuthenticationException authException) throws IOException {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"success\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"未认证或登录已过期\"}}");
        }
    }

    /** 已认证但权限不足（如普通用户访问管理员接口）→ HTTP 403 */
    @Component
    public static class RestAccessDeniedHandler implements AccessDeniedHandler {

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                           AccessDeniedException accessDeniedException) throws IOException {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"success\":false,\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"权限不足\"}}");
        }
    }
}
