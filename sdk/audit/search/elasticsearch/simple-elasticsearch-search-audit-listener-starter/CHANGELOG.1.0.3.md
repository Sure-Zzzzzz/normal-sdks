# 更新日志

## v1.0.3 (2026-06-11)

**依赖升级：**

| 依赖 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-elasticsearch-search-core` | 1.0.10 | 1.0.11 |
| `simple-elasticsearch-search-starter`（测试依赖） | 1.6.4 | 1.6.6 |

**新增功能：**

- `EsAuditRecord` 新增 `countOnly` 字段（Boolean）：区分 count 查询与普通查询，供审计日志统计 count 流量
- `EsAuditEventListener.buildQueryRecord()`：从 `event.getRequest().getCountOnly()` 填充
- `EsAuditEventListener.buildQueryErrorRecord()`：从 `event.isCountOnly()` 填充

**Bug Fix：**

- `EsQueryErrorEvent` / `EsAggErrorEvent` Error 事件现已携带 `ExecutionContext`（search-core 1.0.11 新增），
  `EsAuditEventListener` 从 `event.getContext()` 读取 `downgradeLevel`，而非固定 0
- 聚合 Error 事件同样从 `event.getContext()` 读取 `downgradeLevel`

**说明：**

- 现有 `EsAuditHandler` 实现无需修改，`countOnly` 新增字段通过 builder 追加，向后兼容
- `countOnly` 仅在查询事件中有值，聚合事件为 null（聚合无 countOnly 概念）
- `downgradeLevel` 在 context 非 null 时从事件读取，context 为 null 时保持 0（校验/路由阶段失败时）

---
