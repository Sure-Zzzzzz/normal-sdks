# Changelog - v1.5.2

## 发布日期

2026-04-23

## 版本类型

**Minor Release** - 新增条件表达式查询功能，新增 NOT_LIKE 操作符

## 变更概述

新增条件表达式查询（Expression Query）功能，支持通过类 SQL 表达式字符串直接发起 Elasticsearch 查询，无需手动构造 `QueryCondition` 对象。同时新增 `NOT_LIKE` 操作符支持。

依赖升级：
- `simple-elasticsearch-search-core` 1.0.4 → 1.0.5
- `condition-expression-parser-starter` 1.0.1 → 1.0.2（新增 `最近X` 系列时间关键字）

---

## 新增功能

### 1. 条件表达式查询（Expression Query）

通过类 SQL 表达式字符串发起查询，表达式由 `condition-expression-parser-starter` 解析为 AST，再由 `ExpressionToQueryConditionVisitor` 转换为 `QueryCondition`，最终走标准查询链路执行。

**新增 API**

| 方法 | URI | 说明 |
|------|-----|------|
| POST | `/query/expression` | 条件表达式查询 |
| GET  | `/expression/validate` | 校验表达式语法 |

**请求示例（POST /query/expression）**

```json
{
  "index": "order",
  "expression": "status = \"paid\" AND amount >= 100",
  "pagination": { "page": 1, "size": 20 }
}
```

**响应示例（GET /expression/validate）**

```json
{
  "data": {
    "valid": true,
    "errorMessage": null,
    "errorPosition": -1
  }
}
```

**支持的表达式语法**

| 语法 | 示例 |
|------|------|
| 等于 / 不等于 | `status = "paid"` / `status != "failed"` |
| 比较 | `amount >= 100` |
| 范围 | `amount BETWEEN 10 AND 100` |
| IN / NOT IN | `status IN ("paid", "refunded")` |
| LIKE / NOT LIKE | `name LIKE "张%"` |
| IS NULL / IS NOT NULL | `remark IS NULL` |
| 时间范围 | `create_time = 最近7天` / `create_time = 最近一小时` |
| 逻辑组合 | `A AND B` / `A OR B` / `NOT A` |
| 括号分组 | `(A OR B) AND C` |

**字段名映射**

支持在索引配置中定义字段名映射，表达式中可使用中文或业务字段名，自动映射到 ES 实际字段名：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          search:
            indices:
              - name: order_*
                alias: order
                field-mapping:
                  "[订单状态]": status
                  "[金额]": amount
```

表达式中即可使用：`订单状态 = "paid" AND 金额 >= 100`

**表达式长度限制**

默认最大长度 2048 字符，可通过配置调整：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          search:
            api:
              expression:
                max-length: 4096  # 0 表示不限制
```

---

### 2. NOT_LIKE 操作符

`QueryOperator` 新增 `NOT_LIKE`，对应 ES `must_not wildcard` 查询。

```json
{
  "field": "name",
  "operator": "not_like",
  "value": "张%"
}
```

---

## 改动范围汇总

**新增文件**

| 文件 | 说明 |
|------|------|
| `expression/visitor/ExpressionToQueryConditionVisitor.java` | 表达式 AST → QueryCondition 转换器（非 Bean，线程安全） |
| `expression/visitor/ExpressionVisitorRegistry.java` | Visitor 注册表，启动时按索引预构建，运行时只读 |
| `expression/service/ExpressionService.java` | 表达式服务（解析、转换、校验、长度限制） |
| `endpoint/request/ExpressionQueryRequest.java` | 表达式查询请求模型 |
| `endpoint/response/ExpressionValidationResult.java` | 表达式校验结果模型 |
| `exception/ExpressionParseException.java` | 表达式解析异常 |
| `query/builder/strategy/operator/NotLikeOperatorStrategy.java` | NOT_LIKE 操作符策略 |

**修改文件**

| 文件 | 改动 |
|------|------|
| `endpoint/SimpleElasticsearchSearchApiEndpoint.java` | 新增 `POST /query/expression`、`GET /expression/validate` 两个端点 |
| `query/builder/strategy/OperatorStrategyRegistry.java` | 注册 NOT_LIKE 策略 |
| `configuration/SimpleElasticsearchSearchProperties.java` | `IndexConfig` 新增 `fieldMapping`；`ApiConfig` 新增 `ExpressionConfig`（`maxLength`）；新增 fieldMapping 配置校验 |
| `constant/ErrorCode.java` | 新增 `EXPRESSION_PARSE_FAILED` |
| `constant/ErrorMessage.java` | 新增 `EXPRESSION_PARSE_FAILED`、`EXPRESSION_TOO_LONG` |
| `build.gradle` | 升级 core 至 1.0.5，升级 condition-expression-parser-starter 至 1.0.2 |
| `version.properties` | 1.5.1 → 1.5.2 |

---

## 依赖说明

`condition-expression-parser-starter` 已通过 `api` 传递依赖引入，无需单独添加。

---

## 向后兼容性

✅ **完全向后兼容**

- 所有已有 API 行为不变
- 所有请求/响应模型不变
- 已有配置项不变，仅新增可选配置

---

## 升级指南

```gradle
implementation "io.github.surezzzzzz:simple-elasticsearch-search-starter:1.5.2"
```

升级后按需在配置中添加 `field-mapping` 和 `api.expression.max-length` 即可使用条件表达式查询。

## 贡献者

- @surezzzzzz
