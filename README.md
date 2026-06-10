# 🛒 二手交易平台 SecondHand

一个功能完整的校园/社区二手交易平台，前后端分离架构，支持商品买卖、订单管理、售后维权、私聊评价等全流程交易场景。

---

## 📋 功能总览

### 用户端

| 模块 | 功能 |
|------|------|
| 🔐 **认证** | 注册、登录、多账号切换、JWT 令牌鉴权，角色分为 `USER` / `ADMIN` |
| 🏠 **首页** | 商品瀑布流浏览、分类筛选、搜索、排序 |
| 📦 **商品** | 发布（九成新/轻微使用等成色）、编辑、下架/上架；多图上传 + 图片缩放预览 |
| 🛒 **订单** | 下单购买、模拟支付、确认收货；卖家发货（支持物流单号） |
| 💬 **私聊** | 买家卖家实时聊天，消息中心聚合展示 |
| ⭐ **评价** | 交易完成后双向互评、综合评分展示 |
| 📝 **评论** | 商品评论区，互动讨论 |
| ❤️ **收藏** | 收藏感兴趣的商品，统一管理 |
| 🔄 **售后** | 退货/退款申请、卖家审核、物流退回、平台确认，完整售后流程 |
| 🚚 **物流** | 快递100 实时轨迹查询（可切换 Mock 模式） |
| 🚩 **举报** | 举报违规商品，管理员处理 |
| 👤 **个人中心** | 头像/昵称编辑、收货地址管理（省市区三级联动） |
| 🌙 **暗色模式** | 亮色/暗色切换，跟随系统偏好 |
| 🧭 **卖家主页** | 查看卖家所有在售商品和历史 |

### 管理后台 (`/admin`)

| 页面 | 功能 |
|------|------|
| 📊 **数据面板** | 用户数、商品数、订单数等核心指标概览 |
| 👥 **用户管理** | 用户列表、封禁/解封、角色管理 |
| 📦 **商品管理** | 商品审核、下架/删除违规商品 |
| 📋 **订单管理** | 所有订单查询、状态跟踪 |
| 🚩 **举报处理** | 审核举报并处理 |
| 🔧 **售后处理** | 平台仲裁售后申请 |

---

## 🏗️ 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17+ | 运行环境 |
| Spring Boot | 3.3.2 | 核心框架 |
| Spring Security | — | 认证授权 |
| Spring Data JPA | — | 数据访问层 |
| MySQL | 8.0 | 关系数据库 |
| H2 | — | 测试数据库 |
| JWT (jjwt) | 0.12.6 | 无状态认证 |
| SpringDoc OpenAPI | 2.6.0 | Swagger 接口文档 |
| Maven Wrapper | — | 无需手动安装 Maven |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue 3 | 3.5 | 渐进式框架 |
| Vite | 8.0 | 构建工具 |
| Vue Router | 4.6 | SPA 路由 |
| Pinia | 3.0 | 状态管理 |
| Vitest | 4.1 | 单元测试 |
| 原生 CSS | — | 暗色模式 / 响应式 / 骨架屏 |

---

## 🚀 快速启动

### 一键启动（Windows）

```powershell
.\start.ps1
```

### 手动启动

#### 1. 环境准备

- **JDK 17+** → `java -version`
- **Node.js 18+** → `node -v`
- **MySQL 8.0** → `mysql -u root -p`

#### 2. 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS secondhand DEFAULT CHARACTER SET utf8mb4;
```

#### 3. 修改配置

编辑 `backend/src/main/resources/application.yml`，将数据库密码改为你的 MySQL root 密码：

```yaml
spring:
  datasource:
    username: root
    password: 你的密码
```

#### 4. 安装前端依赖

```bash
cd frontend
npm install
```

#### 5. 启动后端

```bash
cd backend
./mvnw.cmd -DskipTests spring-boot:run
# 看到 "Started App in x.xxx seconds" 即启动成功
```

#### 6. 启动前端

```bash
cd frontend
npm run dev
# 看到 "Local: http://localhost:5173/" 即启动成功
```

---

## 🌐 访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 🖥️ 前端页面 | http://localhost:5173 | 浏览器访问 |
| 🔌 后端 API | http://localhost:8088 | RESTful 接口 |
| 📖 Swagger 文档 | http://localhost:8088/swagger | 在线接口调试 |
| 🗄️ MySQL | localhost:3306 | 数据库连接 |

### 默认管理员

| 账号 | 密码 |
|------|------|
| `admin` | `admin123` |

管理员入口：http://localhost:5173/admin

---

## 📁 项目结构

```
Second-hand Transaction/
│
├── start.ps1                         # 一键启动脚本（自动检查 MySQL、设置 JDK）
├── stop.ps1                          # 停止脚本
├── guide.md                          # 新人上手指南（含常见问题）
│
├── backend/                          # Spring Boot 后端
│   ├── pom.xml                       # Maven 依赖配置
│   ├── mvnw.cmd / mvnw               # Maven Wrapper（无需安装 Maven）
│   ├── sql/                          # 数据库补丁脚本
│   │   └── after_sale_v2.sql
│   └── src/main/java/com/secondhand/
│       ├── App.java                  # 启动入口
│       ├── common/
│       │   └── ratelimit/            # 接口限流（滑动窗口计数，IP 级别）
│       ├── config/                   # Spring 全局配置（CORS、静态资源、拦截器）
│       ├── auth/                     # 🔐 认证模块（注册/登录/切换账号/JWT）
│       ├── user/                     # 👤 用户模块（资料/地址/省市区）
│       ├── product/                  # 📦 商品模块
│       │   ├── category/             #    - 分类管理
│       │   └── image/                #    - 图片上传（本地存储 + CDN 可选）
│       ├── order/                    # 🛒 订单模块（创建/支付/发货/收货/退款）
│       ├── offer/                    # 💰 报价/出价模块
│       ├── aftersale/                # 🔄 售后模块（退货/退款/物流退回）
│       ├── chat/                     # 💬 私聊模块 + 消息中心
│       ├── comment/                  # 📝 评论模块
│       ├── favorite/                 # ❤️ 收藏模块
│       ├── report/                   # 🚩 举报模块
│       ├── rating/                   # ⭐ 评分模块
│       ├── logistics/                # 🚚 物流模块
│       │   └── provider/             #    - Mock 物流 / 快递100 API
│       └── admin/                    # 🛡️ 管理后台模块
│       └── src/test/                 # 测试代码
│
├── frontend/                         # Vue 3 前端
│   ├── package.json                  # npm 依赖
│   ├── vite.config.js                # Vite 配置（API 代理）
│   ├── vitest.config.js              # 测试配置
│   ├── index.html
│   └── src/
│       ├── main.js                   # 入口：挂载 Pinia、Router、全局组件/指令
│       ├── App.vue                   # 根布局（导航栏 + 路由出口）
│       ├── api.js                    # HTTP 请求封装（自动附加 Token、统一错误处理）
│       ├── router.js                 # 路由表 + 导航守卫（登录/管理员权限校验）
│       ├── style.css                 # 全局样式 + CSS 变量（亮色主题）
│       ├── dark.css                  # 暗色主题变量覆写
│       ├── lazyBg.js                 # 图片懒加载自定义指令
│       ├── toast.js                  # Toast 消息提示工具
│       ├── stores/                   # Pinia 状态管理
│       │   ├── user.js               #    - 用户认证（多账号切换、头像同步）
│       │   ├── theme.js              #    - 主题切换（亮/暗、跟随系统）
│       │   └── notification.js       #    - 通知轮询（未读消息、待处理订单）
│       ├── components/               # 通用组件
│       │   ├── AppIcon.vue           #    - 应用图标
│       │   ├── CategoryNav.vue       #    - 分类导航
│       │   ├── ImageUploader.vue     #    - 多图上传
│       │   ├── ImageGallery.vue      #    - 图片预览（支持缩放）
│       │   ├── AddressPicker.vue     #    - 地址选择器
│       │   ├── RegionCascader.vue    #    - 省市区三级联动
│       │   ├── Skeleton.vue          #    - 骨架屏加载占位
│       │   └── Toast.vue             #    - 全局消息提示
│       ├── views/                    # 页面组件
│       │   ├── Home.vue              #    - 首页
│       │   ├── Login.vue             #    - 登录/注册
│       │   ├── ProductDetail.vue     #    - 商品详情
│       │   ├── Sell.vue              #    - 发布/编辑商品
│       │   ├── Order.vue             #    - 订单详情
│       │   ├── Messages.vue          #    - 消息列表
│       │   ├── MyProducts.vue        #    - 我的商品
│       │   ├── MyOrders.vue          #    - 我的订单
│       │   ├── MyFavorites.vue       #    - 我的收藏
│       │   ├── MyAfterSales.vue      #    - 我的售后
│       │   ├── Profile.vue           #    - 个人信息
│       │   ├── AddressForm.vue       #    - 地址表单
│       │   ├── SellerProducts.vue    #    - 卖家主页
│       │   ├── SwitchAccount.vue     #    - 切换账号
│       │   ├── AfterSalePolicy.vue   #    - 售后政策
│       │   ├── PrivacyPolicy.vue     #    - 隐私政策
│       │   └── admin/                # 管理后台
│       │       ├── AdminLayout.vue   #    - 后台布局（侧边栏）
│       │       ├── AdminDashboard.vue#    - 数据面板
│       │       ├── AdminUsers.vue    #    - 用户管理
│       │       ├── AdminProducts.vue #    - 商品管理
│       │       ├── AdminOrders.vue   #    - 订单管理
│       │       ├── AdminReports.vue  #    - 举报处理
│       │       └── AdminAfterSale.vue#    - 售后处理
│       └── __tests__/                # 前端测试
│
├── scripts/                          # 辅助脚本
│   ├── expand-regions.js             # 省市区数据展开
│   ├── generate_report.py            # 测试报告生成
│   └── fix_design_doc.py             # 设计文档修正
│
└── 售后功能测试报告.md                  # 售后功能测试报告
```

---

## 🔧 核心架构

### 后端

- **分层架构**：Controller → Service → Repository → Entity，按功能模块分包
- **认证**：Spring Security + JWT 无状态认证，支持多身份（用户名/手机号/邮箱）
- **限流**：基于 IP 的滑动窗口计数（`@RateLimit` 注解），防刷保护
- **物流**：策略模式，Mock 模拟 / 快递100 真实 API 可切换
- **统一响应**：`{ success, data, error }` 格式，全局异常处理
- **数据库**：JPA 自动建表（`ddl-auto: update`），ID 使用自增主键

### 前端

- **状态管理**：Pinia Store 集中管理用户、主题、通知状态
- **路由守卫**：未登录跳转登录页，非管理员拒绝访问 `/admin`
- **暗色模式**：CSS 变量 + `data-theme` 属性切换，支持跟随系统
- **图片优化**：懒加载指令、压缩上传、图片缩放预览
- **骨架屏**：加载时显示占位骨架，提升感知性能
- **响应式**：适配手机/平板/桌面端

---

## 📡 API 规范

### 统一响应格式

```json
// 成功
{ "success": true, "data": { ... } }

// 失败
{ "success": false, "error": { "code": "...", "message": "..." } }
```

### 认证方式

所有需要登录的接口在请求头携带：

```
Authorization: Bearer <accessToken>
```

### 接口文档

启动后端后访问 Swagger UI：http://localhost:8088/swagger

---

## ⚙️ 配置说明

编辑 `backend/src/main/resources/application.yml`：

```yaml
server:
  port: 8088                         # 后端端口

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/secondhand?...
    username: root                    # 数据库用户名
    password: 123000                  # 数据库密码

app:
  cdn-base-url:                      # CDN 前缀（留空使用本地路径）
  security:
    jwt:
      secret: ...                    # JWT 签名密钥（生产环境务必修改）
      access-token-expiration-minutes: 120

logistics:
  kuaidi100:
    enabled: false                   # 是否启用快递100真实 API
    customer: ""                     # 快递100 customer ID
    key: ""                          # 快递100 授权码
```

---

## 🧪 测试

```bash
# 后端测试
cd backend
./mvnw.cmd test

# 前端测试
cd frontend
npm test
```

---

## 📄 许可

MIT License
