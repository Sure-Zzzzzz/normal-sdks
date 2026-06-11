# v1.0.1 更新日志

**发布日期：** 2026-06-11

**依赖升级：**

| 依赖 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-elasticsearch-search-core` | 1.0.10 | 1.0.11 |
| `simple-elasticsearch-search-starter`（测试依赖） | 1.6.5 | 1.6.6 |

---

## Bug Fix

### Error 事件 downgradeLevel 硬编码为 0

**问题**：`onQueryFailure` / `onAggFailure` 在采集失败指标时，`downgradeLevel` 标签硬编码为 `"0"`。当降级后执行失败时（如 `DOWNGRADE_FAILED`），实际失败级别丢失，metrics 无法区分"未降级失败"和"降级后失败"。

**修复**：从 `EsQueryErrorEvent.getContext()` / `EsAggErrorEvent.getContext()` 读取 `downgradeLevel`。若 context 为 null（校验/路由阶段失败），fallback 到 `"0"`：

```java
String downgradeLevel = event.getContext() != null
        ? String.valueOf(event.getContext().getDowngradeLevel())
        : SimpleElasticsearchSearchMetricsConstant.DOWNGRADE_LEVEL_ZERO;
```

---

## 升级说明

**兼容性**：与 v1.0.0 完全兼容，无 API 变更。
