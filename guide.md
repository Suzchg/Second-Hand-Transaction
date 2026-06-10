# SecondHand 二手交易平台 — 启动指南

> 按步骤操作，每一步都有验证方法。遇到问题翻到最后的常见问题。

---

## 第一步：安装环境

需要安装三个软件：

| 软件 | 最低版本 | 下载地址 | 装完验证 |
|------|---------|---------|---------|
| JDK | 17 | https://adoptium.net/download | 终端输入 `java -version` |
| Node.js | 18 | https://nodejs.org （选 LTS 版本） | 终端输入 `node -v` |
| MySQL | 8.0 | https://dev.mysql.com/downloads | 终端输入 `mysql -u root -p` |

> Node.js 安装后 npm 自动可用（`npm -v` 验证）。  
> MySQL 安装时**记住你设的 root 密码**，下一步要用。

---

## 第二步：克隆项目 & 安装前端依赖

```powershell
# 从群文件下载压缩包解压，或 git clone
cd "Second-hand Transaction"

# 安装前端依赖（只需执行一次）
cd frontend
npm install
cd ..
```

---

## 第三步：配置数据库

### 3.1 启动 MySQL

Windows 上 MySQL 装完默认自动启动。验证方法：

```powershell
mysql -u root -p
# 输入你安装时设的密码，能进入 MySQL 命令行就说明 OK
```

如果服务没启动：

```powershell
# 管理员 PowerShell 执行
Start-Service MySQL80
```

### 3.2 创建数据库

```sql
-- 在 MySQL 命令行中执行
CREATE DATABASE IF NOT EXISTS secondhand DEFAULT CHARACTER SET utf8mb4;
EXIT;
```

### 3.3 修改项目里的数据库密码

打开 `backend\src\main\resources\application.yml`，找到这两行：

```yaml
datasource:
  username: root
  password: 123456     # ← 改成你 MySQL 的 root 密码
```

**只改 password，其他配置不要动。**

---

## 第四步：启动项目

需要开**两个终端窗口**，一个跑后端，一个跑前端。

### 4.1 终端1 — 启动后端

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

首次运行会下载 Maven 依赖（约 3-5 分钟），之后启动就快了。  
看到下面这行说明启动成功：

```
Started App in x.xxx seconds
```

> 后端运行在 **http://localhost:8088**

### 4.2 终端2 — 启动前端

```powershell
cd frontend
npm run dev
```

看到下面这行说明启动成功：

```
➜  Local:   http://localhost:5173/
```

> 前端运行在 **http://localhost:5173**

---

## 第五步：验证是否成功

### 1. 打开浏览器访问 http://localhost:5173

能看到首页 → 前端正常 ✓

### 2. 注册一个账号

首页 → 登录 → 注册 → 填用户名/密码/昵称 → 注册成功自动登录 ✓

### 3. 发布一个商品试试

导航栏 → 发布 → 填信息 → 提交 → 数据库读写正常 ✓

### 4. 管理员账号

系统启动时自动创建：

| 账号 | 密码 |
|------|------|
| admin | admin123 |

管理后台：http://localhost:5173/admin

---

## 端口一览

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端页面 | http://localhost:5173 | 浏览器打开这个 |
| 后端 API | http://localhost:8088 | 前端自动代理到这个地址 |
| Swagger 文档 | http://localhost:8088/swagger | 接口调试用 |
| MySQL | localhost:3306 | 数据库 |

---

## 常见问题

### Q1：后端启动报 "Access denied for user 'root'@'localhost'"

密码不对。检查 `application.yml` 中的 `password` 是否和 MySQL root 密码一致。

### Q2：后端启动报 "Unknown database 'secondhand'"

数据库还没创建。执行第三步的 `CREATE DATABASE secondhand;`

### Q3：`mvnw.cmd` 下载依赖很慢

Maven 默认从国外下载。配置阿里云镜像加速，在 `C:\Users\你的用户名\.m2\` 下创建 `settings.xml`：

```xml
<settings>
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <name>aliyun</name>
      <url>https://maven.aliyun.com/repository/public</url>
      <mirrorOf>central</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

### Q4：`npm install` 下载很慢

换淘宝镜像：

```powershell
npm config set registry https://registry.npmmirror.com
```

然后重新 `npm install`。

### Q5：端口被占用

```powershell
# 查看谁占用了端口
netstat -ano | findstr :8088
netstat -ano | findstr :5173

# 杀掉进程（替换为上面查到的 PID）
taskkill /F /PID 12345
```

### Q6：前端能打开但登录报错

后端没启动或者启动失败了。检查启动后端的终端有没有报错，浏览器访问 http://localhost:8088/api/products?size=1 看是否返回数据。

### Q7：想让同局域网其他人访问我的前端

前端已配置 `host: 0.0.0.0`，同 WiFi 下其他人用 `http://你的IP:5173` 即可访问。

```powershell
# 查自己的局域网 IP
ipconfig | findstr IPv4
```
