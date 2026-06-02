# 认证模块设计文档

## 1. 概述

认证模块（Auth）负责用户身份管理，包括注册、登录、密码修改和会话管理。采用 **JWT（JSON Web Token）无状态认证** 方案，这是目前 RESTful API 领域最主流的认证方式。

## 2. 认证流程

### 2.1 注册流程

```
用户端                  后端
  │                      │
  │  POST /api/auth/register
  │  { identityType: "PHONE"|"EMAIL",
  │    identifier: "138...",
  │    password: "123456" }
  │ ─────────────────────→
  │                      │ 1. 参数校验 (@Valid)
  │                      │ 2. 标识符归一化（邮箱→小写）
  │                      │ 3. 检查是否已注册
  │                      │ 4. BCrypt 加密密码
  │                      │ 5. 创建 User + UserIdentity
  │                      │ 6. 生成 JWT Token
  │                      │ 7. 返回 { accessToken, userId, nickname }
  │  ←─────────────────────
  │  存储 token 到 localStorage
  │  跳转首页
```

### 2.2 登录流程

```
用户端                  后端
  │                      │
  │  POST /api/auth/login
  │  { identityType, identifier, password }
  │ ─────────────────────→
  │                      │ 1. 查找 UserIdentity
  │                      │ 2. BCrypt 验证密码
  │                      │ 3. 检查账号状态 (ACTIVE/DISABLED)
  │                      │ 4. 生成 JWT Token
  │                      │ 5. 返回 { accessToken, userId, nickname }
  │  ←─────────────────────
```

### 2.3 认证请求流程

```
用户端                         后端
  │                              │
  │  GET /api/products (需认证)
  │  Authorization: Bearer <jwt>
  │ ──────────────────────────→
  │                              │ JwtAuthFilter:
  │                              │ 1. 提取 Bearer token
  │                              │ 2. 验证签名 + 过期时间
  │                              │ 3. 解析 userId
  │                              │ 4. 注入 SecurityContext
  │                              │
  │                              │ Controller:
  │                              │ @AuthenticationPrincipal AuthPrincipal
  │                              │ → 获取当前用户 ID
  │                              │ → 执行业务逻辑
  │  ←─────────────────────────
```

### 2.4 修改密码流程

```
用户端 (已登录)              后端
  │                              │
  │  POST /api/auth/password/change
  │  Authorization: Bearer <jwt>
  │  { oldPassword, newPassword }
  │ ──────────────────────────→
  │                              │ 1. JwtAuthFilter 解析 userId
  │                              │ 2. BCrypt 验证旧密码
  │                              │ 3. BCrypt 加密新密码 → 更新
  │  ←─ 200 OK ──────────────
```

---

## 3. 数据模型

### 3.1 User（用户）

```
┌──────────────────────┐
│        users          │
├──────────────────────┤
│ id          BIGINT PK│  自增主键
│ nickname    VARCHAR   │  昵称（默认"新用户"）
│ password_hash VARCHAR │  BCrypt 加密后的密码
│ status      ENUM      │  ACTIVE | DISABLED
│ created_at  TIMESTAMP │
│ updated_at  TIMESTAMP │
└──────────────────────┘
```

### 3.2 UserIdentity（登录标识）

```
┌──────────────────────────┐
│    user_identities        │
├──────────────────────────┤
│ id              BIGINT PK│
│ user_id         BIGINT FK│ → users.id
│ identity_type   ENUM     │  PHONE | EMAIL
│ identifier      VARCHAR  │  手机号或邮箱
│ verified        BOOLEAN  │  是否已验证（预留）
│ created_at      TIMESTAMP│
│ updated_at      TIMESTAMP│
│                          │
│ UNIQUE(identity_type,    │
│        identifier)       │  同一标识只能注册一次
└──────────────────────────┘
```

### 3.3 实体关系

```
User (1) ──── (N) UserIdentity

一个用户可以绑定多个登录方式：
  例：用户 ID=1 同时有
    - PHONE: 13800138000
    - EMAIL: user@example.com
```

---

## 4. JWT 设计

### 4.1 Token 结构

| 字段 | 说明 |
|------|------|
| **签名算法** | HMAC-SHA384（jjwt 库根据密钥长度自动选择） |
| **sub (Subject)** | 用户 ID 字符串 |
| **iat (Issued At)** | 签发时间 |
| **exp (Expiration)** | 过期时间（默认 120 分钟） |

### 4.2 Token 生命周期

```
┌──────────┐      ┌───────────┐
│ 登录/注册 │ ───→ │ JWT Token  │  有效期 120 分钟
└──────────┘      └─────┬─────┘
                         │
                  每次 API 请求携带
                  Authorization: Bearer <token>
                         │
              ┌──────────▼──────────┐
              │  Token 过期？         │
              │  是 → 前端清除 token  │
              │       → 跳转 /login   │
              │  否 → 正常处理        │
              └─────────────────────┘
```

### 4.3 前端 Token 管理

```javascript
// 存储 (Login.vue)
localStorage.setItem('token', user.accessToken)
localStorage.setItem('userId', String(user.userId))
localStorage.setItem('nickname', user.nickname)

// 发送 (api.js)
headers['Authorization'] = `Bearer ${token}`

// 清除 (App.vue → logout)
localStorage.removeItem('token')
localStorage.removeItem('userId')
localStorage.removeItem('nickname')
```

---

## 5. 安全设计要点

### 5.1 密码安全

- **BCrypt** 加密：自适应哈希算法，内置 salt，抗彩虹表
- **存储**：密码哈希存储在 `users.password_hash`，JSON 序列化时被 `@JsonIgnore` 排除
- **传输**：前端发送明文密码（通过 HTTPS 在生产环境保护）
- **最小长度**：6 位（`@Size(min = 6)`）

### 5.2 防攻击措施

| 威胁 | 防护措施 |
|------|----------|
| 暴力破解 | BCrypt 计算成本高，天然降速；可在网关层加频率限制 |
| Token 泄露 | Token 有效期 2 小时，过期自动失效 |
| CSRF | REST API 使用无状态 JWT，天然免疫 CSRF |
| XSS | Token 存 localStorage，非 httpOnly cookie；前端需做输入过滤 |
| 账号枚举 | 注册/登录返回相同错误信息（"账号或密码错误"），不泄露是否存在该账号 |

### 5.3 认证标识归一化

```java
// AuthService.normalize()
EMAIL: "User@Example.COM" → "user@example.com"  // 转小写
PHONE: " 13800138000 "    → "13800138000"       // 去空白
```

防止同一邮箱因为大小写差异而重复注册。

---

## 6. API 接口规格

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/auth/register` | 否 | 注册新用户 |
| POST | `/api/auth/login` | 否 | 用户登录 |
| GET | `/api/auth/me` | 是 | 获取当前用户信息 |
| POST | `/api/auth/password/change` | 是 | 修改密码 |

### 6.1 注册请求/响应示例

```json
// 请求
POST /api/auth/register
Content-Type: application/json

{
  "identityType": "EMAIL",
  "identifier": "test@example.com",
  "password": "123456"
}

// 成功响应 (201 Created)
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
    "userId": 1,
    "nickname": "新用户"
  }
}

// 失败响应 (409 Conflict)
{
  "success": false,
  "error": {
    "code": "IDENTITY_EXISTS",
    "message": "该邮箱已注册"
  }
}

// 校验失败 (400 Bad Request)
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "password: 密码长度需在 6-64 之间"
  }
}
```

---

## 7. 扩展考虑

1. **刷新令牌（Refresh Token）**：增加长期有效的 refresh token，用于无感刷新 access token
2. **手机验证码登录**：新增 `IdentityType.SMS_CODE`，配合短信服务
3. **OAuth2 第三方登录**：集成微信/支付宝/Google 登录
4. **多设备登录管理**：Redis 记录 token → 设备映射，支持远端踢下线
5. **密码强度策略**：前端 + 后端双重校验密码复杂度
6. **登录失败锁定**：连续失败 N 次锁定账号 M 分钟
