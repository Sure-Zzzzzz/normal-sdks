# v1.1.2 更新日志

**发布日期：** 2026-05-12

**类型：** Bug Fix

---

## Bug 修复

### 1. EXPECT_LOGIC_OR_END 中 `sameFieldOrMode` 误触发

**症状**：`handleExpectLogicOrEnd` 中 LOGIC(OR) 分支错误设置 `sameFieldOrMode=true`，导致后续独立 LOGIC token（如 "并且"）之后的字段被误判为同字段 OR 值而非新字段，BETWEEN 等场景解析异常。

**修复**：删除 `handleExpectLogicOrEnd` 中多余的 `sameFieldOrMode` 设置。`sameFieldOrMode` 仅应在 `handleExpectValue` 中遇到 OR 时设置。

### 2. BETWEEN "在X到Y之间" 解析返回 null

**症状**：`"年龄在18到30之间"` 解析后 condition 为 null，而 `"年龄介于25,45"` 正常。

**根因**：HanLP 分词后 "在" 是 UNKNOWN token，`isInOperatorContext` 只识别 IN 模式（"在...中"），无法识别 BETWEEN 模式（"在X到Y之间"）。"在" 被 `collectFieldName` 吞入字段名，导致解析失败。

**修复**：在 `normalizeTokens` 预处理管道末尾新增 `normalizeBetweenPattern` 步骤，将 `UNKNOWN("在") + NUMBER + UNKNOWN("到"/"至") + NUMBER + [UNKNOWN("之间")]` 模式归一化为 `BETWEEN操作符 + NUMBER + NUMBER`，与 "介于X,Y" 走相同的解析路径。

---

## 升级说明

**兼容性**：与 v1.1.1 完全兼容，无 API 变更。

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:natural-language-parser-starter:1.1.2'
}
```
