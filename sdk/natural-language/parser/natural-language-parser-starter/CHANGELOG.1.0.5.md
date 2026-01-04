# v1.0.5 更新日志

**发布日期：** 2026-01-04

**类型：** 功能增强 + Bug修复版本

---

## ✨ 新功能

### Feature 1: DateRange 时间范围支持（含时间组件）✅

新增全局时间范围过滤功能，支持日期和时间（时分秒）组件，用于表达整体的时间过滤条件。

**支持的关键词：**

- 中文：`时间范围`、`日期范围`
- 英文：`dateRange`、`date range`、`time range`、`timeRange`、`date_range`、`time_range`（大小写不敏感）

**支持的日期时间格式：**

- 纯日期：`YYYY-MM-DD`、`YYYY/MM/DD`、`YYYY年MM月DD日`、`YYYYMMDD`（时间默认 00:00:00）
- 带时间：`YYYY-MM-DD HH:mm:ss`、`YYYY-MM-DD HH:mm`、ISO 8601 格式等

**示例：**

- `时间范围2025-01-01到2026-01-01` → from=2025-01-01T00:00:00, to=2026-01-01T00:00:00
- `时间范围2025-01-01 10:00:00到2026-01-01 20:00:00` → from=2025-01-01T10:00:00, to=2026-01-01T20:00:00

**时区：** 使用 `LocalDateTime`（不含时区），按服务器本地时区解释，调用方负责时区转换。

**设计考量：** 只提取带有明确"时间范围"/"日期范围"关键词的表达，避免与条件查询冲突。

---

## 🐛 Bug修复

### Bug 1: 索引名数字前缀截断 ✅

索引名包含数字前缀时被错误截断（如 `db01_user_log-*` 变成 `_user_log-*`）。

**修复：** IndexExtractor 添加 NUMBER、FIELD_CANDIDATE、VALUE 类型token支持。

**影响：** 修复所有包含数字前缀的索引名识别。

---

### Bug 2: DateRange与Condition重复解析 ✅

时间范围同时出现在 `dateRange` 和 `condition` 中，导致重复过滤。

**修复：** 调整解析顺序，将 DateRangeParser 移到 ConditionParser 之前执行。

**影响：** 所有时间范围查询不再重复解析。

---

### Bug 3: Token移除不精确导致误删 ✅

DateRangeParser 使用模糊匹配误删其他数字token（如 `返回500条` 中的 `500` 被删除）。

**修复：** 改用基于文本位置的精确匹配。

**影响：** 修复所有包含时间范围和数字的混合查询。

---

### Bug 4: 策略范围过宽导致抢占条件 ✅

DateRangeParser 的策略2和3过于宽泛，抢走了 ConditionParser 应处理的时间条件。

**修复：** 只保留策略1（匹配明确的"时间范围"/"日期范围"关键词）。

**影响：** 正确区分全局时间范围和字段时间条件。

---

### Bug 5: ConditionParser字段名截断 ✅

ConditionParser 只识别单个token，导致多token字段名被截断（如 `目标IP` 只识别为 `目标`）。

**修复：** EXPECT_FIELD 状态添加连续字段token合并逻辑。

**影响：** 修复所有多token字段名查询（如 `目标IP`、`用户名` 等）。

---

## 📝 修改的文件

**新增：**

- DateRangeIntent.java - 时间范围意图模型
- DateRangeParser.java - 时间范围解析器

**修改：**

- QueryIntent.java - 添加 dateRange 字段
- NLParser.java - 集成 DateRangeParser，调整解析顺序
- IndexExtractor.java - 支持 NUMBER、FIELD_CANDIDATE、VALUE 类型token
- ConditionParser.java - 添加字段token合并逻辑
- Playbook.java - 添加端到端测试用例
