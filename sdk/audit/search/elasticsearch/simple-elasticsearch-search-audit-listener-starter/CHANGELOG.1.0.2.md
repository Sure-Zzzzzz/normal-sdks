# 更新日志

## v1.0.2 (2026-05-28)

**依赖升级：**

| 依赖 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-elasticsearch-search-core` | 1.0.8 | 1.0.10 |
| `simple-elasticsearch-search-starter`（测试依赖） | 1.6.3 | 1.6.4 |
| `simple-aksk-resource-server-starter`（测试依赖） | 1.0.6 | 2.0.0 |
| `simple-aksk-resource-audit-listener-starter`（测试依赖） | 1.0.0 | 2.0.0 |

**新增功能：**

- 新增监听 `EsQueryErrorEvent` / `EsAggErrorEvent`，查询/聚合失败时也生成审计记录
- `EsAuditRecord` 新增字段：
  - `result`：操作结果（`success` / `failure`）
  - `downgradeLevel`：降级级别（0=未降级，来自 ExecutionContext；ErrorEvent 固定为 0）
  - `sourceType`：来源端点类型（QUERY_API / NL_API / EXPRESSION_API；ErrorEvent 来自 `EsQueryErrorEvent.sourceType`）
  - `errorMessage`：错误信息（仅 `result=failure` 时有值）

**缺陷修复：**

- 修复 `EsAuditEventListener` provider 为 null 时的 NPE：`buildXxxRecord()` 方法中 lambda 体直接访问 `userProvider` / `traceIdProvider`，当两者为 null 时在 `supplier.get()` 调用时即抛出 NPE，`safeGet` 的 try-catch 无法捕获。现改为先判空再调用 safeGet。

**测试套件重新设计（aksk 2.0）：**

- 移除废弃的 `simple-aksk-security-context-starter`（aksk 2.0 不再维护）
- 删除 4 个已失效的测试类：Header 认证测试、JWT 认证测试、aksk-event Header/JWT 测试
- 重命名 `EsAuditListenerEndToEndTest` → `EsAuditEventListenerUnitTest`（明确为单元测试，非 E2E）
- 保留 `EsAuditIntrospectIntegrationTest` 作为唯一集成测试
- 精简测试配置文件，移除已删除测试对应的索引配置

**说明：**

- 现有 `EsAuditHandler` 实现无需修改，新字段通过 builder 追加，向后兼容
- ErrorEvent 不携带 `ExecutionContext`，因此 `actualIndices` 为 null，`downgradeLevel` 固定为 0
- `sourceType` 由 `search-core 1.0.10` 起直接写入 ErrorEvent，无需从 request 二次提取

---

## v1.0.1 (2026-05-06)

**依赖升级：**

| 依赖 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-elasticsearch-search-core` | 1.0.1 | 1.0.8 |

**说明：**

`simple-elasticsearch-search-core` 1.0.8 相比 1.0.1，新增了 `PaginationInfo.searchAfter`、`QueryRequest.collapse`、`AggRequest.aggregations` 等字段。本模块监听的是 `EsQueryEvent` / `EsAggEvent`，事件模型本身未变，仅依赖版本对齐。

---

## v1.0.0 (2026-01-01)

初始版本。