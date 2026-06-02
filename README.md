# 二手交易系统

## 功能

注册/登录 · 商品发布/浏览 · 下单（支付占位）· 售后退货退款 · 物流轨迹（Mock）

## 快速启动

```powershell
.\start.ps1
```

或手动启动：

```powershell
# 后端（终端 1）
cd backend
set JAVA_HOME=D:\JAVA\IDK24
.\mvnw.cmd -DskipTests spring-boot:run

# 前端（终端 2）
cd frontend
npm run dev
```

## 访问地址

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:5173 |
| 后端 API | http://localhost:8088 |
| Swagger 文档 | http://localhost:8088/swagger |
| H2 控制台 | http://localhost:8088/h2-console |

> H2 控制台 JDBC URL: `jdbc:h2:file:~/.secondhand/data`，用户名 `sa`，密码留空。

## 技术栈

- **后端**：Java 17+ / Spring Boot 3.3 / Spring Security / JPA / H2 / JWT (jjwt 0.12)
- **前端**：Vue 3 / Vite 8 / Vue Router 4

## 目录结构

```
├── start.ps1                     # 一键启动脚本
├── doc/                          # 📚 架构与设计文档
│   ├── architecture.md           # 系统架构设计
│   ├── auth-design.md            # 认证模块设计
│   ├── api-reference.md          # API 接口参考
│   ├── database-schema.md        # 数据库设计
│   └── implementation-guide.md   # 实现过程与开发指南
├── backend/                      # Spring Boot 后端
│   └── src/main/java/com/secondhand/
│       ├── App.java              # 启动入口
│       ├── common/               # 公共基础设施
│       ├── config/               # 全局配置
│       ├── auth/                 # 认证模块
│       ├── product/              # 商品模块
│       ├── order/                # 订单模块
│       ├── aftersale/            # 售后模块
│       └── logistics/            # 物流模块
└── frontend/                     # Vue 3 前端
    └── src/
        ├── api.js                # HTTP 请求封装
        ├── router.js             # 路由配置
        ├── App.vue               # 主布局
        └── views/                # 页面组件
```

## 架构特点

采用 **按功能分包的分层架构**（Package-by-Feature + Layered Architecture）：

- **Controller** → HTTP 请求处理 + 参数校验
- **Service** → 业务逻辑 + 事务管理
- **Repository** → 数据访问（Spring Data JPA）
- **Entity** → 领域模型（JPA）
- **DTO** → 请求/响应对象（Java Record）

> 详细架构说明见 [`doc/architecture.md`](doc/architecture.md)

## API 规范

所有 API 统一返回：

```json
{ "success": true, "data": { ... } }
{ "success": false, "error": { "code": "...", "message": "..." } }
```

> 完整接口文档见 [`doc/api-reference.md`](doc/api-reference.md)
