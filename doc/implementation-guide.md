# 实现过程与开发指南

## 1. 项目初始化

### 1.1 环境要求

| 工具 | 最低版本 | 检查命令 |
|------|----------|----------|
| Java | 17+ | `java -version` |
| Node.js | 18+ | `node -v` |
| npm | 9+ | `npm -v` |

> **注意**：如果系统有多个 Java 版本，需要设置 `JAVA_HOME` 指向 JDK 17+。

### 1.2 快速启动

```powershell
# 方式一：使用启动脚本（推荐）
.\start.ps1

# 方式二：手动启动
# 终端 1 — 后端
cd backend
set JAVA_HOME=D:\JAVA\IDK24
.\mvnw.cmd -DskipTests spring-boot:run

# 终端 2 — 前端
cd frontend
npm run dev
```

### 1.3 访问地址

| 服务 | URL |
|------|-----|
| 前端页面 | http://localhost:5173 |
| 后端 API | http://localhost:8088 |
| Swagger 文档 | http://localhost:8088/swagger |
| H2 数据库控制台 | http://localhost:8088/h2-console |

---

## 2. 架构重构过程

### 2.1 初始状态

项目初始代码采用 **单文件大模块** 的组织方式，6 个 Java 文件承载了全部功能：

```
Auth.java     — 认证（Controller + Service + Entity + Filter + JWT）
Product.java  — 商品（Controller + Service + Entity + Repository）
Order.java    — 订单 + 物流实体（Controller + Service + Entity + Repository）
AfterSale.java— 售后（Controller + Service + Entity + Repository）
Logistics.java— 物流（Controller + Service + Provider）
App.java      — 启动 + 安全配置 + 异常处理 + SPA 转发
```

**问题**：
1. 职责混合：Controller、Service、Entity、Repository 在同一文件
2. 难以测试：各层紧耦合
3. 不符合开闭原则：修改一个功能需要改动整个文件
4. 团队协作困难：多人修改同一文件易冲突
5. API 响应不一致：部分直接返回 Entity，部分包装了格式

### 2.2 重构步骤

#### Step 1: 提取公共层（common/ + config/）

将 `App.java` 中的通用组件拆分为独立文件：

- `ApiResponse` → `common/ApiResponse.java`
- `AppException` → `common/AppException.java`
- 异常处理器 → `common/GlobalExceptionHandler.java`
- 安全配置 → `config/SecurityConfig.java`
- SPA 转发 → `config/SpaController.java`

#### Step 2: 按模块拆分认证模块（auth/）

以 Auth 模块为样板，建立标准的内部包结构：

```
auth/
├── controller/    # REST 端点
├── service/       # 业务逻辑
├── dto/           # 数据传输对象
├── entity/        # JPA 实体 + 枚举
├── repository/    # 数据访问
└── security/      # JWT 相关
```

关键改进：
- JWT 服务从具体类改为 **接口 + 实现**，便于替换签名算法
- 新增 `GET /api/auth/me` 端点，获取当前用户信息
- 新增 `AuthResponse` DTO，避免直接暴露 Entity
- 统一使用 Java Record 作为 DTO，代码更简洁

#### Step 3: 拆分其余业务模块

按相同模式拆分 Product、Order、AfterSale、Logistics 四个模块。

#### Step 4: 更新 App.java

主类精简为仅包含 `@SpringBootApplication` 和 `main` 方法。

### 2.3 重构前后对比

| 指标 | 重构前 | 重构后 |
|------|--------|--------|
| Java 文件数 | 6 | 47 |
| 单文件最大行数 | ~320 | ~220 |
| 模块包数 | 1 | 6 |
| 接口抽象 | 0 | 2 (JwtService, LogisticsProvider) |
| DTO 类数 | 3 (内部 Record) | 6 (独立文件) |

---

## 3. 前端重构过程

### 3.1 修复的关键 Bug

**问题**：`Login.vue` 向后端发送 `phone` 和 `email` 字段，但后端 `RegisterRequest`/`LoginRequest` 期望的是 `identifier` 字段。导致注册/登录永远失败。

**修复**：
```javascript
// 修复前
const body = { identityType, phone, email, password }

// 修复后
const identifier = identityType === 'PHONE' ? phone.value : email.value
const body = { identityType, identifier, password }
```

### 3.2 api.js 重构

改为自动解包后端 `ApiResponse<T>` 格式：
```javascript
// 旧行为：返回 { success: true, data: {...} }
// 新行为：自动提取 data 字段返回，前端代码无需 .data
```

### 3.3 App.vue 增强

- 新增 `fetchUser()`：页面加载时通过 `GET /api/auth/me` 获取用户信息
- 新增昵称显示：登录后在导航栏显示用户昵称
- 新增 `logout()`：清除 localStorage 并跳转登录页
- 未登录时显示「登录」按钮

### 3.4 Login.vue 增强

- 添加前端格式校验：手机号正则 `/^1[3-9]\d{9}$/`，邮箱正则格式校验
- 密码最短 6 位校验
- 逐字段错误提示（红色文字在输入框下方）
- 切换登录/注册模式时清除错误状态

---

## 4. 开发调试

### 4.1 查看日志

```bash
# 后端日志（Maven 控制台）
cd backend
.\mvnw.cmd -DskipTests spring-boot:run

# 查看 SQL（application.yml 已开启 show-sql: true）
```

### 4.2 API 调试

使用 Swagger UI：`http://localhost:8088/swagger`

或直接用 curl：

```bash
# 注册
curl -X POST http://localhost:8088/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"identityType":"EMAIL","identifier":"test@test.com","password":"123456"}'

# 登录
curl -X POST http://localhost:8088/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"identityType":"EMAIL","identifier":"test@test.com","password":"123456"}'

# 获取用户信息
curl http://localhost:8088/api/auth/me \
  -H "Authorization: Bearer <token>"
```

### 4.3 数据库查看

访问 `http://localhost:8088/h2-console`：
- JDBC URL: `jdbc:h2:file:~/.secondhand/data`
- Username: `sa`
- Password: (留空)

### 4.4 前端热更新

Vite 开发服务器支持 HMR（Hot Module Replacement），修改 `.vue`/`.js` 文件后浏览器自动更新，无需手动刷新。

---

## 5. 添加新功能的步骤

以"添加用户头像"为例：

1. **Entity**：`User.java` 添加 `avatarUrl` 字段
2. **DTO**：`AuthResponse.java` 添加 `avatarUrl`
3. **Service**：注册时设置默认头像
4. **Controller**：可新增 `PUT /api/auth/avatar` 端点
5. **前端**：App.vue 显示头像图片

不需要修改 common/、config/ 或其他模块。

---

## 6. 部署建议

### 6.1 生产环境配置

```yaml
# application-prod.yml
spring:
  datasource:
    url: jdbc:mysql://...
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate.ddl-auto: validate  # 禁止自动更新，使用 Flyway
    show-sql: false

app.security.jwt.secret: ${JWT_SECRET}  # 从环境变量注入
```

### 6.2 构建产物

```bash
# 后端：生成 fat JAR
cd backend && mvnw package -DskipTests
# → target/secondhand-backend-0.1.0-SNAPSHOT.jar

# 前端：生成静态文件
cd frontend && npm run build
# → frontend/dist/
```

### 6.3 Docker 化（建议）

```dockerfile
# 后端
FROM eclipse-temurin:17-jre
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]

# 前端（可放入 Nginx 或与后端合并）
```
