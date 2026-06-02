package com.secondhand.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 登录请求 DTO。
 */
public record LoginRequest(
        @NotBlank(message = "身份类型不能为空")
        @Pattern(regexp = "PHONE|EMAIL", message = "身份类型必须是 PHONE 或 EMAIL")
        String identityType,

        @NotBlank(message = "手机号或邮箱不能为空")
        @Size(max = 128, message = "标识过长")
        String identifier,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间")
        String password
) {}
