# Changelog - v1.5.0

## 发布日期

2026-04-21

## 版本类型

**Minor Release** - 内部架构重构，向后兼容

## 变更概述

全面引入策略模式、模板方法模式、责任链模式，消除核心类中的大型 switch/if-else，提升扩展性和可维护性。对外 API 零变化。

---

## 重构内容

### 1. QueryDslBuilder 操作符策略化

**现状问题：** `buildFieldCondition()` 中有 17 个操作符的 switch，每新增操作符都要改核心类。

**重构方案：** 策略模式

新增结构：
```
query/builder/strategy/
  OperatorStrategy.java              <- 接口
  OperatorStrategyRegistry.java      <- 注册表（对齐 PaginationStrategyRegistry 风格）
  operator/
    EqOperatorStrategy.java          <- eq（TEXT 用 match，其他用 term）
    NeOperatorStrategy.java          <- ne（复用 EqOperatorStrategy）
    GtOperatorStrategy.java          <- gt
    GteOperatorStrategy.java         <- gte
    LtOperatorStrategy.java          <- lt
    LteOperatorStrategy.java         <- lte
    InOperatorStrategy.java          <- in（TEXT 用 should+match，其他用 terms）
    NotInOperatorStrategy.java       <- not_in（复用 InOperatorStrategy）
    BetweenOperatorStrategy.java     <- between
    LikeOperatorStrategy.java        <- like（TEXT 用 match，KEYWORD 用 wildcard）
    PrefixOperatorStrategy.java      <- prefix
    SuffixOperatorStrategy.java      <- suffix
    ExistsOperatorStrategy.java      <- exists
    NotExistsOperatorStrategy.java   <- not_exists（复用 ExistsOperatorStrategy）
    IsNullOperatorStrategy.java      <- is_null（复用 ExistsOperatorStrategy）
    IsNotNullOperatorStrategy.java   <- is_not_null（复用 ExistsOperatorStrategy）
    RegexOperatorStrategy.java       <- regex
```

**扩展点：** 用户可通过注入 `OperatorStrategyRegistry` Bean 调用 `register()` 注册自定义操作符策略，不允许覆盖内置 key（抛 `SEARCH_QUERY_010`）。

---

### 2. AggregationDslBuilder 聚合类型策略化

**现状问题：** `buildByType()` 中有 12 个聚合类型的 switch。

**重构方案：** 策略模式

新增结构：
```
agg/builder/strategy/
  AggregationStrategy.java           <- 接口
  AggregationStrategyRegistry.java   <- 注册表
  metric/
    SumAggregationStrategy.java
    AvgAggregationStrategy.java
    MinAggregationStrategy.java
    MaxAggregationStrategy.java
    CountAggregationStrategy.java
    CardinalityAggregationStrategy.java
    StatsAggregationStrategy.java
    ExtendedStatsAggregationStrategy.java
  bucket/
    TermsAggregationStrategy.java
    DateHistogramAggregationStrategy.java
    HistogramAggregationStrategy.java
    RangeAggregationStrategy.java
    CompositeAggregationStrategy.java  <- 直接注入 metrics 策略 Bean，处理 sub-agg
```

**说明：** composite 聚合通过 `AggDefinition.composite = true` 标志位走独立分支，不在 registry 注册（composite 不是 `AggType` 枚举值）。

**扩展点：** 用户可通过注入 `AggregationStrategyRegistry` Bean 调用 `register()` 注册自定义聚合策略，不允许覆盖内置 key（抛 `SEARCH_AGG_006`）。

---

### 3. AbstractExecutor 模板方法模式

**现状问题：** `QueryExecutorImpl` 和 `AggExecutorImpl` 有完全相同的执行骨架和降级重试逻辑（约 60 行重复代码）。

**重构方案：** 模板方法模式

新增 `executor/AbstractExecutor<Req, Resp>`，定义通用执行骨架：
- `execute()`：final，不可覆盖，包含参数校验 → 获取元数据 → 降级重试 → 执行
- `executeWithDowngradeRetry()`：通用降级重试逻辑，`isTooLongFrameException` 检测
- 抽象方法：`validateRequest()`、`needsDowngradeRetry()`、`executeOnce()`、`getIndex()`、`wrapIoException()`
- 钩子方法：`estimateDowngradeLevel()`（默认 LEVEL_0，子类可覆盖）

`QueryExecutorImpl` 和 `AggExecutorImpl` 继承 `AbstractExecutor`，删除重复逻辑。

**同步变更：** `QueryExecutor` 和 `AggExecutor` 接口删除，`QueryExecutorImpl` → `QueryExecutor`，`AggExecutorImpl` → `AggExecutor`（接口只有一个实现，无需保留）。

**ES 6.x 兼容性：** `Es6xAggregationResponseException extends IOException`，在 `AggExecutor.executeOnce()` 内部捕获，不冒泡到基类的 `catch(IOException)`。

---

### 4. 请求校验责任链

**现状问题：** `QueryExecutorImpl.validateRequest()` 把 7 个校验步骤堆在一个方法里（约 65 行），新增校验要改核心类。

**重构方案：** 责任链模式

新增结构：
```
query/validator/
  QueryRequestValidator.java         <- 接口
  QueryRequestValidatorChain.java    <- 责任链容器（Spring 自动注入 List<QueryRequestValidator>）
  IndexAliasValidator.java           <- @Order(10) 索引别名非空
  DefaultDateRangeValidator.java     <- @Order(20) 通配索引默认时间范围注入
  PaginationDefaultsValidator.java   <- @Order(30) 分页默认值填充
  PaginationSizeValidator.java       <- @Order(40) size 上限校验
  OffsetDepthValidator.java          <- @Order(50) offset 深度校验
  SearchAfterSortValidator.java      <- @Order(60) search_after 排序必填 + 委托策略校验
  CollapseSortValidator.java         <- @Order(70) collapse 排序必填
```

**顺序说明：** `DefaultDateRangeValidator` 必须在 `PaginationDefaultsValidator` 之前（注入 dateRange 后分页才能正确处理）；`PaginationDefaultsValidator` 必须在 `PaginationSizeValidator` 之前（先填默认值再校验上限）。

---

### 5. SensitiveFieldProcessor 脱敏策略化

**现状问题：** `process()` 中用 if-else 判断 `FORBIDDEN` 和 `MASK` 两种策略，新增脱敏策略要改核心类。

**重构方案：** 策略模式

新增结构：
```
processor/sensitive/
  SensitiveFieldStrategy.java              <- 接口（避免与枚举 SensitiveStrategy 同名）
  SensitiveFieldStrategyRegistry.java      <- 注册表
  ForbiddenSensitiveFieldStrategy.java     <- 禁止访问：直接移除字段
  MaskSensitiveFieldStrategy.java          <- 脱敏：保留前 N 位和后 M 位
```

---

### 6. 工具类整理

- `DateIntervalHelper` 移至 `support/` 包（原在 `agg/builder/strategy/bucket/` 包内，访问权限 package-private）
- `"too_long_frame_exception"` 字符串提取为 `SimpleElasticsearchSearchConstant.TOO_LONG_FRAME_EXCEPTION`

---

## 新增错误码

| 错误码 | 值 | 说明 |
|--------|-----|------|
| `OPERATOR_STRATEGY_DUPLICATE` | `SEARCH_QUERY_010` | 操作符策略 key 已存在，不允许覆盖内置策略 |
| `AGG_STRATEGY_DUPLICATE` | `SEARCH_AGG_006` | 聚合策略 key 已存在，不允许覆盖内置策略 |

---

## 依赖变更

无。本次为纯内部重构，不涉及依赖升级。

---

## 向后兼容性

✅ **完全向后兼容**

- 所有对外 API（`/api/query`、`/api/agg` 等）行为不变
- 所有请求/响应模型不变
- 配置项不变
- `QueryExecutor` / `AggExecutor` 类名不变（接口删除，类名保留）

---

## 升级指南

```gradle
implementation "io.github.surezzzzzz:simple-elasticsearch-search-starter:1.5.0"
```

无需修改任何代码或配置。

## 贡献者

- @surezzzzzz
