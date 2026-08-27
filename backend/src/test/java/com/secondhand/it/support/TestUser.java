package com.secondhand.it.support;

/** 已注册测试用户（含 userId、accessToken、手机号、密码） */
public record TestUser(long userId, String token, String phone, String password) {
}
