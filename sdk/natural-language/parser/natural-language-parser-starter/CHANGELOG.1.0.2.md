# Changelog

## [1.0.2] - 2026-01-03

### 🐛 Bug修复

#### 索引名提取bug修复

修复了 `IndexExtractor` 无法正确提取包含逻辑关键词（如 "or"）的索引名的问题。

**问题描述：**

当索引名包含子串与逻辑关键词相同时（如 "user_behavior" 包含 "or"），HanLP 分词器会错误地将这些子串识别为 LOGIC 类型的 token，导致 IndexExtractor 提取失败。

**示例：**
```
查询: "查询user_behavior索引"
分词结果: [UNKNOWN 'user_behavi', LOGIC 'or', UNKNOWN '索引']
之前: indexHint = null  ❌
修复后: indexHint = user_behavior  ✅
```

**根本原因：**

HanLP 将 "user_behavior" 拆分为 "user_behavi" + "or"，并将 "or" 识别为 LOGIC 类型。旧的 IndexExtractor 代码只接受 UNKNOWN 类型的 token 作为索引名的一部分，当遇到 LOGIC 类型时会停止合并，导致索引名提取失败。

**修复方案：**

修改 `IndexExtractor.java` 中的 `searchBackwardForIndexName()` 和 `searchForwardForIndexName()` 方法：

- **旧逻辑**: 只接受 `TokenType.UNKNOWN` 类型的 token
- **新逻辑**: 接受 `TokenType.UNKNOWN`、`TokenType.LOGIC`、`TokenType.OPERATOR` 类型的 token

这样即使索引名中的某些部分被错误识别为 LOGIC 或 OPERATOR 类型，也能正确合并成完整的索引名。

**影响的文件：**

- `IndexExtractor.java` (lines 63-116, 122-160)
  - 修改了 `searchBackwardForIndexName()` 方法
  - 修改了 `searchForwardForIndexName()` 方法
  - 添加了详细的注释说明

**升级说明：**

- ✅ 完全向后兼容，无需修改代码
- ✅ 无破坏性变更
- ✅ 推荐升级以支持更多索引命名场景
