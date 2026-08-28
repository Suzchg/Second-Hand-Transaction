# 二手交易平台集成/API 测试用例与报告

## 1. 结论

2026-08-28 在本机实际执行 `mvnw.cmd --batch-mode --no-transfer-progress clean verify`，
于 **10:14:54（Asia/Shanghai）** 完成，耗时 **1 分 47 秒**，退出码 **0 / BUILD SUCCESS**。

| 测试范围 | 总数 | 通过 | 断言失败 | 执行错误 | 跳过 |
|---|---:|---:|---:|---:|---:|
| 集成/API（本次扩展） | 60 | 60 | 0 | 0 | 0 |
| 既有后端单元测试回归 | 108 | 108 | 0 | 0 | 0 |
| 既有后端 E2E 回归 | 22 | 22 | 0 | 0 | 0 |
| **合计** | **190** | **190** | **0** | **0** | **0** |

计数来自本次 clean 构建生成的 Surefire/Failsafe XML，参数化测试按实际执行次数展开，
不把 JUnit 嵌套容器或业务准备步骤另算为用例。原有 `AuthFlowIT` 的 1 个测试扩展为 8 个，
新增其他 6 个场景的测试类；集成/API 测试净增加 59 个。

## 2. 运行环境与隔离方式

| 项目 | 实际环境 |
|---|---|
| 操作系统 | Windows 11 / 10.0 / amd64 |
| Java | Microsoft OpenJDK 21.0.6；项目编译目标 Java 17 |
| Maven | Maven Wrapper 3.9.8 |
| Spring Boot | 3.3.2 |
| JUnit Jupiter | 5.10.3 |
| Surefire / Failsafe | 3.5.2 / 3.5.2 |
| Testcontainers | 1.21.4（通过 BOM 统一模块版本） |
| Docker Engine | 29.3.1，Docker Desktop Linux containers |
| MySQL | 8.0.46，`mysql:8.0`，临时数据库 `secondhand_it` |
| MySQL 镜像摘要 | `sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b` |
| 字符集 | Java 源文件与 JSON 响应均按 UTF-8 处理 |

- 集成/API 使用 `@SpringBootTest + @AutoConfigureMockMvc`：保留真实 Spring Security、JWT 验证、Controller、Service、JPA 和 MySQL；没有 Mockito 替换这些组件，也没有关闭安全过滤器。
- MockMvc 是进程内的 HTTP 请求/响应模拟，不是网络端口测试；真实 HTTP 传输由另行回归的 22 个 E2E 测试验证。
- 用例没有外围测试事务。API 请求结束后，通过独立 JDBC 查询验证已提交数据，避免一级缓存或测试自动回滚掩盖事务问题。
- 每个测试 JVM 使用独立临时 MySQL 容器，随机映射端口，Testcontainers/Ryuk 自动清理。**不连接本机 3306、不使用或清空开发数据库**；Docker 不可用时直接失败，不跳过、不降级为 H2。
- 禁用随机演示数据；各用例通过注册/发布/下单 API 建立独立业务数据，只在售后时间窗口测试中直接调整测试记录的时间戳，避免等待或 `sleep`。
- 保留真实限流组件，普通业务测试使用不同的模拟客户端地址；本轮不测限流算法和并发性能。
- 物流查询调用项目已有 `MockLogisticsProvider`，断言数据库中的承运商、运单号被正确传递。购买流程验证前端当前使用的 `/api/orders/{id}/pay`；**未验证真实支付扣款、第三方退款到账、快递100网络调用或支付网关回调**。

## 3. 与《用例清单.md》的覆盖对应

清单只定义场景及结果，以下按现有 API 和业务规则具体化主成功、备选与异常流程。

| 清单编号 / 场景 | 主成功流程 | 备选流程 | 异常流程与数据库断言 | 测试类 / 实际数量 |
|---|---|---|---|---|
| 1 用户注册与登录 | 注册写入用户和身份；BCrypt 密码可校验；登录令牌可访问用户 API | 邮箱注册、大小写/空白归一化；管理员禁用后再启用 | 重复手机/邮箱不增加孤立用户；短密码/错误身份类型不落库；错误密码/未知账号；禁用账号；匿名/非法 JWT 无法发布商品 | `AuthFlowIT` / 8 |
| 2 商品发布与编辑 | 发布进入 ON_SALE；修改后数据库和公开详情一致 | 下架后搜索不可见，重新上架可见 | 价格、库存、运费、描述校验；其他卖家编辑；不存在商品；失败后价格、库存保持不变 | `ProductApiIT` / 8 |
| 3 商品购买（下单+支付） | 扣减库存、售罄下架、订单 WAIT_PAY → WAIT_DELIVER、支付时间和事件落库 | 取消恢复库存后再次购买；多库存逐次购买 | 自购、缺收件信息、售罄、不存在商品、越权支付/取消/查看、重复支付、已支付取消；无额外订单/事件 | `PurchaseApiIT` / 7 |
| 4 卖家发货 | 运单落库、WAIT_RECEIVE、事件记录、物流接口返回对应运单；确认收货进入 COMPLETED | 已发货后再次发货被拒，原运单保留 | 未付款、非卖家、空运单号、不存在订单/物流、未发货先确认；不误建运单、不推进订单状态 | `ShippingApiIT` / 5 |
| 5 出价议价 | PENDING → ACCEPTED，关联按 800 分议价创建的订单；补收件信息后支付 | 卖家拒绝 / 买家撤回，不生成订单、不消耗库存 | 非正报价、自有/售罄商品、越权接受/拒绝/撤回、重复接受、缺地址支付；尤其验证接受时商品售罄导致报价修改与订单创建整笔回滚 | `OfferApiIT` / 10 |
| 6 售后处理 | 申请 → 同意 → 退货发货 → 确认退货 → REFUNDED，全额退款订单 CANCELLED | 仅退款/部分退款；拒绝后介入；仲裁全额/部分/驳回/退货；买家取消；卖家超时自动同意退货 | 重复申请、未确认收货、超过 7 天、越权申请/查看/审批、错误阶段、普通用户仲裁、无效裁决；失败后无多余售后单，无效裁决回滚责任/裁决字段 | `AfterSaleApiIT` / 16 |
| 7 举报处理 | 举报 PENDING → 管理员 HANDLED；审核人/时间/备注落库；举报人和卖家收到相应系统消息 | 管理员 DISMISSED；举报人收到驳回消息，卖家不收到办结通知 | 自报、不存在商品/举报、缺失/非法原因、普通用户审核；不新增/改写举报，不向无关用户泄漏通知 | `ReportApiIT` / 6 |

关键接口：`/api/auth/*`、`/api/products`、`/api/orders/*`、`/api/shipments/*/track`、
`/api/products/*/offers`、`/api/offers/*`、`/api/after-sale/*`、
`/api/admin/after-sale/*`、`/api/products/*/report`、`/api/admin/reports/*`、`/api/messages/system`。

断言包含精确 HTTP 状态码、业务错误码、关键响应字段、数据库行数/关联 ID/金额/状态/事件与通知内容。
业务异常断言统一响应结构；由 Spring Security 直接拒绝的请求按当前实现断言 HTTP 403 及无数据库副作用，不假定其具有业务 JSON 错误体。

## 4. 测试用例明细

### 通用前置与阅读约定

- 下表与测试代码中的中文编号一一对应，共 **60 条**；参数化测试的每组参数单列一条，同一个测试方法内的连续断言不重复计数。
- 卖家、买家和无关用户均通过 `POST /api/auth/register` 新建，使用各自的 `Bearer accessToken`；管理员通过 `POST /api/auth/login` 登录。注册密码默认 `integration-pass-123`。
- 默认商品通过 `POST /api/products` 发布：`priceCent=1000`、`quantity=1`、有效标题和描述、`condition=NINE_TENTHS`、`freeShipping=true`。金额单位均为**分**。
- 正常下单调用 `POST /api/orders`，提交 `productId`、`receiverName=测试买家`、`receiverPhone=13900000000`、`receiverAddress=测试省测试市测试路1号`。
- “待付款订单”指完成注册、发布、下单后的 `WAIT_PAY` 订单；“已付款订单”在此基础上由买家调用 `POST /api/orders/{orderId}/pay`，进入 `WAIT_DELIVER`。
- “已完成订单”继续由卖家调用 `POST /api/orders/{orderId}/ship`，提交 `carrierCode=SF`、`trackingNo=IT-SF-{orderId}`，再由买家调用 `POST /api/orders/{orderId}/confirm`，进入 `COMPLETED`。
- `{productId}`、`{orderId}`、`{offerId}`、`{requestId}`、`{reportId}` 均取自当前用例创建的实际记录；不存在的资源使用 `9223372036854775807`。
- 所有正常 API 调用均断言 HTTP 200（注册为 201）、`success=true` 和 `data` 节点存在；业务异常同时断言 HTTP 状态、`success=false`、`error.code` 和非空 `error.message`。安全过滤器直接返回的 403 不额外假设 JSON 错误码。
- “数据库/跨模块断言”列只列出代码实际检查的内容，不将未实现的业务预期视为已验证。

### 场景 1：用户注册与登录（8 条）

对应代码：`backend/src/test/java/com/secondhand/auth/AuthFlowIT.java`。

| 编号 | 用例名称 | 测试步骤（HTTP 调用） | 预期接口结果 | 数据库/跨模块断言 |
|---|---|---|---|---|
| 1.1 | 手机注册、密码加密与登录凭证可用 | ① `POST /api/auth/register`，提交新手机号和有效密码；② 使用相同账号密码 `POST /api/auth/login`；③ 携带登录令牌 `GET /api/auth/me` | 注册 HTTP 201，`role=USER`，用户 ID 为正数且令牌非空；登录和查询 HTTP 200，两次返回的用户 ID 与注册结果一致 | `user_identities` 中该用户、手机号的关联记录恰好 1 条；`users.password_hash` 不等于明文，BCrypt 校验原密码成功 |
| 1.2 | 邮箱归一化与重复邮箱注册 | ① 用带首尾空格、大小写混合的新邮箱注册；② 用小写邮箱登录；③ 再用同一小写邮箱注册 | 首次注册 201；小写邮箱登录 200，用户 ID 一致；重复注册 409 / `IDENTITY_EXISTS` | `users.email` 保存为去空白后的小写邮箱；重复注册前后 `users`、`user_identities` 总数都不变，不产生孤立用户 |
| 1.3 | 重复手机号注册 | ① 注册新手机号；② 记录两张表的数量；③ 同一手机号再次 `POST /api/auth/register` | 首次注册 201；再次注册 409 / `IDENTITY_EXISTS` | `users` 和 `user_identities` 数量均与第二次请求前一致 |
| 1.4 | 注册密码长度不足 | `POST /api/auth/register`，提交 `identityType=PHONE`、新手机号、`password=123` | HTTP 400 / `VALIDATION_ERROR` | `users` 总数不变；该手机号对应的身份记录数为 0 |
| 1.5 | 非法注册身份类型 | `POST /api/auth/register`，提交 `identityType=INVALID`、新标识、`password=valid-password` | HTTP 400 / `VALIDATION_ERROR` | `users` 总数不变；该标识对应的身份记录数为 0 |
| 1.6 | 错误密码与未知账号登录 | ① 注册用户；② 用其手机号和 `wrong-password` 登录；③ 换用未注册的新手机号登录 | 两次登录均为 HTTP 401 / `INVALID_CREDENTIALS` | 已注册用户仍有且仅有 1 条 `ACTIVE` 记录，登录失败不改变账号状态 |
| 1.7 | 管理员禁用与重新启用账号 | ① 管理员 `PUT /api/admin/users/{userId}/disable?disabled=true`；② 用户登录；③ 管理员用 `disabled=false` 重新启用；④ 用户再次登录 | 两次管理操作均 200；禁用期间登录 403 / `FORBIDDEN`；重新启用后登录 200，返回原用户 ID | 禁用后查询 `users`，该账号确为 `DISABLED`；重新启用后的登录成功验证管理模块与认证模块联动 |
| 1.8 | 匿名与非法 JWT 发布商品 | ① 不携带身份凭证 `POST /api/products`；② 携带 `Authorization: Bearer invalid-token` 再发布有效商品 | 两次均被 Spring Security 拒绝，HTTP 403 | 两次请求前后 `products` 总数不变，未认证请求没有写入副作用 |

### 场景 2：商品发布与编辑（8 条）

对应代码：`backend/src/test/java/com/secondhand/product/ProductApiIT.java`。

| 编号 | 用例名称 | 测试步骤（HTTP 调用） | 预期接口结果 | 数据库/跨模块断言 |
|---|---|---|---|---|
| 2.1 | 发布商品并编辑标题、价格 | ① 卖家发布 `quantity=2` 的默认商品；② `PUT /api/products/{productId}`，提交 `title=更新商品`、`priceCent=1500`；③ 匿名 `GET /api/products/{productId}` | 发布、编辑、公开查询均 200；详情标题为“更新商品” | 发布后库存 2、状态 `ON_SALE`，卖家 ID 正确、免邮运费为 0；编辑后数据库价格为 1500 |
| 2.2 | 下架与重新上架影响搜索 | ① 发布商品；② `PUT /api/products/{productId}`，设置唯一标题及 `status=OFF_SALE`；③ `GET /api/products?keyword={唯一标题}`；④ 设置 `status=ON_SALE` 后再次搜索 | 操作均 200；下架后 `totalElements=0`；重新上架后首条结果 ID 等于该商品 ID | 下架时数据库库存仍为 1、状态为 `OFF_SALE`；搜索验证写入状态与公开查询一致 |
| 2.3 | 发布价格为零 | 使用有效商品请求，仅改为 `priceCent=0`，卖家 `POST /api/products` | HTTP 400 / `VALIDATION_ERROR` | 该卖家的商品记录数为 0 |
| 2.4 | 发布库存为零 | 使用有效商品请求，仅改为 `quantity=0`，卖家 `POST /api/products` | HTTP 400 / `VALIDATION_ERROR` | 该卖家的商品记录数为 0 |
| 2.5 | 发布运费为负 | 使用有效商品请求，仅改为 `shippingFeeCent=-1`，卖家 `POST /api/products` | HTTP 400 / `VALIDATION_ERROR` | 该卖家的商品记录数为 0，免邮设置不绕过数值校验 |
| 2.6 | 缺少商品描述 | 从有效商品请求中移除 `description` 后发布 | HTTP 400 / `VALIDATION_ERROR` | 该卖家的商品记录数为 0 |
| 2.7 | 越权编辑与非法价格编辑 | ① 卖家发布默认商品；② 其他用户 `PUT /api/products/{productId}`，提交 `priceCent=2000`；③ 原卖家提交 `priceCent=0` | 越权编辑 403 / `FORBIDDEN`；非法价格 400 / `VALIDATION_ERROR` | 两次失败后价格仍为 1000，库存 1、状态 `ON_SALE`，原商品未被修改 |
| 2.8 | 查询、编辑不存在商品 | ① 匿名 `GET /api/products/{不存在ID}`；② 登录用户 `PUT /api/products/{不存在ID}`，提交标题 | 两次均 HTTP 404 / `NOT_FOUND` | 本条检查资源不存在的接口契约，不额外执行写入后的 SQL 断言 |

### 场景 3：商品购买（下单 + 支付，7 条）

对应代码：`backend/src/test/java/com/secondhand/order/PurchaseApiIT.java`。

| 编号 | 用例名称 | 测试步骤（HTTP 调用） | 预期接口结果 | 数据库/跨模块断言 |
|---|---|---|---|---|
| 3.1 | 下单支付联动库存、订单和事件 | ① 创建待付款订单；② 买家 `POST /api/orders/{orderId}/pay`；③ 卖家 `GET /api/orders/{orderId}` | 下单、支付、详情均 200；卖家详情中 `canShip=true` | 下单后库存 0、商品 `OFF_SALE`，订单 `WAIT_PAY` 且买卖双方和金额 1000 正确；支付后 `WAIT_DELIVER`、`paid_at` 非空；事件顺序恰为 `WAIT_PAY → WAIT_DELIVER` |
| 3.2 | 取消恢复库存并允许再次购买 | ① 创建待付款订单；② 买家 `POST /api/orders/{orderId}/cancel`；③ 再取消同一订单；④ 另一买家对原商品重新下单 | 首次取消 200；重复取消 409 / `CONFLICT`；另一买家下单 200 | 原订单 `CANCELLED`，库存只恢复至 1 并上架，重复取消不再增加库存；原订单事件数 2；新订单 `WAIT_PAY`，库存再次变为 0、商品下架 |
| 3.3 | 缺收货信息或购买自有商品 | ① 发布商品；② 买家仅提交 `productId` 调用 `POST /api/orders`；③ 卖家携带完整收件信息购买自己的商品 | 缺信息 400 / `VALIDATION_ERROR`；自购 403 / `FORBIDDEN` | 该商品订单数为 0，库存仍 1、状态仍 `ON_SALE` |
| 3.4 | 售罄或不存在商品下单 | ① 创建待付款订单，使商品售罄；② 其他买家再次购买该商品；③ 同一买家购买不存在的商品 ID | 售罄 409 / `CONFLICT`；不存在商品 404 / `NOT_FOUND` | 其他买家的订单数为 0；原商品库存保持 0、`OFF_SALE` |
| 3.5 | 其他买家支付、取消和查看订单 | 创建待付款订单后，无关用户依次 `POST /api/orders/{orderId}/pay`、`POST /api/orders/{orderId}/cancel`、`GET /api/orders/{orderId}` | 支付、取消均 404 / `NOT_FOUND`；详情查询 403 / `FORBIDDEN` | 订单仍 `WAIT_PAY`，库存 0、商品下架，订单事件仍仅 1 条 |
| 3.6 | 重复支付与已支付订单取消 | ① 准备已付款订单；② 买家再次调用 `/pay`；③ 买家调用 `/cancel` | 两次均 HTTP 409 / `CONFLICT` | 订单保持 `WAIT_DELIVER`；库存 0、商品下架；事件数仍为 2，不重复支付或恢复库存 |
| 3.7 | 多库存逐次购买 | ① 发布 `quantity=2` 的商品；② 买家 A 完整下单；③ 买家 B 完整下单 | 两次下单均 HTTP 200 | 第一次后库存 1、`ON_SALE`；第二次后库存 0、`OFF_SALE`；该商品订单数为 2 |

### 场景 4：卖家发货（5 条）

对应代码：`backend/src/test/java/com/secondhand/order/ShippingApiIT.java`。除特别说明外，前置为已付款订单。

| 编号 | 用例名称 | 测试步骤（HTTP 调用） | 预期接口结果 | 数据库/跨模块断言 |
|---|---|---|---|---|
| 4.1 | 发货、物流查询与确认收货 | ① 卖家 `POST /api/orders/{orderId}/ship`，提交 `SF` 与 `IT-SF-{orderId}`；② 匿名 `GET /api/shipments/{orderId}/track`；③ 买家 `POST /api/orders/{orderId}/confirm` | 均 HTTP 200；发货返回运单号一致；物流返回 `carrierCode=SF`、同一运单号、4 条轨迹 | 数据库只有 1 条匹配订单、承运商和运单号的记录；发货后订单 `WAIT_RECEIVE`，事件依次 `WAIT_PAY → WAIT_DELIVER → WAIT_RECEIVE`；确认后 `COMPLETED` |
| 4.2 | 重复发货不能覆盖原运单 | ① 使用默认运单成功发货；② 卖家再次 `/ship`，提交 `carrierCode=YTO`、`trackingNo=WRONG` | 首次 200；再次 409 / `CONFLICT` | 运单记录仍仅 1 条、运单号仍为 `IT-SF-{orderId}`；订单事件仍 3 条 |
| 4.3 | 未付款不能发货且暂无物流 | ① 准备待付款订单；② 卖家提交有效运单发货；③ 查询该订单物流 | 发货 409 / `CONFLICT`；物流 404 / `NOT_FOUND` | 运单数为 0；订单仍 `WAIT_PAY` |
| 4.4 | 非卖家发货与空运单号 | ① 无关用户对已付款订单提交有效运单；② 原卖家提交 `carrierCode=SF`、空 `trackingNo` | 非卖家 404 / `NOT_FOUND`；空运单 400 / `VALIDATION_ERROR` | 运单数为 0；订单仍 `WAIT_DELIVER` |
| 4.5 | 未发货先确认及不存在订单查物流 | ① 买家对已付款但未发货的订单调用 `/confirm`；② 查询 `GET /api/shipments/{不存在ID}/track` | 确认收货 409 / `CONFLICT`；不存在订单物流 404 / `NOT_FOUND` | 真实订单仍为 `WAIT_DELIVER` |

### 场景 5：出价议价（10 条）

对应代码：`backend/src/test/java/com/secondhand/offer/OfferApiIT.java`。
默认报价由买家 `POST /api/products/{productId}/offers` 创建，提交 `offeredPriceCent=800`、`message=议价`。

| 编号 | 用例名称 | 测试步骤（HTTP 调用） | 预期接口结果 | 数据库/跨模块断言 |
|---|---|---|---|---|
| 5.1 | 接受议价、补地址、支付及重复接受 | ① 买家报价；② 卖家 `POST /api/offers/{offerId}/accept`；③ 买家直接支付生成的订单；④ `PUT /api/orders/{orderId}/receiver` 补齐收件信息后再支付；⑤ 卖家重复接受报价 | 报价、接受、补地址和第二次支付均 200；缺地址支付 400 / `BAD_REQUEST`；重复接受 409 / `CONFLICT` | 报价由 `PENDING` 变为 `ACCEPTED` 并关联订单；订单金额为 800，库存 0、商品下架；首次支付失败时订单仍 `WAIT_PAY`、事件仅 1 条；补地址后 `WAIT_DELIVER`；该商品始终只产生 1 个订单 |
| 5.2 | 卖家拒绝报价 | ① 买家报价；② 卖家 `POST /api/offers/{offerId}/reject`；③ 卖家再调用 `/accept` | 拒绝 200；再接受 409 / `CONFLICT` | 报价为 `REJECTED`；该商品订单数 0；库存 1、`ON_SALE` |
| 5.3 | 买家撤回报价 | ① 买家报价；② 买家 `POST /api/offers/{offerId}/cancel`；③ 卖家再调用 `/accept` | 撤回 200；再接受 409 / `CONFLICT` | 报价为 `CANCELLED`；该商品订单数 0；库存 1、`ON_SALE` |
| 5.4 | 商品售罄后接受报价的事务回滚 | ① 买家 A 报价；② 买家 B 正常下单买走商品；③ 卖家接受 A 的报价 | 买家 B 下单 200；接受报价 409 / `CONFLICT` | 报价必须仍为 `PENDING`、`order_id` 为空，而非残留 `ACCEPTED`；商品订单数仍 1，库存 0、下架，验证报价修改和订单创建的事务回滚 |
| 5.5 | 零价报价 | 买家 `POST /api/products/{productId}/offers`，提交 `offeredPriceCent=0` | HTTP 400 / `BAD_REQUEST` | 报价数为 0，库存 1、`ON_SALE` |
| 5.6 | 负价报价 | 买家 `POST /api/products/{productId}/offers`，提交 `offeredPriceCent=-1` | HTTP 400 / `BAD_REQUEST` | 报价数为 0，库存 1、`ON_SALE` |
| 5.7 | 给自有及售罄商品报价 | ① 卖家对自有商品提交 800 分报价；② 其他买家正常下单；③ 又一用户对售罄商品提交报价 | 自有商品 403 / `FORBIDDEN`；售罄商品 409 / `CONFLICT` | 该商品报价数始终为 0 |
| 5.8 | 无关用户接受报价 | 准备有效待处理报价，无关用户 `POST /api/offers/{offerId}/accept` | HTTP 403 / `FORBIDDEN` | 报价仍 `PENDING`；该商品订单数为 0 |
| 5.9 | 无关用户拒绝报价 | 准备有效待处理报价，无关用户 `POST /api/offers/{offerId}/reject` | HTTP 403 / `FORBIDDEN` | 报价仍 `PENDING`；该商品订单数为 0 |
| 5.10 | 无关用户撤回报价 | 准备有效待处理报价，无关用户 `POST /api/offers/{offerId}/cancel` | HTTP 403 / `FORBIDDEN` | 报价仍 `PENDING`；该商品订单数为 0 |

### 场景 6：售后处理（16 条）

对应代码：`backend/src/test/java/com/secondhand/aftersale/AfterSaleApiIT.java`。

除 6.10 中的未付款分支外，前置均为 7 天售后窗口内的已完成订单。申请接口为
`POST /api/after-sale`，提交 `orderId`、下表中的 `type` 和 `refundAmountCent`，以及
`reason=商品存在问题`、`buyerEvidence=买家凭证`。
成功申请后，公共辅助方法都会查询售后单的订单 ID、买家 ID、卖家 ID，并断言售后单 `REQUESTED`、订单 `AFTER_SALE`。

“进入仲裁”包含：卖家 `POST /api/after-sale/{requestId}/reject`（`note=卖家拒绝`），
再由买家 `POST /api/after-sale/{requestId}/escalate`（`evidence=补充凭证`）；分别核对 `REJECTED` 和 `PLATFORM_ARBITRATION`。
下表 6.4～6.7 的管理员裁决统一提交 `responsibility=SELLER`、`shippingPaidBy=SELLER`、
`shippingCostCent=100`、`partialRefundCent=300`、`note=管理员裁决`，只改变 `result`。

| 编号 | 用例名称 | 测试步骤（HTTP 调用） | 预期接口结果 | 数据库/跨模块断言 |
|---|---|---|---|---|
| 6.1 | 退货退款完整审批流程 | ① 买家申请 `RETURN_REFUND`、1000 分；② 卖家 `POST /api/after-sale/{requestId}/approve`；③ 买家调用 `/return-ship`，提交 `SF`、`RETURN-{requestId}`；④ 卖家调用 `/confirm-return`；⑤ 买家 `GET /api/after-sale/{requestId}` | 各步均 200；详情 `refundAmountCent=1000` | 每阶段分别验证 `REQUESTED → APPROVED → RETURN_SHIPPED → REFUNDED`；退货运单号正确，`refunded_at` 非空；订单最终 `CANCELLED` |
| 6.2 | 收货后仅退款获批 | 买家申请 `REFUND_RECEIVED`、1000 分，卖家调用 `/approve` | 申请和审批均 HTTP 200 | 售后 `REFUNDED`，退款金额 1000；订单 `CANCELLED`，无需寄回步骤 |
| 6.3 | 部分退款获批 | 买家申请 `PARTIAL_REFUND`、300 分，卖家调用 `/approve` | 申请和审批均 HTTP 200 | 售后 `REFUNDED`，退款金额 300；订单保持 `COMPLETED`，验证部分退款不作废订单 |
| 6.4 | 平台裁决全额退款 | ① 申请 `RETURN_REFUND`、1000 分并进入仲裁；② 管理员 `POST /api/admin/after-sale/{requestId}/arbitrate`，提交 `result=FULL_REFUND` | 各步均 HTTP 200 | 售后 `REFUNDED`、退款金额 1000、订单 `CANCELLED`；责任方、运费承担方均 `SELLER`，运费 100 正确落库 |
| 6.5 | 平台裁决部分退款 | 申请并进入仲裁，管理员调用 `/arbitrate`，提交 `result=PARTIAL_REFUND`、`partialRefundCent=300` | 各步均 HTTP 200 | 售后 `REFUNDED`、金额改为 300、订单恢复 `COMPLETED`；责任方及运费字段与裁决一致 |
| 6.6 | 平台驳回售后 | 申请并进入仲裁，管理员调用 `/arbitrate`，提交 `result=DISMISS` | 各步均 HTTP 200 | 售后 `CLOSED`，订单恢复 `COMPLETED`；申请记录中的金额字段保留 1000（不代表退款到账），责任方及运费字段正确 |
| 6.7 | 平台裁决退货后完成退款 | ① 申请并进入仲裁；② 管理员提交 `result=RETURN_REFUND`；③ 买家调用 `/return-ship` 提交默认运单；④ 卖家调用 `/confirm-return` | 裁决、寄回、确认均 HTTP 200 | 裁决后售后 `APPROVED`、订单 `AFTER_SALE`、申请金额 1000、责任及运费字段正确；完成退货后售后 `REFUNDED`、订单 `CANCELLED` |
| 6.8 | 买家取消售后 | ① 申请 `REFUND_RECEIVED`、1000 分；② 买家 `POST /api/after-sale/{requestId}/cancel`；③ 卖家尝试 `/approve` | 取消 200；关闭后的审批 409 / `CONFLICT` | 售后 `CLOSED`，订单恢复并保持 `COMPLETED` |
| 6.9 | 重复申请不同售后类型 | ① 申请 `REFUND_RECEIVED`、1000 分；② 对同一订单再次申请 `RETURN_REFUND`、1000 分 | 首次 200；再次 409 / `CONFLICT` | 该订单售后记录仅 1 条，原申请仍 `REQUESTED`，订单仍 `AFTER_SALE` |
| 6.10 | 未确认收货与售后窗口过期 | ① 对待付款、未确认收货的订单申请 `REFUND_RECEIVED`；② 另建已完成订单，将测试库 `completed_at` 调整为 8 天前，再申请相同售后 | 未确认收货 403 / `FORBIDDEN`；超过窗口 410 / `CLOSED` | 两个订单的售后记录数都为 0；订单分别保持 `WAIT_PAY`、`COMPLETED`，无状态污染 |
| 6.11 | 无关用户申请、查看和审批售后 | ① 无关用户对已完成订单申请售后；② 真正买家正常申请；③ 无关用户 `GET /api/after-sale/{requestId}` 并调用 `/approve` | 越权申请、详情查询、审批均 403 / `FORBIDDEN`；真正买家申请 200 | 合法申请仍为 `REQUESTED`，订单保持 `AFTER_SALE` |
| 6.12 | 未被拒绝就申请平台介入 | 申请 `RETURN_REFUND` 后，不经过卖家拒绝，买家直接 `POST /api/after-sale/{requestId}/escalate` | HTTP 409 / `CONFLICT` | 售后仍 `REQUESTED`，订单仍 `AFTER_SALE` |
| 6.13 | 未获批就寄回退货 | 申请 `RETURN_REFUND` 后，买家直接 `POST /api/after-sale/{requestId}/return-ship`，提交有效运单 | HTTP 409 / `CONFLICT` | 售后仍 `REQUESTED`，订单仍 `AFTER_SALE` |
| 6.14 | 未寄回就确认退货 | 申请 `RETURN_REFUND` 后，卖家直接 `POST /api/after-sale/{requestId}/confirm-return` | HTTP 409 / `CONFLICT` | 售后仍 `REQUESTED`，订单仍 `AFTER_SALE` |
| 6.15 | 非管理员仲裁与无效裁决回滚 | ① 申请并进入仲裁；② 买家调用管理员 `/arbitrate`，提交 `result=FULL_REFUND`；③ 管理员提交 `result=INVALID`、`responsibility=BUYER` | 买家请求被安全过滤器拒绝，HTTP 403；无效裁决 400 / `BAD_REQUEST` | 售后保持 `PLATFORM_ARBITRATION`，订单保持 `AFTER_SALE`；`responsibility`、`arbitration_result` 都仍为空，验证无效裁决的字段修改回滚 |
| 6.16 | 卖家超时自动同意退货 | ① 买家申请 `RETURN_REFUND`、1000 分；② 将测试库该售后单 `deadline_at` 调整为 1 小时前；③ 管理员 `POST /api/admin/after-sale/process-timeouts` | 超时处理接口 HTTP 200 | 售后从 `REQUESTED` 变为 `APPROVED`，订单仍 `AFTER_SALE`；验证此分支只是同意退货，并非立即退款 |

### 场景 7：举报处理（6 条）

对应代码：`backend/src/test/java/com/secondhand/report/ReportApiIT.java`。
默认由非商品所有者调用 `POST /api/products/{productId}/report`，提交
`reasonType=COUNTERFEIT`、`description=疑似假冒商品`；审核备注为 `handleNote=核查结论`。

| 编号 | 用例名称 | 测试步骤（HTTP 调用） | 预期接口结果 | 数据库/跨模块断言 |
|---|---|---|---|---|
| 7.1 | 管理员办结并通知相关用户 | ① 用户举报；② 举报人 `GET /api/messages/system`；③ 管理员 `PUT /api/admin/reports/{reportId}/handle`；④ 举报人、卖家、无关用户分别查询系统消息 | 提交、办结、查询均 200；处理前举报人消息为空；处理后举报人收到恰好 1 条对应 `report-{reportId}` 的 `report_handled` 消息，关联商品 ID 正确且含“核查结论” | 举报先为 `PENDING` 且举报人/商品关联正确，办结后为 `HANDLED`；`handled_by` 为管理员、`handled_at` 非空、备注一致；卖家恰好收到 1 条 `report_product_handled`，无关用户消息为空 |
| 7.2 | 管理员驳回并通知举报人 | ① 用户举报；② 确认举报人系统消息为空；③ 管理员 `PUT /api/admin/reports/{reportId}/dismiss`；④ 三种用户分别查询系统消息 | 驳回 200；举报人收到恰好 1 条对应的 `report_dismissed` 消息，关联商品 ID 正确且含“核查结论” | 举报为 `DISMISSED`；审核人、审核时间和备注正确落库；卖家和无关用户消息均为空 |
| 7.3 | 自报、目标不存在和非法原因 | 依次：① 卖家举报自有商品；② 普通用户举报不存在商品；③ 举报现有商品但不传 `reasonType`；④ 提交 `reasonType=INVALID` | 依次为 403 / `FORBIDDEN`、404 / `NOT_FOUND`、400 / `VALIDATION_ERROR`、400 / `BAD_REQUEST` | 四次失败请求前后 `reports` 总数不变 |
| 7.4 | 普通用户不能办结举报 | ① 正常提交举报；② 举报人冒充审核者 `PUT /api/admin/reports/{reportId}/handle`，提交 `handleNote=unauthorized`；③ 查询自己的系统消息 | 管理接口被安全过滤器拒绝，HTTP 403；消息查询 200 且为空 | 举报仍 `PENDING`、`handled_by` 为空，不能产生审核通知 |
| 7.5 | 普通用户不能驳回举报 | ① 正常提交举报；② 举报人 `PUT /api/admin/reports/{reportId}/dismiss`；③ 查询自己的系统消息 | 管理接口 HTTP 403；消息查询 200 且为空 | 举报仍 `PENDING`、`handled_by` 为空，不能产生驳回通知 |
| 7.6 | 管理员处理不存在举报 | 管理员依次调用 `PUT /api/admin/reports/{不存在ID}/handle` 和 `/dismiss` | 两次均 HTTP 404 / `NOT_FOUND` | 两次请求前后 `reports` 总数不变 |

## 5. 执行记录与失败原因

| 轮次 | 实际执行结果 | 原因及处理 |
|---|---|---|
| 基线单元回归 | 108 / 108 通过 | 修改前已有单元测试通过 |
| 首次定向集成运行（10:04:05 结束） | 38 个，38 个启动错误；Maven 退出 1 | 旧 Testcontainers 依赖访问 Docker 29 时返回 HTTP 400，Spring 上下文未启动，未进入业务断言。升级测试依赖至 1.21.4 并使用 BOM 统一版本 |
| 第一次完整验证（10:12:30 结束） | 190 个，187 通过、3 个断言失败；Maven 退出 1 | 新测试辅助方法默认字符集解码 MockMvc JSON，中文商品标题和两种举报通知乱码。改为显式 UTF-8；保留原有中文断言，没有放宽预期或修改业务代码 |
| 最终完整验证（10:14:54 结束） | **190 / 190 通过，0 失败、0 错误、0 跳过；Maven 退出 0** | 修复后重新 `clean verify`，不使用旧编译或旧 XML 结果 |

最终失败原因：**无**。不把前两轮失败混入最终通过计数，也不隐去失败过程。
Testcontainers 版本选择参考 [官方 1.21.4 发布说明](https://github.com/testcontainers/testcontainers-java/releases/tag/1.21.4)。

辅助验证：报告汇总逻辑分别验证成功、缺少结果、断言失败、执行错误、跳过、无效 XML；
CI/CD YAML 使用 SnakeYAML 实际解析并检查依赖门禁和报告保留步骤，均通过。
这些辅助检查不计入上面的 190 个 JUnit 用例。

## 6. 流水线失败门禁

- CI 后端执行 Maven `verify`，包括 Surefire 与 Failsafe；镜像构建任务显式 `needs: [backend, frontend]`。
- CD 保持 `test-backend + test-frontend → prepare → publish → deploy` 的成功依赖链，未加入 `continue-on-error` 或失败后强行发布的条件。
- 测试失败时 Maven 返回非零；报告汇总和上传用 `if: always()` 保存失败证据，不能使前面失败的任务变为成功。
- Failsafe 设置 `failIfNoTests=true`。报告脚本发现失败/错误/跳过、缺失 XML 或缺少任一必测 API 场景时也返回非零，防止空测试或不完整报告被当作通过。
- 移除不再使用的固定端口 MySQL CI service；E2E 和集成测试都使用临时 MySQL。


## 7. 复现命令与报告位置

前提：JDK 17+、Python 3、Docker 已运行；首次运行需要下载 Maven 依赖及 `mysql:8.0`、Ryuk 镜像。
本次实际执行环境是 JDK 21；GitHub Actions 配置为 JDK 17，本次没有远端执行结果。

在项目根目录执行（PowerShell）：

```powershell
cd backend
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
$testExit = $LASTEXITCODE
python ../scripts/summarize_tests.py
if ($testExit -ne 0) { throw "Maven tests failed: $testExit" }
if ($LASTEXITCODE -ne 0) { throw "Test report is incomplete or failed" }
```

仅跑集成/API（开发调试，不等于完整回归）：

```powershell
cd backend
.\mvnw.cmd test-compile failsafe:integration-test failsafe:verify
# 单场景：
.\mvnw.cmd test-compile failsafe:integration-test failsafe:verify '-Dit.test=OfferApiIT'
```

| 产物 | 路径 |
|---|---|
| 可重新生成的自动统计及逐条用例结果 | `backend/target/test-report.md` |
| 集成/API 原始 XML | `backend/target/failsafe-reports/TEST-*.xml` |
| 单元/E2E 原始 XML | `backend/target/surefire-reports/TEST-*.xml` |
| 本次完整执行日志 | `backend/verification.log` |
| 修正前完整运行日志/失败统计 | `backend/verification-first.log` / `backend/verification-failed-report.log` |
| 首轮环境错误日志 | `backend/integration-first.log` |
| GitHub Actions 报告产物 | `backend-test-reports`（以后运行 CI/CD 时生成） |

`target/` 和 `.log` 已被项目忽略，不加入 Git；`clean` 会移除 target 中的旧报告，CI 会将本次结果上传保存。

## 8. 范围与限制

本次针对清单中 7 个业务场景补齐集成/API 验证，并回归已有后端测试。
不宣称覆盖全部 REST 端点、安全攻击面、并发超卖、性能、浏览器 UI 或第三方服务故障。
支付与退款只验证当前后端业务状态，不代表真实资金到账。前端测试、真实外部服务以及远端部署未在本次执行。
没有以代码覆盖率百分比代替业务场景断言。
