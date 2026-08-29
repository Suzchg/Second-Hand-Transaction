# 二手交易平台 SecondHand

校园/社区二手交易平台，前后端分离架构，支持商品买卖、订单管理、售后维权、私聊评价等全流程交易场景。

---

## 功能总览

### 用户端

| 模块 | 功能 |
|------|------|
| 认证 | 注册、登录、多账号切换、JWT 令牌鉴权，角色分为 USER / ADMIN |
| 首页 | 商品瀑布流浏览、分类筛选、搜索、排序 |
| 商品 | 发布（多种成色）、编辑、上架/下架；多图上传 + 图片缩放预览 |
| 订单 | 下单购买、模拟支付、确认收货；卖家发货（支持物流单号） |
| 私聊 | 买家卖家实时聊天，消息中心聚合展示 |
| 评价 | 交易完成后双向互评、综合评分展示 |
| 评论 | 商品评论区互动讨论 |
| 收藏 | 收藏感兴趣的商品，统一管理 |
| 售后 | 退货/退款申请、卖家审核、物流退回、平台确认，完整售后流程 |
| 物流 | 快递100 实时轨迹查询（可切换 Mock 模式） |
| 举报 | 举报违规商品，管理员处理 |
| 个人中心 | 头像/昵称编辑、收货地址管理（省市区三级联动） |
| 暗色模式 | 亮色/暗色切换，跟随系统偏好 |
| 卖家主页 | 查看卖家所有在售商品和历史 |

### 管理后台 (`/admin`)

| 页面 | 功能 |
|------|------|
| 数据面板 | 用户数、商品数、订单数等核心指标概览 |
| 用户管理 | 用户列表、封禁/解封、角色管理 |
| 商品管理 | 商品审核、下架/删除违规商品 |
| 订单管理 | 所有订单查询、状态跟踪 |
| 举报处理 | 审核举报并处理 |
| 售后处理 | 平台仲裁售后申请 |

---

## 环境要求

| 环境 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 后端编译与运行 |
| Node.js | 18+ | 前端构建 |
| MySQL | 8.0 | 关系数据库 |
| Docker | — | Docker Compose 启动（推荐），可选 |

Windows 下额外需要 PowerShell 5.1+。

---

## 端口说明

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端（开发） | 5173 | `npm run dev` Vite 开发服务器 |
| 前端（生产/Docker） | 8080 | Nginx 托管静态文件 |
| 后端 | 8088 | Spring Boot REST API |
| MySQL | 3306 | 数据库 |

---

## 启动方法

### 方式一：Docker Compose（推荐）

无需手动安装 JDK、Node.js 或 MySQL，仅需 Docker：

```bash
git clone https://github.com/Suzchg/Second-Hand-Transaction.git
cd Second-Hand-Transaction
cp .env.example .env
docker compose up --build -d
```

```powershell
git clone https://github.com/Suzchg/Second-Hand-Transaction.git
Set-Location Second-Hand-Transaction
Copy-Item .env.example .env
docker compose up --build -d
```

启动后访问 http://localhost:8080。MySQL 首次启动自动执行 `db/init.sql`，数据保存在 Docker Volume 中。

停止：`docker compose down`。如需清空所有数据：`docker compose down --volumes`。

### 方式二：手动启动

1. 安装 JDK 17+、Node.js 18+、MySQL 8.0
2. 创建数据库：`CREATE DATABASE secondhand DEFAULT CHARACTER SET utf8mb4;`
3. 修改 `backend/src/main/resources/application.yml` 中的数据库密码
4. 启动后端：

```bash
cd backend
./mvnw.cmd -DskipTests spring-boot:run
```

5. 安装前端依赖并启动：

```bash
cd frontend
npm install
npm run dev
```

6. 访问 http://localhost:5173

Windows 下也可使用一键脚本：`.\start.ps1`（自动检查 MySQL、设置 JDK 并启动前后端）。

---

## 测试账号与初始数据

### 预置管理员（数据库初始化自带）

`db/init.sql` 中预置一个管理员账号，Docker Compose 或手动导入后即可使用：

| 角色 | 登录标识 | 密码 |
|------|----------|------|
| 管理员 | `admin` | `admin123` |

管理后台入口：`/admin`

### 演示数据（DataSeeder 自动生成）

后端首次启动且数据库为空时，`DataSeeder` 自动生成演示数据，包含：

- **8 个普通用户**，手机号 `13800000001` ~ `13800000008`，**统一密码 `123456`**
- 23 个商品分类下各生成 1 个商品（含随机图片、描述）
- 10 个不同状态的订单、20 条评论、收藏、评价、聊天记录

演示数据生成后日志会输出 `[DataSeeder] 演示用户密码均为: 123456`。

> 关闭演示数据：`application.yml` 中设置 `app.seed.enabled: false`。

### init.sql 预置数据概要

| 数据 | 数量 |
|------|------|
| 商品分类 | 23 个 |
| 用户 | 3 个（管理员、用户111112、用户113） |
| 商品 | 2 个 |
| 订单 | 2 个（已取消 + 售后中） |
| 售后请求 | 2 个 |
| 评论/评价/聊天/物流 | 各 1~2 条 |

---

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17+ | 运行环境 |
| Spring Boot | 3.3.2 | 核心框架 |
| Spring Security | — | 认证授权 |
| Spring Data JPA | — | 数据访问层（Hibernate） |
| MySQL | 8.0 | 关系数据库 |
| H2 | — | 测试用内存数据库 |
| JWT (jjwt) | 0.12.6 | 无状态认证 |
| SpringDoc OpenAPI | 2.6.0 | Swagger 接口文档 |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue 3 | 3.5 | 渐进式框架 |
| Vite | 8.0 | 构建工具 |
| Vue Router | 4.6 | SPA 路由 |
| Pinia | 3.0 | 状态管理 |
| Vitest | 4.1 | 单元测试 |

---

## 项目结构

```
Second-hand Transaction/
├── backend/                              # Spring Boot 后端
│   ├── pom.xml
│   ├── sql/                              # 数据库补丁脚本（after_sale_v2.sql）
│   └── src/main/java/com/secondhand/
│       ├── App.java                      # 启动入口
│       ├── admin/                        # 管理后台模块（6 个 Controller + TokenBlacklist + AdminService）
│       ├── aftersale/                    # 售后模块（退货/退款/物流退回）
│       ├── auth/                         # 认证模块（注册/登录/多账号/JWT + 4 个安全类）
│       ├── chat/                         # 私聊模块 + 消息中心
│       ├── comment/                      # 评论模块
│       ├── common/                       # 公共层（ApiResponse、GlobalExceptionHandler、速率限制）
│       ├── config/                       # 全局配置（CORS、Security、静态资源、DataSeeder）
│       ├── favorite/                     # 收藏模块
│       ├── logistics/                    # 物流模块（Provider 模式：Mock / 快递100 API 可切换）
│       ├── offer/                        # 报价/出价模块
│       ├── order/                        # 订单模块（5 个 Entity + 3 个 Repository）
│       ├── payment/                      # 支付模块（Mock 实现）
│       ├── product/                      # 商品模块 + category 子模块 + image 子模块（上传/本地存储）
│       ├── rating/                       # 评分模块
│       ├── report/                       # 举报模块
│       └── user/                         # 用户模块（资料/地址/省市区）
│
├── frontend/                             # Vue 3 前端
│   ├── vite.config.js                    # Vite 配置（API 代理到 8088）
│   └── src/
│       ├── main.js                       # 入口（挂载 Pinia、Router、全局组件/指令）
│       ├── App.vue                       # 根布局
│       ├── api.js                        # HTTP 请求封装（自动附加 Token、统一错误处理）
│       ├── router.js                     # 路由表 + 导航守卫（登录/管理员权限校验）
│       ├── stores/                       # Pinia 状态管理
│       │   ├── user.js                   # 用户认证（多账号切换、头像同步、token 管理）
│       │   ├── theme.js                  # 主题切换（亮/暗、跟随系统）
│       │   └── notification.js           # 通知轮询（未读消息、待处理订单）
│       ├── components/                   # 通用组件（8 个）
│       │   ├── AppIcon.vue               # 应用图标
│       │   ├── CategoryNav.vue           # 分类导航
│       │   ├── ImageUploader.vue         # 多图上传
│       │   ├── ImageGallery.vue          # 图片预览（支持缩放）
│       │   ├── AddressPicker.vue         # 地址选择器
│       │   ├── RegionCascader.vue        # 省市区三级联动
│       │   ├── Skeleton.vue              # 骨架屏加载占位
│       │   └── Toast.vue                 # 全局消息提示
│       └── views/                        # 页面组件
│           ├── Home.vue                  # 首页（瀑布流浏览）
│           ├── Login.vue                 # 登录/注册
│           ├── ProductDetail.vue         # 商品详情
│           ├── Sell.vue                  # 发布/编辑商品
│           ├── Order.vue                 # 订单详情
│           ├── Messages.vue              # 消息列表/聊天
│           ├── MyProducts.vue            # 我的商品
│           ├── MyOrders.vue              # 我的订单
│           ├── MyFavorites.vue           # 我的收藏
│           ├── MyAfterSales.vue          # 我的售后
│           ├── Profile.vue               # 个人信息
│           ├── AddressForm.vue           # 地址表单（新增/编辑复用）
│           ├── SellerProducts.vue        # 卖家主页
│           ├── SwitchAccount.vue         # 切换账号
│           ├── AfterSalePolicy.vue       # 售后政策
│           ├── PrivacyPolicy.vue         # 隐私政策
│           └── admin/                    # 管理后台（7 个页面）
│               ├── AdminLayout.vue       #   后台布局（侧边栏）
│               ├── AdminDashboard.vue    #   数据面板
│               ├── AdminUsers.vue        #   用户管理
│               ├── AdminProducts.vue     #   商品管理
│               ├── AdminOrders.vue       #   订单管理
│               ├── AdminReports.vue      #   举报处理
│               └── AdminAfterSale.vue    #   售后处理
│
├── db/                                   # 数据库初始化（Dockerfile + init.sql）
├── k8s/                                  # Kubernetes 部署清单
│   ├── deployment.yaml                   # 基础 Deployment/Service
│   └── production.yaml                   # 生产环境覆盖配置
├── scripts/                              # 辅助脚本
│   ├── deploy.sh                         # K8s 部署脚本
│   ├── health-check.sh                   # 健康检查脚本
│   └── expand-regions.js                 # 省市区数据展开
├── docker-compose.yml                    # Docker Compose 编排（mysql + backend + frontend）
├── .env.example                          # 环境变量模板
└── .github/workflows/                    # CI/CD 流水线
    ├── ci.yml                            # 持续集成
    └── cd.yml                            # 持续部署
```

---

## 后端模块架构

后端按功能垂直拆分，每个模块遵循 **Controller → Service → Repository → Entity** 分层结构：

| 模块 | 路径 | 文件构成 |
|------|------|---------|
| 认证 | `auth/` | Controller、4 DTO、4 Entity（User/Role/UserIdentity/UserStatus）、2 Repository、JWT 安全类、Service |
| 用户 | `user/` | 3 Controller、1 Entity、1 Repository、2 Service |
| 商品 | `product/` | 2 Controller、3 Entity、2 Repository、Service、category 子模块、image 子模块 |
| 订单 | `order/` | Controller、5 Entity、3 Repository、Service |
| 支付 | `payment/` | Controller、Service（Mock 实现，可接入真实支付） |
| 报价 | `offer/` | Controller、2 Entity、Repository、Service |
| 售后 | `aftersale/` | Controller、2 Entity、Repository、Service |
| 聊天 | `chat/` | 2 Controller、Entity、Repository、2 Service |
| 评论 | `comment/` | Controller、Entity、Repository、Service |
| 收藏 | `favorite/` | Controller、Entity、Repository、Service |
| 评分 | `rating/` | Controller、Entity、Repository、Service |
| 举报 | `report/` | Controller、3 Entity、Repository、Service |
| 物流 | `logistics/` | Controller、Service、Provider（Mock + 快递100 策略模式可切换） |
| 管理后台 | `admin/` | 6 Controller、Service、TokenBlacklist |
| 公共层 | `common/` | ApiResponse、AppException、GlobalExceptionHandler、@RateLimit 限流注解 |

### 架构要点

- **认证**：Spring Security + JWT 无状态认证，支持多身份（用户名/手机号/邮箱）登录
- **限流**：基于 IP 的滑动窗口计数（`@RateLimit` 注解），防刷保护
- **物流**：Provider 策略模式，Mock 模拟与快递100 真实 API 可切换
- **统一响应**：`{ success, data, error }` 格式，全局异常处理捕获
- **数据库**：JPA `ddl-auto: update` 自动建表，ID 使用自增主键

### 前端组件拆分

**通用组件（`components/`，8 个）：**

| 组件 | 说明 |
|------|------|
| AppIcon | 全局应用 Logo |
| CategoryNav | 商品分类导航栏 |
| ImageUploader | 多图上传 |
| ImageGallery | 图片画廊，支持缩放预览 |
| AddressPicker | 收货地址选择器 |
| RegionCascader | 省市区三级联动选择 |
| Skeleton | 骨架屏加载占位 |
| Toast | 全局消息提示 |

**状态管理（Pinia Store，3 个）：**

| Store | 职责 |
|------|------|
| user | 用户认证状态（多账号切换、头像同步、token 管理） |
| theme | 主题切换（亮色/暗色、跟随系统偏好） |
| notification | 通知轮询（未读消息数、待处理订单数） |

**页面组件（`views/`，用户端 16 个 + 管理后台 7 个）：** 详见上方项目结构。

### 前端架构要点

- **路由守卫**：未登录跳转登录页，非管理员拒绝访问 `/admin`
- **暗色模式**：CSS 变量 + `data-theme` 属性切换，支持跟随系统
- **图片优化**：懒加载指令、图片缩放预览
- **骨架屏**：加载时显示占位骨架，提升感知性能
- **响应式**：适配手机/平板/桌面端

---

## CI/CD 配置

### GitHub Actions 流水线

| 文件 | 触发条件 | 说明 |
|------|----------|------|
| `ci.yml` | push 到 main/master/ci/cd 及所有 PR | CI：后端 Maven 测试 + 前端 npm 测试 + Docker 镜像构建 |
| `cd.yml` | push 到 master + 手动触发 | CD：测试 → 构建镜像 → 推送到 GHCR → K8s 部署 → 健康检查 |

#### CI 流水线（3 个 Job）

| Job | 内容 |
|------|------|
| **backend** | 启动 MySQL 8.0 Service Container → `mvn verify`（单元测试 + Testcontainers 集成测试） |
| **frontend** | `npm ci` → `npm test` → `npm run build` |
| **containers** | 矩阵策略并行构建 `backend`、`frontend`、`mysql` 三个 Docker 镜像 |

#### CD 流水线（4 个 Stage）

```
test-backend ─┬─► prepare ─► publish ─► deploy
test-frontend─┘
```

| Stage | 说明 |
|------|------|
| test | 执行与 CI 相同的测试（并行） |
| prepare | 归一化 GHCR 镜像路径 |
| publish | 构建并推送三个镜像到 GitHub Container Registry |
| deploy | 自托管 Runner 执行 `scripts/deploy.sh` → `scripts/health-check.sh` 部署到 Kubernetes |

### 容器化交付物

| 组件 | Dockerfile | 基础镜像 |
|------|------------|---------|
| Backend | `backend/Dockerfile` | 多阶段：Maven:3.9-temurin-17 → eclipse-temurin:17-jre-alpine |
| Frontend | `frontend/Dockerfile` | 多阶段：node:22-alpine → nginx:alpine |
| MySQL | `db/Dockerfile` | MySQL 8.0 + `init.sql` 初始化脚本 |

### Docker Compose 编排

`docker-compose.yml` 定义三个服务：`mysql` (3306) → `backend` (8088) → `frontend` (8080)，含健康检查依赖和数据卷持久化。

### Kubernetes 部署

`k8s/deployment.yaml` 提供基础 Deployment/Service 清单，`k8s/production.yaml` 提供生产环境覆盖配置。部署通过 `scripts/deploy.sh` 脚本执行，部署后通过 `scripts/health-check.sh` 做端口转发健康检查。

---

## 健康检查

Spring Boot Actuator 暴露两个 Kubernetes 探针端点：

| 端点 | 说明 |
|------|------|
| `GET /actuator/health/readiness` | 就绪探针（应用就绪 + 数据库连通） |
| `GET /actuator/health/liveness` | 存活探针（应用存活） |

CD 部署后通过 `scripts/health-check.sh` 端口转发验证前端首页和 `/api/categories`，任一请求失败则 CD 流水线失败。

---

## API 规范

### 统一响应格式

```json
{ "success": true, "data": { ... } }
{ "success": false, "error": { "code": "...", "message": "..." } }
```

### 认证方式

需要登录的接口携带请求头：

```
Authorization: Bearer <accessToken>
```

Swagger 文档：启动后端后访问 http://localhost:8088/swagger。

---

## 关键配置

`backend/src/main/resources/application.yml` 的核心配置项：

| 配置 | 说明 |
|------|------|
| `server.port` | 后端端口，默认 8088 |
| `spring.datasource.url` | MySQL 连接地址，默认 `jdbc:mysql://localhost:3306/secondhand` |
| `spring.datasource.password` | 数据库密码 |
| `app.cdn-base-url` | CDN 前缀，留空使用本地路径 |
| `app.seed.enabled` | 是否自动生成演示数据，默认 true |
| `app.security.jwt.secret` | JWT 签名密钥（生产环境务必修改） |
| `app.security.jwt.access-token-expiration-minutes` | JWT 过期时间，默认 120 分钟 |
| `logistics.kuaidi100.enabled` | 是否启用快递100真实 API（默认 false，使用 Mock） |

Docker Compose 运行时可通过 `.env` 文件修改端口和密码。

---

## 测试

```bash
# 后端测试（单元测试 + Testcontainers 集成测试，需本机 Docker）
cd backend
./mvnw.cmd clean verify
python ../scripts/summarize_tests.py

# 前端测试
cd frontend
npm test
```

`mvn verify` 流程：Mockito 单元测试 → Testcontainers 启动 MySQL 8.0 容器 → 完整 Spring Boot 上下文 → HTTP 端到端集成测试。CI 流水线自动执行，测试失败时阻止部署。

---

## 许可

MIT License