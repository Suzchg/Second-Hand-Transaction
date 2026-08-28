# 二手交易平台后端端到端测试用例与报告

> 测试范围：后端 7 大核心业务场景的完整业务流程（端到端）
> 测试方式：启动完整 Spring Boot 上下文（随机端口），通过真实 HTTP 链路（Spring Security → Controller → Service → Repository → MySQL）逐接口调用并断言
> 测试库：独立 MySQL 测试库 `secondhand_test`（不污染开发库 `secondhand`）
> 说明：只新增测试代码，未修改任何前后端业务代码。

---

## 一、测试环境

| 项 | 值 |
|---|---|
| JDK | 24.0.2（`D:\JAVA\IDK24`） |
| Spring Boot | 3.3.2 |
| 数据库 | MySQL 9.7（`localhost:3306`），测试库 `secondhand_test` |
| 测试框架 | JUnit 5 + `@SpringBootTest(RANDOM_PORT)` + JDK HttpClient |
| 测试代码位置 | `backend/src/test/java/com/secondhand/e2e/BackendE2eTest.java` |
| 测试配置 | `backend/src/test/resources/application-e2e.yml`（`@ActiveProfiles("e2e")`） |

### 运行方式

```bash
cd backend
# 若 JDK 不在 PATH，先设置 JAVA_HOME=D:\JAVA\IDK24
./mvnw.cmd -Dtest=BackendE2eTest test
```

### 通用约定

- 统一响应体：`{ "success": true/false, "data": {...}, "error": { "code": "...", "message": "..." } }`
- 认证：需要登录的接口在请求头携带 `Authorization: Bearer <accessToken>`
- 管理员账号：系统启动自动创建 `13800000000` / `admin123`
- 每个用例注册全新用户（唯一手机号），互不干扰、可重复执行
- 每个请求带独立 `X-Forwarded-For` 头，规避注册/登录接口的 IP 限流

---

## 二、测试用例明细

### 场景 1：用户注册与登录

| 编号 | 用例名称 | 测试步骤（HTTP 调用） | 预期结果 |
|---|---|---|---|
| 1.1 | 注册成功获得身份凭证 | `POST /api/auth/register`（identityType=PHONE、identifier=新手机号、password） | HTTP 201，`data.accessToken` 非空，`data.role=USER` |
| 1.2 | 重复注册 | 先注册成功，再用同一手机号 `POST /api/auth/register` | HTTP 409，`error.code=IDENTITY_EXISTS` |
| 1.3 | 注册校验失败 | `POST /api/auth/register`（password 长度 < 6） | HTTP 400，`error.code=VALIDATION_ERROR` |
| 1.4 | 密码错误登录 | 注册成功后 `POST /api/auth/login`（错误密码） | HTTP 401，`error.code=INVALID_CREDENTIALS` |
| 1.5 | 账号禁用后登录 | 注册 → 管理员 `PUT /api/admin/users/{id}/disable?disabled=true` → 再登录 | 禁用接口 HTTP 200；再登录 HTTP 403，`error.code=FORBIDDEN` |

### 场景 2：商品发布与编辑

| 编号 | 用例名称 | 测试步骤 | 预期结果 |
|---|---|---|---|
| 2.1 | 发布商品成功 | 卖家 `POST /api/products`（title/priceCent/description…） | HTTP 200，`data.status=ON_SALE`，返回商品 id |
| 2.2 | 发布校验失败 | `POST /api/products`（缺 description） | HTTP 400，`error.code=VALIDATION_ERROR` |
| 2.3 | 编辑他人商品（权限不足） | 卖家A 发布 → 卖家B `PUT /api/products/{id}` | HTTP 403，`error.code=FORBIDDEN` |
| 2.4 | 编辑自己的商品 | 卖家 `PUT /api/products/{id}`（改标题、改价） | HTTP 200，标题与价格更新生效 |

### 场景 3：商品购买（下单 + 支付）

| 编号 | 用例名称 | 测试步骤 | 预期结果 |
|---|---|---|---|
| 3.1 | 下单并支付成功 | 卖家发布 → 买家 `POST /api/orders` → 买家 `POST /api/orders/{id}/pay` | 下单返回 `status=WAIT_PAY`；支付返回 `status=WAIT_DELIVER` |
| 3.2 | 购买自己的商品 | 卖家对自有商品 `POST /api/orders` | HTTP 403，`error.code=FORBIDDEN` |
| 3.3 | 重复支付（状态不符） | 支付成功后再次 `POST /api/orders/{id}/pay` | HTTP 409，`error.code=CONFLICT` |

### 场景 4：卖家发货

| 编号 | 用例名称 | 测试步骤 | 预期结果 |
|---|---|---|---|
| 4.1 | 发货成功 | 下单 → 支付 → 卖家 `POST /api/orders/{id}/ship`（carrierCode/trackingNo） | HTTP 200，返回运单（trackingNo 一致）；订单进入 `WAIT_RECEIVE` |
| 4.2 | 未支付发货（状态不符） | 下单后（未支付）直接 `POST /api/orders/{id}/ship` | HTTP 409，`error.code=CONFLICT` |

### 场景 5：出价议价（出价 + 接受/拒绝/撤销）

| 编号 | 用例名称 | 测试步骤 | 预期结果 |
|---|---|---|---|
| 5.1 | 出价 + 接受，按议价生成订单 | 买家 `POST /api/products/{id}/offers`（offeredPriceCent）→ 卖家 `POST /api/offers/{id}/accept` → 买家补填收货信息 → 支付 | 出价 `PENDING`；接受后生成订单 `amountCent=议价金额`、`WAIT_PAY`；补填后支付 → `WAIT_DELIVER` |
| 5.2 | 卖家拒绝报价 | 买家出价 → 卖家 `POST /api/offers/{id}/reject` | 报价状态 `REJECTED` |
| 5.3 | 买家撤销报价 | 买家出价 → 买家 `POST /api/offers/{id}/cancel` | 报价状态 `CANCELLED` |

### 场景 6：售后处理（申请 + 审批 + 仲裁）

> 前置：完整走通「下单→支付→发货→确认收货」，订单进入 `COMPLETED`（售后仅确认收货后 7 天内可发起）。

| 编号 | 用例名称 | 测试步骤 | 预期结果 |
|---|---|---|---|
| 6.1 | 退货退款获批全流程 | 买家 `POST /api/after-sale`(RETURN_REFUND) → 卖家 `approve` → 买家 `return-ship` → 卖家 `confirm-return` | 状态依次 `REQUESTED → APPROVED → RETURN_SHIPPED → REFUNDED` |
| 6.2 | 被拒 → 平台介入 → 仲裁退款 | 买家申请(REFUND_RECEIVED) → 卖家 `reject` → 买家 `escalate` → 管理员 `POST /api/admin/after-sale/{id}/arbitrate`(FULL_REFUND) | 状态依次 `REQUESTED → REJECTED → PLATFORM_ARBITRATION → REFUNDED` |

### 场景 7：举报处理（举报 + 审核）

| 编号 | 用例名称 | 测试步骤 | 预期结果 |
|---|---|---|---|
| 7.1 | 举报违规商品并办结 | 用户 `POST /api/products/{id}/report`(reasonType=COUNTERFEIT) → 管理员 `PUT /api/admin/reports/{id}/handle` | 举报 `PENDING` → 办结 `HANDLED` |
| 7.2 | 举报违规商品并驳回 | 用户举报 → 管理员 `PUT /api/admin/reports/{id}/dismiss` | 举报 `PENDING` → 驳回 `DISMISSED` |
| 7.3 | 举报自己的商品 | 卖家举报自有商品 | HTTP 403，`error.code=FORBIDDEN` |

---

## 三、测试结果

| 测试类 | 用例数 | 通过 | 失败 | 错误 |
|---|---|---|---|---|
| `BackendE2eTest`（7 大场景，22 用例） | 22 | 22 | 0 | 0 |
| `AuthServiceTest`（既有单元测试，回归） | 5 | 5 | 0 | 0 |
| **合计** | **27** | **27** | **0** | **0** |

**结论：7 大业务场景端到端测试全部通过，主流程与关键失败分支（校验失败、权限不足、状态不符、账号禁用等）均符合预期；既有单元测试无回归。**

---

## 四、覆盖的关键业务规则验证点

1. **注册/登录**：手机号唯一性（409）、密码长度校验（400）、错误密码（401）、禁用账号（403）。
2. **商品**：发布即 `ON_SALE`；编辑权限校验（非卖家 403）。
3. **订单**：下单扣减库存并 `WAIT_PAY`；支付进入 `WAIT_DELIVER`；不可购买自有商品（403）；重复支付（409）。
4. **发货**：仅 `WAIT_DELIVER` 可发货（否则 409），生成运单并进入 `WAIT_RECEIVE`。
5. **议价**：报价金额校验、卖家接受后按议价生成订单（`amountCent=offeredPriceCent`）、拒绝/撤销状态流转。
6. **售后**：仅 `COMPLETED` 后 7 天内可发起；退货退款四段状态流转；卖家拒绝后可平台介入，管理员仲裁（全额/部分退款、驳回）。
7. **举报**：不可举报自有商品（403）；管理员办结（HANDLED）/驳回（DISMISSED）状态流转。
