# v1.1.3 更新日志

**发布日期：** 2026-05-13

**类型：** Bug Fix

---

## Bug 修复

### IndexExtractorPlugin 无法提取通配符索引名

**症状**：`"查询app_access_log-*索引"` 提取 indexHint 为 null，导致 search-starter 抛出"未指定索引"异常。

**根因**：HanLP 将 `app_access_log-*` 拆分为 `["app_access_log-"(UNKNOWN), "*"(UNKNOWN)]` 两个 token。`isValidIndexNameToken` 的正则不包含 `*`，导致 `*` 被视为非索引名 token，backward search 在此处中断，索引名无法完整收集。

**修复**：`isValidIndexNameToken` 正则增加 `*`，支持 ES 通配符索引名模式（如 `app_access_log-*`）。

---

## 升级说明

**兼容性**：与 v1.1.2 完全兼容，无 API 变更。

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:natural-language-parser-starter:1.1.3'
}
```
