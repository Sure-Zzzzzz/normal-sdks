# simple-feedback-server-starter 迁移目录

仅追加式迁移：每版一个 `V<n>__<描述>.sql`，按序号升序执行一次；不修改已发布迁移，不重放 `../schema.sql`。

| 迁移 | 版本 | 内容 |
|---|---|---|
| （基线） | 1.0.0 | `../schema.sql` 三表全量建表；`feedback_submission.first_response` 已含在基线（全新库直接执行 schema.sql） |
| — | 1.0.1 | 无数据库结构迁移；领域错误码、REST 错误映射、查询优化和客户端能力不改变表结构 |

## 升级路径

- 1.0.0 全新部署：执行 `../schema.sql`。
- 已有 1.0.0 正式基线升级到 1.0.1：无需执行新增 SQL，直接升级制品；仍按本目录规则保留已执行的 1.0.0 基线记录。
- 早期联调库（基线前手工建的 `feedback_submission`）：补 `ALTER TABLE feedback_submission ADD COLUMN first_response TEXT COMMENT '首成功响应快照（重放依据）' AFTER idempotency_digest;` 即对齐基线。
