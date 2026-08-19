# Simple AKSK Server 3.0.0 数据库初始化指南

## 概述

本目录包含 Simple AKSK Server 3.0.0 的数据库脚本。表结构基于 **Spring Authorization Server 0.4.1**，并增加 AKSK Client 元数据与应用授权投影。

## SQL 文件说明

| 文件名 | 使用场景 | 说明 |
|--------|----------|------|
| `00_database.sql` | 首次部署 | 创建默认数据库，可按部署需要自行调整数据库名 |
| `01_schema_3.0.0.sql` | 首次部署或允许重建数据的环境 | 3.0.0 完整初始化脚本，会重建 AKSK 相关表 |
| `02_upgrade_3.0.0.sql` | 从 2.x 升级 | 保留历史 Client 与 Token 表，新建应用授权投影表与查询索引，仅执行一次 |
| `03_install_3.0.0.md` | 新装 | 基础设施、精确依赖、初始化和首个 Client 准入闭环 |
| `04_upgrade_2.x_to_3.0.0.md` | 升级 | 备份、停写、一次迁移、历史 Token 处置与回退边界 |
| `05_operations_3.0.0.md` | 运维 | Redis/JWE、应用授权、故障处置、日志与 IAM 可选协作边界 |
| `06_release_acceptance_3.0.0.md` | 发布验收 | 新装、升级、Token、并发、管理安全、IAM 和质量门禁 |
| `07_dependency_resolution_3.0.0.md` | 依赖收口 | Central 解析、同步切换上游依赖与干净消费者验证 |

> 历史 `01_schema.sql` 仅保留给已冻结的旧版本参考，不用于 3.0.0 新部署。

## 3.0.0 表结构

| 表 | 用途 |
|----|------|
| `oauth2_registered_client` | Spring Authorization Server 注册 Client，包含 AKSK 所属用户、类型与启用状态等扩展字段 |
| `oauth2_authorization` | Spring Authorization Server 授权与 Token 持久化信息 |
| `aksk_application_authorization` | AKSK Server 自主维护的应用授权投影，保存角色、页面权限、精确 API permission、`DataGrantDocument`、准入与撤销状态 |

`aksk_application_authorization` 中的 `authorization_version` 是对外可见的授权快照版本；`lock_version` 是 JPA 持久化乐观锁版本。两者职责不同，不能混用。

## 执行步骤

### 前置要求

- MySQL 5.7+ 或 MySQL 8.0+
- 具有 DDL 权限的数据库用户
- 可执行 MySQL 脚本的部署工具或客户端

### 首次部署

```bash
mysql -u <database-user> -p <database-name> < 01_schema_3.0.0.sql
```

`01_schema_3.0.0.sql` 含有 `DROP TABLE IF EXISTS`，仅能用于首次初始化或确认允许清空 AKSK 数据的环境。

### 从 2.x 升级

先完成数据库备份并停止 2.x 服务写入，确认目标库尚不存在 `aksk_application_authorization` 后，执行一次：

```bash
mysql -u <database-user> -p <database-name> < 02_upgrade_3.0.0.sql
```

该脚本保留 `oauth2_registered_client` 与 `oauth2_authorization` 的存量数据，只新增 3.0 所需的应用授权投影表与查询索引。它不会从旧 `scope` 推导角色、页面权限、API permission 或 DATA grant，也不会自动准入任何 Client。

升级至 3.0 服务后，管理员必须逐 Client 完成以下顺序：先处理该 Client 的全部 2.x 存量活跃 Token，再配置完整应用授权，最后显式准入。未配置、未准入或已撤销的 Client 不能签发有效 Token，存量 Token 的内省也会被判为 inactive。

不要省略升级期 Token 处置：内省会按当前授权投影重建授权。进入 3.0 后，完整替换或撤销应用授权会在同一事务中撤销该 Client 的活跃 Token，因此替换前 Token 不会获得新投影。该升级脚本不是可重复执行的迁移工具，已部署历史版本的生产数据迁移必须先完成评估和备份，不得直接套用初始化脚本。完整步骤见 [2.x 升级手册](04_upgrade_2.x_to_3.0.0.md)。

## 安装验证

```sql
SHOW TABLES;
DESC aksk_application_authorization;
SHOW INDEX FROM aksk_application_authorization;
```

预期可见三张表，并且 `aksk_application_authorization` 含有 `authorization_version` 与 `lock_version` 字段。

## 下一步

1. 配置应用的数据源、Redis 与 JWE 密钥材料。
2. 启动 Simple AKSK Server。
3. 通过 Admin 页面创建 Client，并配置应用授权投影。
4. 只有完成显式准入后，才请求带 `aksk_authorization` 快照的 Token。
5. 使用受保护的管理 REST API 时，同时配置精确 API permission 与 `DataAccessPlan`。

## 参考资料

- [Spring Authorization Server 0.4.1](https://docs.spring.io/spring-authorization-server/docs/0.4.1/reference/html/index.html)
- [OAuth 2.0 Client Credentials Grant](https://datatracker.ietf.org/doc/html/rfc6749#section-4.4)
- [BCrypt 密码存储](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html#authentication-password-storage-bcrypt)
