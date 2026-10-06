# DDL 执行说明

> 严重警告：`01_schema.sql` 会先执行 `DROP TABLE IF EXISTS`，删除同名表及全部数据。该脚本仅用于首次安装或确认可销毁的环境；生产环境已有表时禁止重复执行。

## 前提

- MySQL 5.7 或更高版本。
- InnoDB 存储引擎。
- 数据库和连接使用 `utf8mb4`。

## 执行顺序

当前版本只有一个脚本，确认目标库无需要保留的同名表后执行 `01_schema.sql`。

starter 不自动建表、不自动执行 DDL，也不集成 Flyway 或 Liquibase。部署前必须由数据库变更流程手动执行脚本。

如果配置 `table-name` 为非默认值，必须同步修改脚本中的表名后再执行；表名只允许字母、数字和下划线，最大 64 字符。

## 与 javax 线表结构的关系

本目录 DDL 与 javax 线 `simple-kafka-outbox-starter` 的 `docs/01_schema.sql` 完全一致：两条运行线共用同一张 Outbox 表结构。任何一侧调整表结构时，另一侧必须同步修改并在各自 CHANGELOG 中声明升级脚本；表结构差异会导致快照读写失败。
