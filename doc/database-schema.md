# 数据库设计文档

## 1. 概述

- **数据库**: H2 (嵌入式，文件模式)
- **文件路径**: `~/.secondhand/data.mv.db`
- **连接 URL**: `jdbc:h2:file:~/.secondhand/data;MODE=MySQL`
- **控制台**: `http://localhost:8088/h2-console`
- **Schema 管理**: JPA `ddl-auto: update`（开发阶段自动更新表结构）

---

## 2. ER 图

```
┌──────────┐       ┌───────────────────┐
│   users   │       │  user_identities   │
├──────────┤       ├───────────────────┤
│ id    PK │←──────│ user_id       FK   │
│ nickname │  1:N  │ identity_type      │
│ pwd_hash │       │ identifier         │
│ status   │       │ verified           │
│ created  │       │ created_at         │
│ updated  │       │ updated_at         │
└──────────┘       └───────────────────┘
                         UNIQUE(type, identifier)

┌──────────────┐
│   products    │
├──────────────┤
│ id       PK   │
│ seller_id FK  │──→ users.id
│ title         │
│ price_cent    │
│ cover_img_url │
│ description   │
│ status        │  DRAFT | ON_SALE | OFF_SALE
│ created_at    │
│ updated_at    │
└──────────────┘

┌──────────────┐       ┌──────────────┐
│    orders     │       │ order_events  │
├──────────────┤       ├──────────────┤
│ id       PK   │←──────│ order_id  FK  │
│ buyer_id  FK  │  1:N  │ from_status   │
│ seller_id FK  │       │ to_status     │
│ product_id FK │       │ note          │
│ amount_cent   │       │ created_at    │
│ status        │       └──────────────┘
│ receiver_name │
│ receiver_phone│       ┌──────────────┐
│ receiver_addr │       │  shipments    │
│ created_at    │       ├──────────────┤
│ updated_at    │       │ id       PK   │
└──────────────┘       │ order_id  FK  │ 1:1
                       │ carrier_code  │
                       │ tracking_no   │
┌──────────────────┐   │ status        │
│after_sale_requests│   │ created_at    │
├──────────────────┤   │ updated_at    │
│ id           PK   │   └──────────────┘
│ order_id     FK   │──→ orders.id
│ request_type      │
│ reason            │
│ status            │
│ requested_at      │
│ handled_at        │
│ created_at        │
│ updated_at        │
└──────────────────┘
```

---

## 3. 表结构详情

### 3.1 `users` — 用户表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 用户 ID |
| nickname | VARCHAR(50) | NOT NULL | 昵称 |
| password_hash | VARCHAR(255) | NOT NULL | BCrypt 哈希 |
| status | VARCHAR(16) | NOT NULL | ACTIVE / DISABLED |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL | 更新时间 |

### 3.2 `user_identities` — 用户登录标识表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| user_id | BIGINT | FK → users.id, NOT NULL | 所属用户 |
| identity_type | VARCHAR(16) | NOT NULL | PHONE / EMAIL |
| identifier | VARCHAR(128) | NOT NULL | 手机号或邮箱 |
| verified | BOOLEAN | NOT NULL | 是否已验证 |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |
| | | **UNIQUE** (identity_type, identifier) | 同类型同标识唯一 |

### 3.3 `products` — 商品表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| seller_id | BIGINT | FK, NOT NULL | 卖家用户 ID |
| title | VARCHAR(100) | NOT NULL | 商品标题 |
| price_cent | INTEGER | NOT NULL | 价格（分） |
| cover_image_url | VARCHAR(512) | | 封面图 URL |
| description | TEXT | | 商品描述 |
| status | VARCHAR(16) | NOT NULL | DRAFT / ON_SALE / OFF_SALE |
| created_at | TIMESTAMP | | |
| updated_at | TIMESTAMP | | |

### 3.4 `orders` — 订单表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| buyer_id | BIGINT | FK, NOT NULL | 买家用户 ID |
| seller_id | BIGINT | FK, NOT NULL | 卖家用户 ID |
| product_id | BIGINT | FK, NOT NULL | 商品 ID |
| amount_cent | INTEGER | NOT NULL | 订单金额（分） |
| status | VARCHAR(16) | NOT NULL | CREATED → PAID → SHIPPED → COMPLETED / CANCELLED |
| receiver_name | VARCHAR | | 收货人 |
| receiver_phone | VARCHAR | | 收货电话 |
| receiver_address | VARCHAR | | 收货地址 |
| created_at | TIMESTAMP | | |
| updated_at | TIMESTAMP | | |

### 3.5 `order_events` — 订单事件日志表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| order_id | BIGINT | FK, NOT NULL | 订单 ID |
| from_status | VARCHAR | | 变更前状态 |
| to_status | VARCHAR | | 变更后状态 |
| note | VARCHAR | | 事件备注 |
| created_at | TIMESTAMP | | |

### 3.6 `shipments` — 物流运单表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| order_id | BIGINT | FK, NOT NULL, UNIQUE | 订单 ID (1:1) |
| carrier_code | VARCHAR | | 快递公司编码 |
| tracking_no | VARCHAR | | 快递单号 |
| status | VARCHAR | | CREATED / IN_TRANSIT / DELIVERED |
| created_at | TIMESTAMP | | |
| updated_at | TIMESTAMP | | |

### 3.7 `after_sale_requests` — 售后申请表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| order_id | BIGINT | FK, NOT NULL | |
| request_type | VARCHAR | | REFUND / RETURN_REFUND |
| reason | VARCHAR | | 申请原因 |
| status | VARCHAR | | REQUESTED / APPROVED / REJECTED / CLOSED |
| requested_at | TIMESTAMP | | 申请时间 |
| handled_at | TIMESTAMP | | 处理时间 |
| created_at | TIMESTAMP | | |
| updated_at | TIMESTAMP | | |

---

## 4. 状态枚举

### 4.1 UserStatus

| 值 | 说明 |
|------|------|
| `ACTIVE` | 正常 |
| `DISABLED` | 禁用 |

### 4.2 IdentityType

| 值 | 说明 |
|------|------|
| `PHONE` | 手机号 |
| `EMAIL` | 邮箱 |

### 4.3 ProductStatus

| 值 | 说明 |
|------|------|
| `DRAFT` | 草稿 |
| `ON_SALE` | 在售 |
| `OFF_SALE` | 下架 |

### 4.4 OrderStatus 流转

```
CREATED ──→ PAID ──→ SHIPPED ──→ COMPLETED
   │                     │
   └─────→ CANCELLED ←──┘ (通过售后审批)
```

### 4.5 ShipmentStatus

| 值 | 说明 |
|------|------|
| `CREATED` | 已创建 |
| `IN_TRANSIT` | 运输中 |
| `DELIVERED` | 已送达 |

### 4.6 AfterSaleStatus / AfterSaleType

| 状态 | 说明 | 类型 | 说明 |
|------|------|------|------|
| `REQUESTED` | 已申请 | `REFUND` | 仅退款 |
| `APPROVED` | 已通过 | `RETURN_REFUND` | 退货退款 |
| `REJECTED` | 已拒绝 | | |
| `CLOSED` | 已关闭 | | |

---

## 5. 索引设计

| 表 | 索引 | 类型 |
|------|------|------|
| user_identities | (identity_type, identifier) | UNIQUE |
| products | (status) | 普通索引（用于列表查询） |
| orders | (buyer_id) | 外键索引 |
| orders | (seller_id) | 外键索引 |
| order_events | (order_id) | 外键索引 |
| shipments | (order_id) | UNIQUE + 外键索引 |

---

## 6. 数据迁移说明

当前使用 `ddl-auto: update`，JPA 启动时自动检测实体变更并更新表结构：

- **新增列** → 自动 ALTER TABLE ADD COLUMN
- **修改列类型** → H2 的 ALTER COLUMN（类似 MySQL MODIFY）
- **新增约束** → 自动 ADD CONSTRAINT

⚠️ **生产环境警告**：`ddl-auto: update` 不适合生产，应使用 Flyway 或 Liquibase 做版本化数据库迁移。
