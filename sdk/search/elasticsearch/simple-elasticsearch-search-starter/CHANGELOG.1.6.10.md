# v1.6.10 更新日志

**发布日期：** 2026-07-09

**类型：** Bug Fix - 支持通过 ES 元字段 `_id` 查询文档，并修复通配符字段类型冲突与 ES 7 totalHits 兼容日志

**依赖版本：**

| 依赖 | 版本 |
|------|------|
| `simple-elasticsearch-search-core` | 1.0.12 |

---

## 修复内容

### 支持 `_id` 查询

**问题：** 查询响应已返回 `_id`，但请求侧把 `_id` 当成普通 mapping 字段校验。由于 `_id` 是 ES 元字段，不在 mapping `properties` 中，`QueryDslBuilder` 会误抛字段不存在异常。

**修复：** `QueryDslBuilder` 在普通字段元数据校验前识别 `_id`，使用 ES 原生 `idsQuery` 构建查询。

支持的操作符：

| operator | 说明 |
|----------|------|
| `eq` | 查询单个文档 id |
| `in` | 查询多个文档 id |
| `ne` | 排除单个文档 id |
| `not_in` | 排除多个文档 id |

`countOnly=true` 同步支持 `_id` 查询，因为普通查询和 `_count` 路径共用 `QueryDslBuilder`。

### 通配符索引字段类型冲突查询兼容

**问题：** 通配符索引下，同名字段可能在部分索引中是 `keyword`，在部分索引中是 `text` 且带 `keyword` 子字段。旧合并逻辑展示属性采用最新索引定义，同时合并子字段，但查询策略只能选择主字段或 `.keyword` 单一路径，容易漏掉另一类索引。

**修复：** 字段元数据内部记录精确查询路径与全文匹配路径；`eq` / `in` / `ne` / `not_in` 复用这些路径生成兼容查询。混合 `keyword` 与 `text.keyword` 时同时查询 `field` 和 `field.keyword`；混合纯 `text` 与 `text.keyword` 时同时保留 `match(field)` 与 `term/terms(field.keyword)`。

字段接口响应不暴露内部查询路径，展示属性仍沿用最新索引定义。

### ES 7 totalHits 正常路径不再打 warn

**问题：** ES 7 `SearchHits#getTotalHits()` 返回 Lucene `TotalHits` 对象，该对象通过 public `value` 字段暴露命中数。旧逻辑先反射调用不存在的 `value()` 方法，导致正常路径出现 warning。

**修复：** 对 `TotalHits` 对象优先读取 public `value` 字段；仅字段读取失败时才打 warn 并返回兜底值。

---

## 使用示例

### 单 id 查询

```json
{
  "index": "test_wildcard",
  "query": {
    "field": "_id",
    "op": "eq",
    "value": "doc-001"
  }
}
```

### 多 id 查询

```json
{
  "index": "test_wildcard",
  "query": {
    "field": "_id",
    "op": "in",
    "values": ["doc-001", "doc-002"]
  }
}
```

### countOnly 查询

```json
{
  "index": "test_wildcard",
  "query": {
    "field": "_id",
    "op": "in",
    "values": ["doc-001", "doc-002"]
  },
  "countOnly": true
}
```

---

## 新增/变更文件

| 文件 | 类型 | 说明 |
|------|------|------|
| `version.properties` | 修改 | 1.6.9 -> 1.6.10 |
| `src/main/java/.../constant/SimpleElasticsearchSearchConstant.java` | 修改 | 新增 `_id` 查询异常原因常量 |
| `src/main/java/.../query/builder/QueryDslBuilder.java` | 修改 | 新增 `_id` 元字段查询分支 |
| `src/main/java/.../metadata/model/FieldMetadata.java` | 修改 | 新增内部查询路径，不对字段接口暴露 |
| `src/main/java/.../metadata/parser/FieldMetadataParser.java` | 修改 | 合并通配符索引下同名字段查询路径 |
| `src/main/java/.../query/builder/strategy/operator/EqOperatorStrategy.java` | 修改 | `eq/ne` 支持混合 mapping 双路径查询 |
| `src/main/java/.../query/builder/strategy/operator/InOperatorStrategy.java` | 修改 | `in/not_in` 支持混合 mapping 双路径查询 |
| `src/main/java/.../support/ElasticsearchCompatibilityHelper.java` | 修改 | ES 7 totalHits 优先读取 public `value` 字段 |
| `src/test/java/.../test/cases/QueryDslBuilderTest.java` | 修改 | 补充 `_id` DSL 单元测试 |
| `src/test/java/.../test/cases/SearchEndToEndTest.java` | 修改 | 补充 `_id` 接口查询与 countOnly 覆盖 |
| `src/test/java/.../test/cases/FieldMetadataParserTest.java` | 修改 | 补充通配符字段类型冲突合并路径覆盖 |
| `src/test/java/.../test/cases/EqOperatorStrategyTest.java` | 修改 | 补充混合 mapping 的 `eq` 查询覆盖 |
| `src/test/java/.../test/cases/InOperatorStrategyTest.java` | 新增 | 补充混合 mapping 的 `in` 查询覆盖 |
| `src/test/java/.../test/cases/ElasticsearchCompatibilityHelperTest.java` | 新增 | 补充 totalHits `value` 字段读取覆盖 |
| `DESIGN.1.6.10.md` | 新增 | `_id` 查询设计文档 |
| `README.md` | 修改 | 更新版本选型、依赖示例与 `_id` 查询说明 |
| `CHANGELOG.1.6.10.md` | 新增 | 本版本更新日志 |

---

## 向后兼容性

- 请求模型不变，继续使用 `QueryCondition.field/op/value/values`。
- 普通业务字段仍走 mapping 元数据校验和原 operator strategy。
- `_id` 不加入字段元数据接口，不污染敏感字段、排序、聚合等业务字段能力判断。
- 错误体系复用模块 `FieldException`，不新增对外错误结构。
- 不新增依赖，不改变现有自动配置与 Bean 注册。

---

## 验证结果

| 范围 | 重点 | 结果 |
|------|------|------|
| `QueryDslBuilderTest` | `_id eq/in/ne/not_in` DSL、AND/OR 组合、数值 id、空值/空列表/全 null、不支持操作符、普通字段回归 | 通过 |
| `SearchEndToEndTest` | `/api/query` `_id eq/in/not_in`、`countOnly=true`、`_id` 与普通字段 AND/OR 组合 | 通过 |
| `FieldMetadataParserTest` | `keyword` / `text.keyword`、纯 `text` / `text.keyword` 混合 mapping 查询路径合并 | 通过 |
| `EqOperatorStrategyTest` | 混合 mapping 下 `eq` 同时覆盖精确路径与 match 路径 | 通过 |
| `InOperatorStrategyTest` | 混合 mapping 下 `in` 同时覆盖 terms 与 match 路径 | 通过 |
| `ElasticsearchCompatibilityHelperTest` | totalHits public `value` 字段读取与失败兜底 | 通过 |

多版本完整测试均已通过：Spring Boot 2.7.9、2.4.5、2.3.12、2.2.x。

---

## 升级指南

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.6.10'
}
```

依赖的 `simple-elasticsearch-search-core:1.0.12` 不变。
