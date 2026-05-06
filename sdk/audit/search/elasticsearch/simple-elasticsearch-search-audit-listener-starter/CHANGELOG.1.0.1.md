# 更新日志

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