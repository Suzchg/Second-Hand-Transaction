# 二手交易系统 — 架构设计文档

## 1. 系统概览

本系统是一个二手商品交易平台的 MVP（最小可行产品），支持用户注册/登录、商品发布与浏览、下单购买、物流追踪和售后退款。

### 技术栈

| 层级 | 技术选型 | 版本 |
|------|----------|------|
| 后端框架 | Spring Boot | 3.3.2 |
| 编程语言 | Java | 17+（当前运行环境 JDK 24） |
| 安全框架 | Spring Security + JWT (jjwt) | 6.x / 0.12.6 |
| ORM | Spring Data JPA + Hibernate | 6.5.2 |
| 数据库 | H2 (嵌入式，文件模式) | — |
| API 文档 | SpringDoc OpenAPI (Swagger) | 2.6.0 |
| 前端框架 | Vue 3 + Vite | 3.5 / 8.x |
| 路由 | Vue Router | 4.6.4 |
| 密码加密 | BCrypt | — |

### 运行环境

- **后端端口**: `8088`（原 8080 被 Steam 占用，已修改）
- **前端端口**: `5173`（Vite 默认）
- **H2 数据库文件**: `~/.secondhand/data`
- **H2 控制台**: `http://localhost:8088/h2-console`
- **Swagger UI**: `http://localhost:8088/swagger`

---

## 2. 架构模式：按功能分包的分层架构

本项目采用 **Package-by-Feature + Layered Architecture**，这是目前 Java/Spring Boot 行业中最为主流的架构模式。

### 2.1 设计思想

> **分层架构（Layered Architecture）** 将系统按职责垂直拆分为 Controller → Service → Repository 三层，每层有明确的边界和依赖方向。
>
> **按功能分包（Package-by-Feature）** 将同一业务领域的代码聚合在同一包下，避免按类型分包导致的包爆炸和耦合。

两者结合后，形成本项目使用的 **"功能模块内部三层分离"** 的架构：

```
com.secondhand
├── App.java                          # Spring Boot 启动入口
├── common/                           # 公共基础设施（跨模块复用）
│   ├── ApiResponse.java              # 统一 API 响应体
│   ├── AppException.java             # 业务异常类
│   └── GlobalExceptionHandler.java   # 全局异常处理（@RestControllerAdvice）
├── config/                           # 全局配置
│   ├── SecurityConfig.java           # Spring Security 配置（双 FilterChain）
│   └── SpaController.java            # SPA 路由转发（Vue History 模式）
├── auth/                             # === 认证模块 ===
│   ├── controller/AuthController.java
│   ├── service/AuthService.java
│   ├── dto/                          # 请求/响应 DTO（Data Transfer Object）
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   ├── AuthResponse.java
│   │   └── ChangePasswordRequest.java
│   ├── entity/                       # JPA 实体（Domain Model）
│   │   ├── User.java
│   │   ├── UserIdentity.java
│   │   ├── UserStatus.java
│   │   └── IdentityType.java
│   ├── repository/                   # 数据访问层
│   │   ├── UserRepository.java
│   │   └── UserIdentityRepository.java
│   └── security/                     # 安全组件（JWT 相关）
│       ├── JwtService.java           # JWT 服务接口
│       ├── JwtServiceImpl.java       # JWT 服务实现（HMAC-SHA）
│       ├── JwtAuthFilter.java        # JWT 认证过滤器
│       └── AuthPrincipal.java        # 认证主体（UserDetails）
├── product/                          # === 商品模块 ===
│   ├── controller/ProductController.java
│   ├── service/ProductService.java
│   ├── dto/...
│   ├── entity/Product.java, ProductStatus.java
│   └── repository/ProductRepository.java
├── order/                            # === 订单模块 ===
│   ├── controller/OrderController.java
│   ├── service/OrderService.java
│   ├── entity/Order.java, OrderEvent.java, Shipment.java, ...
│   └── repository/OrderRepository.java, OrderEventRepository.java, ShipmentRepository.java
├── aftersale/                        # === 售后模块 ===
│   ├── controller/AfterSaleController.java
│   ├── service/AfterSaleService.java
│   ├── entity/...
│   └── repository/...
└── logistics/                        # === 物流模块 ===
    ├── controller/LogisticsController.java
    ├── service/LogisticsService.java
    └── provider/                     # 策略模式
        ├── LogisticsProvider.java    # 物流查询接口
        └── MockLogisticsProvider.java # Mock 实现
```

### 2.2 分层职责

```
┌──────────────────────────────────────────────────┐
│  Controller 层（@RestController）                 │
│  · 处理 HTTP 请求/响应                            │
│  · 参数校验（@Valid）                             │
│  · 调用 Service，组装 ApiResponse                 │
├──────────────────────────────────────────────────┤
│  Service 层（@Service, @Transactional）           │
│  · 核心业务逻辑                                    │
│  · 事务管理                                       │
│  · 跨 Repository 协调                              │
│  · 抛出 AppException 表示业务异常                   │
├──────────────────────────────────────────────────┤
│  Repository 层（@Repository, JpaRepository）       │
│  · 数据持久化                                     │
│  · 自定义查询方法（Spring Data JPA Derived Query）  │
├──────────────────────────────────────────────────┤
│  Entity 层（@Entity, JPA）                        │
│  · 领域模型定义                                    │
│  · 与数据库表一一映射                                │
├──────────────────────────────────────────────────┤
│  DTO 层（Java Record）                            │
│  · 请求/响应数据传输对象                            │
│  · 隔离外部 API 与内部模型                          │
│  · 携带校验注解（@NotBlank, @Size 等）              │
└──────────────────────────────────────────────────┘
```

### 2.3 关键设计模式

| 模式 | 应用位置 | 说明 |
|------|----------|------|
| **策略模式** | `logistics/provider/` | `LogisticsProvider` 接口 → `MockLogisticsProvider` 实现，后续可扩展真实物流 API |
| **模板方法** | Spring Security FilterChain | `OncePerRequestFilter` 定义 JWT 过滤模板 |
| **依赖注入** | 全部 Service/Controller | 构造器注入（Spring 推荐最佳实践） |
| **统一响应** | `common/ApiResponse` | `{ success, data, error }` 格式，前端自动解包 |

---

## 3. 请求处理流程

```
浏览器 → Nginx/CDN(可选) → Vue SPA (localhost:5173)
                                │
                    Vite Proxy: /api/* → localhost:8088
                                │
                    ┌───────────▼───────────┐
                    │  Tomcat (Spring Boot)  │
                    ├───────────────────────┤
                    │  SecurityFilterChain   │
                    │  ├─ JwtAuthFilter      │  ← 从 Authorization header 解析 JWT
                    │  └─ 注入 AuthPrincipal │
                    ├───────────────────────┤
                    │  Controller            │  ← @Valid 参数校验
                    │  → Service             │  ← @Transactional 事务
                    │    → Repository        │  ← JPA/Hibernate
                    │      → H2 Database     │
                    ├───────────────────────┤
                    │  ApiResponse<T>        │  ← 统一 JSON 格式
                    └───────────────────────┘
```

### 3.1 前端的 Vite 代理

由于前端运行在 `localhost:5173`，后端在 `localhost:8088`，Vite 开发服务器配置了代理：

```js
// vite.config.js
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8088',
      changeOrigin: true,
    },
  },
}
```

这意味着前端的 `fetch('/api/auth/login')` 会被 Vite 自动转发到 `http://localhost:8088/api/auth/login`，**跨域问题在开发环境被代理解决**。

---

## 4. 安全架构

```
请求进入 → SecurityFilterChain(api)
           │
           ├─ /api/auth/**          → permitAll  (注册、登录无需认证)
           ├─ GET /api/products     → permitAll  (浏览商品无需登录)
           ├─ GET /api/shipments/*/track → permitAll
           ├─ /swagger/**           → permitAll
           │
           └─ 其余 /api/**          → authenticated
              │
              └─ JwtAuthFilter
                 ├─ 提取 Authorization: Bearer <token>
                 ├─ 验证 JWT 签名 + 过期时间
                 ├─ 解析 userId → AuthPrincipal
                 └─ 注入 SecurityContext
```

---

## 5. 数据存储

使用 **H2 嵌入式数据库**（文件模式），数据持久化到 `~/.secondhand/data.mv.db`：

- **零配置**：无需安装 MySQL/PostgreSQL
- **开发友好**：JPA `ddl-auto: update` 自动建表/更新
- **兼容 MySQL 语法**：`MODE=MySQL` 连接参数

### 5.1 数据库表

| 表名 | 实体 | 说明 |
|------|------|------|
| `users` | User | 用户基本信息 + 密码哈希 |
| `user_identities` | UserIdentity | 登录标识（手机号/邮箱），一对多关联 User |
| `products` | Product | 商品信息 |
| `orders` | Order | 订单信息 |
| `order_events` | OrderEvent | 订单状态变更日志 |
| `shipments` | Shipment | 物流运单 |
| `after_sale_requests` | AfterSaleRequest | 售后申请 |

---

## 6. 与旧架构的对比

| 维度 | 旧架构（单文件） | 新架构（分层） |
|------|-----------------|---------------|
| 文件组织 | 6 个大文件，每个包含全部层 | 47 个文件，按模块/层拆分 |
| 职责边界 | 混合在一起，难以定位 | 每层职责清晰 |
| 测试可行性 | 难以单元测试（耦合） | 可独立 Mock 各层 |
| 团队协作 | 容易冲突 | 可按模块并行开发 |
| 新人上手 | 需要理解整个文件 | 按模块理解即可 |
| API 响应 | 直接返回实体 | 统一 ApiResponse 包装 |
| 异常处理 | 分散在 App.java 中 | 集中的 GlobalExceptionHandler |
| JWT 设计 | 具体类 | 接口 + 实现，可替换 |

---

## 7. 扩展方向

1. **真实数据库**：切换 H2 → MySQL/PostgreSQL，仅需修改 `application.yml` 中的 datasource 配置
2. **真实物流 API**：实现新的 `LogisticsProvider`（如快递 100、菜鸟），通过 `@Profile` 切换
3. **真实支付**：对接支付宝/微信支付，替换当前的占位 `mark-paid`
4. **缓存**：引入 Redis 缓存热门商品列表
5. **消息队列**：订单状态变更通过 MQ 异步通知
6. **容器化**：添加 Dockerfile + docker-compose.yml
7. **API 版本化**：`/api/v1/...` 路径前缀
