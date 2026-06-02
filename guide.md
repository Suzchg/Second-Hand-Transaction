# 二手交易平台 — 部署与启动指南

## 1. 环境要求

| 软件 | 版本要求 | 说明 |
|------|----------|------|
| Java JDK | **17 或更高** | 推荐 [Eclipse Temurin JDK 21](https://adoptium.net/) |
| Node.js | **18 或更高** | 推荐 [Node.js 20 LTS](https://nodejs.org/) |
| npm | 随 Node.js 自带 | 版本 ≥ 9 |
| Git | 任意版本 | 用于版本管理（可选） |

### 1.1 验证环境

打开终端（PowerShell 或命令提示符），分别运行以下命令确认安装成功：

```powershell
java -version
# 应输出类似：openjdk version "21.0.x" ...

node -v
# 应输出类似：v20.x.x

npm -v
# 应输出类似：10.x.x
```

---

## 2. 项目结构

```
Second-hand Transaction/
├── start.ps1              # 一键启动脚本（PowerShell）
├── guide.md               # 本指南
├── backend/               # Spring Boot 后端（Java）
│   ├── pom.xml            # Maven 依赖配置
│   ├── mvnw.cmd           # Maven Wrapper（Windows，无需安装 Maven）
│   └── src/main/java/com/secondhand/
│       ├── App.java       # 启动入口
│       ├── auth/          # 认证模块
│       ├── product/       # 商品模块
│       ├── order/         # 订单模块
│       ├── offer/         # 报价模块
│       ├── admin/         # 管理后台
│       ├── user/          # 用户模块
│       ├── payment/       # 支付模块
│       └── config/        # 全局配置
└── frontend/              # Vue 3 前端（JavaScript）
    ├── package.json       # npm 依赖配置
    └── src/
        ├── App.vue        # 主布局
        ├── router.js      # 路由配置
        ├── api.js         # HTTP 请求封装
        ├── views/         # 页面组件
        └── components/    # 通用组件
```

---

## 3. 快速启动

### 3.1 方式一：一键启动（推荐）

在项目根目录打开 **PowerShell**，运行：

```powershell
.\start.ps1
```

该脚本会自动启动后端（端口 8088）和前端（端口 5173）。

> 如果遇到脚本执行权限问题，先运行：
> ```powershell
> Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
> ```

### 3.2 方式二：手动启动

#### 启动后端（终端 1）

```powershell
cd backend

# 设置 Java 路径（如果系统已配置 JAVA_HOME 可跳过此步）
$env:JAVA_HOME = "你的 JDK 安装路径"
# 例如：$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.0.35-hotspot"

# 启动
.\mvnw.cmd -DskipTests spring-boot:run
```

看到以下输出表示启动成功：
```
Started App in x.xxx seconds
```

后端运行在 **http://localhost:8088**

#### 启动前端（终端 2）

```powershell
cd frontend

# 首次运行需要安装依赖（仅需一次）
npm install

# 启动开发服务器
npm run dev
```

看到以下输出表示启动成功：
```
VITE v8.x.x  ready in xxx ms
➜  Local:   http://localhost:5173/
```

前端运行在 **http://localhost:5173**

---

## 4. 访问地址

| 服务 | 地址 |
|------|------|
| 前端页面 | http://localhost:5173 |
| 后端 API | http://localhost:8088 |
| Swagger 文档 | http://localhost:8088/swagger |
| H2 数据库控制台 | http://localhost:8088/h2-console |

### H2 控制台连接信息

| 参数 | 值 |
|------|-----|
| JDBC URL | `jdbc:h2:file:~/.secondhand/v2` |
| 用户名 | `sa` |
| 密码 | 留空 |

---

## 5. 管理员账号

系统启动后自动创建一个管理员账号：

| 字段 | 值 |
|------|-----|
| 登录方式 | 手机号 |
| 账号 | `13800000000` |
| 密码 | `admin123` |

管理员登录后，导航栏右侧会出现橙色的「**管理**」按钮，可进入后台管理页面。

> 后台功能：数据总览、用户管理、商品管理、订单管理。

---

## 6. 功能概览

### 用户端

| 页面 | 路径 | 功能 |
|------|------|------|
| 首页 | `/` | 商品浏览、分类筛选、关键词搜索 |
| 商品详情 | `/products/:id` | 查看详情、立即购买、砍价 |
| 发布商品 | `/sell` | 上传商品信息和图片 |
| 在售管理 | `/my-products` | 管理自己的商品（编辑/上下架） |
| 订单 | `/my-orders` | 我买到的 / 我卖出的（报价、支付、发货、收货） |
| 个人中心 | `/profile` | 资料编辑、收货地址、已售出/我的购买（历史） |
| 切换账号 | `/switch-account` | 已保存账号的快速切换 |

### 管理端

| 页面 | 功能 |
|------|------|
| 数据总览 | 用户数、商品数、订单数、在线用户 |
| 用户管理 | 查看、禁用、强制下线 |
| 商品管理 | 查看、上下架、删除违规商品 |
| 订单管理 | 查看、标记支付、取消订单 |

### 交易流程

```
买家浏览商品 → 立即购买 / 砍价报价
    ↓
卖家接受报价 / 订单创建
    ↓
买家支付 → 卖家发货 → 买家收货确认
    ↓
交易完成 → 进入历史（已售出/我的购买）
```

### 资金托管

当前使用 Mock 支付（开发环境）。生产环境可接入支付宝/微信支付，只需实现 `PaymentService` 接口即可，业务代码无需改动。

---

## 7. 常见问题

### 7.1 端口被占用

如果 8088 或 5173 端口被占用：

```powershell
# 查看占用端口的进程
netstat -ano | findstr "8088"
netstat -ano | findstr "5173"

# 终止进程（替换 PID 为实际进程号）
taskkill /F /PID <PID>
```

### 7.2 前端页面空白

确保后端已正常启动。前端依赖后端 API，如果后端未运行，页面会跳转到登录页或显示空白。

### 7.3 登录失败

- 确认选择的是「手机号」还是「邮箱」登录方式
- 手机号格式：11 位数字（如 `13800000000`）
- 密码至少 6 位

### 7.4 如何重置数据库

关闭后端，删除数据库文件后重启即可：

```powershell
# 删除 H2 数据库文件
del $env:USERPROFILE\.secondhand\v2.mv.db
del $env:USERPROFILE\.secondhand\v2.trace.db

# 重启后端，系统会自动建表并创建管理员
```

### 7.5 Maven 编译慢

首次运行 Maven 会下载依赖（约 100MB），耐心等待。后续启动会快很多。

### 7.6 多人协作注意事项

- 每个成员的数据库是独立的（存储在各自电脑上），互不影响
- 提交代码前确保 `npm run build` 和 Maven `compile` 都能通过
- 不要在 Git 中提交数据库文件（`.secondhand/` 目录已在 `.gitignore` 中）

---

## 8. 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.3 |
| 安全认证 | Spring Security + JWT (jjwt 0.12) |
| 数据库 | H2（内嵌，无需安装） |
| ORM | Spring Data JPA / Hibernate |
| 前端框架 | Vue 3 (Composition API) |
| 构建工具 | Vite 8 |
| 路由 | Vue Router 4 |
