# v1.6.3 更新日志

**发布日期：** 待发布

**类型：** Bug Fix

**依赖升级：** 无（`simple-elasticsearch-search-core` 版本不变，仍为 1.0.10）

---

## Bug 修复

### `DowngradeFailedException` 不触发 Error Event

**问题**：`AbstractExecutor.execute()` 只 catch `IOException`，`DowngradeFailedException`（RuntimeException）在所有降级层级耗尽后抛出，绕过了 `onExecutionError` 回调，导致 `EsQueryErrorEvent` / `EsAggErrorEvent` 不发布，audit 和 metrics 感知不到降级彻底失败的情况。

**修复**：在 `execute()` 中补充 `catch (DowngradeFailedException e)`，调用 `onExecutionError` 后重新抛出。

```java
} catch (DowngradeFailedException e) {
    log.error("Execution failed after all downgrade levels: index={}", getIndex(request), e);
    onExecutionError(request, e);
    throw e;
}
```

**影响范围**：`QueryExecutor`、`AggExecutor` 均继承 `AbstractExecutor`，两者的降级彻底失败场景均补齐了 Error Event 发布。

---

## 升级说明

**兼容性**：与 v1.6.2 完全兼容，无破坏性 API 变更。

**无需升级 `simple-elasticsearch-search-core`**，core 版本保持 1.0.10。

```gradle
dependencies {
    implementation 'io.github.surezzzzz:simple-elasticsearch-search-starter:1.6.3'
}
```
