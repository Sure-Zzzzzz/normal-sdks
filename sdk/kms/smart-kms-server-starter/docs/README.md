# Smart KMS Server 数据库脚本

## 执行顺序

本目录 SQL 只包含 KMS 表结构。首次安装或全新、可销毁测试库按以下顺序执行：

1. 创建并切换到 KMS 专属 MySQL 数据库。
2. 执行 [01_schema.sql](01_schema.sql)。

```bash
mysql -u your_username -p your_kms_database < 01_schema.sql
```

## 破坏性警告

[01_schema.sql](01_schema.sql) 会先删除全部 `smart_kms_*` 同名表及其中所有数据，再重建六张 KMS 表。它只适用于首次安装或明确可销毁的环境；**生产环境已存在表时禁止重复执行**。

Starter 不会自动执行、迁移或修复 DDL，也不集成 Flyway 或 Liquibase。生产环境的结构升级必须经过独立变更评审、备份和恢复验证。

## 前提

- MySQL 5.7 或兼容版本。
- 数据库使用 `utf8mb4` 字符集与 `utf8mb4_unicode_ci` 排序规则。
- 执行账号具有目标数据库的建表、删表、建索引及外键权限。
- KMS 使用专属数据库和最小权限数据库账号；业务服务不得访问任何 `smart_kms_*` 表。

## 表清单

| 表 | 用途 |
| --- | --- |
| `smart_kms_key` | 逻辑密钥元数据与状态 |
| `smart_kms_key_version` | 版本状态及仅可信边界内可见的材料 |
| `smart_kms_key_policy` | 精确 allow-only 密钥策略 |
| `smart_kms_idempotency_record` | 管理操作幂等摘要与安全响应快照 |
| `smart_kms_destruction_job` | 销毁调度、领取租约与完成事实 |
| `smart_kms_destruction_worker_state` | 实例级 worker 恢复与停止领取状态 |

所有时间由 JDBC 通过数据库 UTC 时间获取。应用 JVM 时间只用于调度等待，不能用于策略、幂等、到期或租约安全判断。
