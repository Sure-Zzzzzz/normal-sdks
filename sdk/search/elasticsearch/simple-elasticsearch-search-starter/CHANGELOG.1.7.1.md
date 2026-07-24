# v1.7.1 更新日志

**发布日期：** 2026-07-24

**类型：** Bug Fix - 修复 LIKE wildcard、表达式双重否定、时间范围语义与 long 时间字段边界

**依赖版本：**

| 依赖 | 版本 |
|------|------|
| `simple-elasticsearch-search-core` | 1.0.12 |
| `simple-elasticsearch-route-starter` | 1.2.0 |

---

## 修复内容

### LIKE 统一使用 wildcard 查询

`like` 在根字段为 `text` 时，旧实现会构造 ES `match` query，使调用方传入的 `*`、`?` 不再具有 wildcard 语义。

v1.7.1 统一使用 ES `wildcard` query：

- `keyword` 根字段直接查询根字段。
- `text + keyword` 自动查询 keyword-compatible 子字段。
- 纯 `text` 仍查询根字段，不拒绝查询，也不降级为 `match`。
- 通配符索引合并多个 mapping 时，同时查询 `exactQueryFields` 与 `matchQueryFields`，避免遗漏纯 text 或 keyword-compatible 路径。
- value 未显式包含 `*`、`?` 时，继续按 `*value*` 执行包含匹配；显式模式保持原样。

纯 `text` 的 wildcard 查询针对 analyzer 写入的词项，不能承诺原始完整字段值的字符级匹配。需要稳定完整值 wildcard 语义时，mapping 应提供能收录目标值的 keyword-compatible 字段；`ignore_above` 限制仍由 ES mapping 决定。

### 表达式双重否定

修复一元 `NOT` 未能反转已是否定的 LIKE 类操作符：

- `NOT (field NOT LIKE "mock-value")` 恢复为 `like`。
- `NOT (field NOT PREFIX LIKE "mock")` 恢复为 `prefix`。
- `NOT (field NOT SUFFIX LIKE "@example.test")` 恢复为 `suffix`。

### 时间范围补集

时间关键字等于仍生成闭区间 `between`。以下两种表达式现在统一表示闭区间外：

```text
createTime != 最近7天
NOT (createTime = 最近7天)
```

二者均翻译为：

```text
createTime < from OR createTime > end
```

不新增 `NOT_BETWEEN`，复用现有 `lt`、`gt` 与逻辑条件树。`NOT (createTime != 最近7天)` 则恢复为区间内条件。

### 时间字段类型与请求级截止边界

- 修复 mapping 为 `long` 的时间字段：时间关键字边界按 epoch seconds 传入 `Long`，不再错误传入 ISO 日期字符串；`date` 字段保持原日期字符串边界。
- 表达式查询与表达式聚合请求新增可选 `timeRangeEnd`（`NOW` / `TODAY_START`）和 IANA `timeZone`。默认 `NOW` 保持滚动到当前时刻；`TODAY_START` 截止到指定时区当天零点。
- `timeZone` 同时决定当天零点和 long epoch seconds 的换算；缺省时保持 JVM 系统时区行为。

---

## 新增与调整测试

- 新增 `LikeOperatorStrategyTest`：覆盖 keyword、text + keyword、纯 text、显式 `*` / `?` 模式，以及 mixed mapping 的全部 wildcard 选路。
- 更新 `ExpressionTest`：覆盖 LIKE 类双重否定、时间范围 `!=`、`NOT (= 时间范围)`、long epoch-seconds 边界、`TODAY_START` 指定 IANA 时区的固定时钟边界与最终数值 DSL、请求上下文并发隔离，以及非法时区错误。
- 更新 `SearchEndToEndTest`：覆盖 text + keyword、纯 text 与 mixed mapping 的 LIKE / NOT LIKE 实际命中，LIKE 双重否定、long 时间范围补集与双重否定、DATE IANA 时区链路、查询与聚合入口的默认 `NOW` 行为，以及两个入口对非法 IANA 时区和非法 `timeRangeEnd` 的统一拒绝。

已按 `--rerun-tasks` 完整验证 Spring Boot 2.2.x、2.3.12、2.4.5、2.7.9 四组矩阵，均通过；不使用跳过关键端到端测试的开关。

---

## 向后兼容性与升级说明

- 新增表达式请求可选字段 `timeRangeEnd` 与 `timeZone`；省略时保持原有滚动到当前时间、JVM 系统时区行为。请求/响应其余模型、表达式关键字与 `QueryOperator` 不变。
- `regex` / `not_regex` 仍仅支持 JSON API，表达式语法不增加 REGEX 关键字。
- `field-mapping` 继续只配置根字段和标签，不需要填写 `.keyword`。
- 不升级 search-core、route-starter、metrics-starter 或 audit-listener-starter 依赖版本。
- 调用方需要更新时，仅将依赖版本升至 `1.7.1`。