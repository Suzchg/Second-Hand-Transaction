# API 接口参考

## 基础信息

- **Base URL**: `http://localhost:8088`
- **Content-Type**: `application/json`
- **认证方式**: `Authorization: Bearer <jwt_token>`
- **响应格式**: `ApiResponse<T>`

### 统一响应结构

```typescript
// 成功
{ "success": true, "data": T }

// 失败
{ "success": false, "error": { "code": "ERROR_CODE", "message": "人类可读的消息" } }
```

---

## 1. 认证模块 `/api/auth`

### 1.1 注册

```
POST /api/auth/register
```

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| identityType | string | ✅ | `PHONE` 或 `EMAIL` |
| identifier | string | ✅ | 手机号或邮箱地址 |
| password | string | ✅ | 密码，6–64 位 |

**成功响应** `201 Created`

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "userId": 1,
    "nickname": "新用户"
  }
}
```

**错误码**

| code | HTTP | 说明 |
|------|------|------|
| `VALIDATION_ERROR` | 400 | 参数校验失败 |
| `IDENTITY_EXISTS` | 409 | 该手机号/邮箱已注册 |

---

### 1.2 登录

```
POST /api/auth/login
```

请求体格式与注册相同。

**成功响应** `200 OK`

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "userId": 1,
    "nickname": "张三"
  }
}
```

**错误码**

| code | HTTP | 说明 |
|------|------|------|
| `INVALID_CREDENTIALS` | 401 | 账号或密码错误 |
| `FORBIDDEN` | 403 | 账号已被禁用 |
| `VALIDATION_ERROR` | 400 | 参数校验失败 |

---

### 1.3 获取当前用户信息

```
GET /api/auth/me
Authorization: Bearer <token>
```

**成功响应** `200 OK`

```json
{
  "success": true,
  "data": {
    "accessToken": null,
    "userId": 1,
    "nickname": "张三"
  }
}
```

> `accessToken` 在此接口返回 `null`，仅用于获取用户基本信息。

---

### 1.4 修改密码

```
POST /api/auth/password/change
Authorization: Bearer <token>
```

**请求体**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| oldPassword | string | ✅ | 旧密码 |
| newPassword | string | ✅ | 新密码，6–64 位 |

**成功响应** `200 OK`

```json
{ "success": true, "data": null }
```

---

## 2. 商品模块 `/api/products`

### 2.1 创建商品

```
POST /api/products
Authorization: Bearer <token>
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | string | ✅ | 标题，≤100 字符 |
| priceCent | integer | ✅ | 价格（分），如 1999 表示 ¥19.99 |
| coverImageUrl | string | ✅ | 封面图 URL |
| description | string | ✅ | 商品描述 |

### 2.2 获取商品列表

```
GET /api/products?page=0&size=10
```

无需认证。返回 Spring Data 的 `Page<Product>` 分页对象。

### 2.3 获取商品详情

```
GET /api/products/{id}
```

### 2.4 编辑商品

```
PUT /api/products/{id}
Authorization: Bearer <token>
```

仅卖家可编辑自己的商品。所有字段可选。

---

## 3. 订单模块 `/api/orders`

### 3.1 创建订单

```
POST /api/orders
Authorization: Bearer <token>
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| productId | long | ✅ | 商品 ID |
| receiverName | string | ✅ | 收货人姓名 |
| receiverPhone | string | ✅ | 收货手机号 |
| receiverAddress | string | ✅ | 收货地址 |

### 3.2 支付占位

```
POST /api/orders/{id}/mark-paid
Authorization: Bearer <token>
```

将订单状态从 `CREATED` → `PAID`。

### 3.3 发货

```
POST /api/orders/{id}/ship
Authorization: Bearer <token>
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| carrierCode | string | ✅ | 快递公司编码 |
| trackingNo | string | ✅ | 快递单号 |

### 3.4 订单详情

```
GET /api/orders/{id}
Authorization: Bearer <token>
```

返回 `{ order, shipment, events[] }`。

---

## 4. 售后模块 `/api/after-sale`

### 4.1 发起售后

```
POST /api/after-sale
Authorization: Bearer <token>
```

| 字段 | 类型 | 说明 |
|------|------|------|
| orderId | long | 订单 ID |
| type | enum | `REFUND` 或 `RETURN_REFUND` |
| reason | string | 申请原因 |

### 4.2 通过售后

```
POST /api/after-sale/{id}/approve
Authorization: Bearer <token>
```

### 4.3 拒绝售后

```
POST /api/after-sale/{id}/reject
Authorization: Bearer <token>

{ "note": "理由" }
```

### 4.4 查看售后

```
GET /api/after-sale/{id}
Authorization: Bearer <token>
```

---

## 5. 物流模块 `/api/shipments`

### 5.1 查询物流轨迹

```
GET /api/shipments/{orderId}/track
```

无需认证。返回 Mock 数据。

---

## 6. 其他端点

| 路径 | 说明 |
|------|------|
| `/swagger` | Swagger UI |
| `/h2-console` | H2 数据库控制台 |
| `/login`, `/sell`, `/products/**`, `/orders/**` | SPA 前端路由（转发到 index.html） |

---

## 7. 通用错误码

| code | HTTP | 说明 |
|------|------|------|
| `VALIDATION_ERROR` | 400 | 请求参数校验失败 |
| `BAD_REQUEST` | 400 | 请求格式错误 |
| `INVALID_CREDENTIALS` | 401 | 认证凭据无效 |
| `FORBIDDEN` | 403 | 权限不足 |
| `NOT_FOUND` | 404 | 资源不存在 |
| `CONFLICT` | 409 | 业务冲突（如重复注册、状态不允许） |
| `IDENTITY_EXISTS` | 409 | 注册标识已存在 |
| `INTERNAL_ERROR` | 500 | 服务器内部错误 |
