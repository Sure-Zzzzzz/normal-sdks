# Simple AKSK Server 数据库初始化指南

## 概述

本目录包含 Simple AKSK Server 所需的数据库初始化SQL脚本，基于 **Spring Authorization Server 0.4.0** 标准表结构并进行了扩展。

## SQL文件说明

| 文件名 | 说明 | 是否必需 |
|--------|------|----------|
| `00_database.sql` | 数据库创建脚本 | ✅ 必需 |
| `01_schema.sql` | 表结构创建脚本 | ✅ 必需 |

### 00_database.sql - 数据库创建

**内容**：
- 创建数据库 `sure_auth_aksk`（可自定义）
- 设置字符集为 `utf8mb4`

### 01_schema.sql - 表结构

**内容**：
- Spring Authorization Server 0.4.0 标准表：
  - `oauth2_registered_client` - 客户端注册信息
  - `oauth2_authorization` - 授权信息
- 扩展字段：
  - `owner_user_id` - 所属用户ID（用户级AKSK）
  - `owner_username` - 所属用户名（用户级AKSK）
  - `client_type` - 客户端类型（1=平台级，2=用户级）
  - `enabled` - 是否启用（1=启用，0=禁用）
- 索引优化

---

## 执行步骤

### 前置要求

- MySQL 5.7+ 或 MySQL 8.0+
- 具有DDL权限的数据库用户
- 已安装MySQL客户端工具

### 方式1：逐个执行（推荐）

```bash
# 1. 创建数据库
mysql -u your_username -p < 00_database.sql

# 2. 创建表结构
mysql -u your_username -p sure_auth_aksk < 01_schema.sql
```

### 方式2：一键执行

```bash
cat 00_database.sql 01_schema.sql | mysql -u your_username -p
```

### 方式3：MySQL命令行内执行

```sql
-- 登录MySQL
mysql -u your_username -p

-- 执行脚本
SOURCE /path/to/00_database.sql;
SOURCE /path/to/01_schema.sql;
```

---

## 验证安装

执行以下SQL验证表结构：

```sql
-- 1. 查看数据库
SHOW DATABASES LIKE 'sure_auth_aksk';

-- 2. 切换数据库
USE sure_auth_aksk;

-- 3. 查看表结构
SHOW TABLES;

-- 4. 验证扩展字段
DESC oauth2_registered_client;

-- 5. 查看索引
SHOW INDEX FROM oauth2_registered_client;
```

**预期结果**：
- 2个表：`oauth2_registered_client`, `oauth2_authorization`
- `oauth2_registered_client` 包含扩展字段：`owner_user_id`, `owner_username`, `client_type`, `enabled`

---

## 重要注意事项

### 1. 数据库名称
- 默认数据库名：`sure_auth_aksk`
- 如需修改，请在 `00_database.sql` 中修改 `CREATE DATABASE` 语句
- 执行后续SQL前，确保已 `USE` 正确的数据库

### 2. 字符集
- 建议使用 `utf8mb4` 字符集
- 支持完整的Unicode字符（包括emoji）

### 3. 权限要求
- 数据库用户需要 `CREATE`, `ALTER`, `INDEX` 权限
- 生产环境建议使用专用数据库用户

### 4. 备份
- 执行DDL操作前，请先备份现有数据库
- 如果表已存在，脚本会先删除（`DROP TABLE IF EXISTS`）

---

## 故障排查

### 问题1：表已存在

**错误**: `Table 'oauth2_registered_client' already exists`

**解决**: 脚本已包含 `DROP TABLE IF EXISTS`，如仍报错，请手动删除表或检查权限

### 问题2：数据库不存在

**错误**: `Unknown database 'sure_auth_aksk'`

**解决**: 确保先执行 `00_database.sql` 创建数据库

### 问题3：字符集问题

**错误**: 中文乱码

**解决**:
```sql
SET NAMES utf8mb4;
ALTER DATABASE sure_auth_aksk CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 问题4：权限不足

**错误**: `Access denied`

**解决**: 授予用户必要权限
```sql
GRANT CREATE, ALTER, INDEX, SELECT ON sure_auth_aksk.* TO 'your_username'@'localhost';
FLUSH PRIVILEGES;
```

---

## 下一步

数据库初始化完成后：

1. ✅ 配置 `application.yml` 中的数据库连接信息
2. ✅ 启动 Simple AKSK Server 应用
3. ✅ 通过 Admin 管理页面或 REST API 创建和管理 AKSK
4. ✅ 运行端到端测试验证功能

---

## 参考资料

- [Spring Authorization Server 官方文档](https://docs.spring.io/spring-authorization-server/docs/0.4.0/reference/html/index.html)
- [OAuth 2.0 Client Credentials Grant](https://datatracker.ietf.org/doc/html/rfc6749#section-4.4)
- [BCrypt密码加密](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html#authentication-password-storage-bcrypt)
