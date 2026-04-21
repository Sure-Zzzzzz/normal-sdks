# Changelog - v1.5.1

## 发布日期

2026-04-21

## 版本类型

**Minor Release** - 内部架构重构，向后兼容

## 变更概述

对三个职责过多的核心类进行拆分，消除大型方法和混杂职责，提升可维护性。对外 API 零变化。

---

## 重构内容

### 1. IndexRouteDowngradeProcessor 降级策略化

**现状问题：** `IndexRouteProcessor`（614 行）内部包含降级策略应用、索引名生成、通配符生成、日期工具方法等 6 个职责，且类名未体现降级含义。

**重构方案：** 策略模式 + 工具类提取

新增结构：
```
processor/downgrade/
  DowngradeStrategy.java              <- 接口
  DowngradeStrategyRegistry.java      <- 注册表（按 DateGranularity 查找）
  DailyDowngradeStrategy.java         <- 日粒度：LEVEL_0=具体日期，LEVEL_1=月通配，LEVEL_2=年通配，LEVEL_3=全通配
  MonthlyDowngradeStrategy.java       <- 月粒度：LEVEL_0=具体月份，LEVEL_1=年通配，LEVEL_2/3=全通配
  YearlyDowngradeStrategy.java        <- 年粒度：LEVEL_0=具体年份，LEVEL_1/2/3=全通配

support/
  IndexDateHelper.java                <- 日期/字符串工具（parseDate、extractIndexPrefix、extractSeparator、buildMonthPattern、buildYearPattern）
```

`IndexRouteProcessor` 重命名为 `IndexRouteDowngradeProcessor`，删除所有降级策略方法和工具方法，委托给 `DowngradeStrategyRegistry`，约 614 行 → 约 160 行。

---

### 2. ElasticsearchCompatibilityHelper 反射隔离

**现状问题：** `ElasticsearchCompatibilityHelper`（476 行）混合了反射操作、响应解析、查询执行、DSL 字段清理等 5 个职责，反射代码难以维护。

**重构方案：** 工具类提取

新增结构：
```
support/
  XContentReflectionHelper.java       <- 所有反射操作（包路径检测、NamedXContentRegistry、Parser 创建/关闭、响应解析）
  DslCompatibilityHelper.java         <- DSL 兼容性处理（removeEs7OnlyCompositeFields）
```

`ElasticsearchCompatibilityHelper` 变成门面，只暴露 `executeSearch` / `parseResponse` 两个公开方法，约 476 行 → 约 150 行。

`Es6xAggregationResponseException` 留在 `ElasticsearchCompatibilityHelper` 中（对外异常契约不移动）。

---

### 3. MappingManager 接口删除 + 字段解析拆分

**现状问题：** `MappingManagerImpl`（483 行）包含生命周期管理、缓存管理、版本兼容路由、字段解析（递归，约 80 行）等 5 个职责；`MappingManager` 接口只有一个实现，无需保留。

**重构方案：** 接口删除 + 解析器提取

新增结构：
```
metadata/parser/
  FieldMetadataParser.java            <- 字段元数据解析器（parseFields 拆出，递归处理嵌套字段和 multi-fields）
```

`MappingManager` 接口删除，`MappingManagerImpl` 重命名为 `MappingManager`，字段解析委托给 `FieldMetadataParser`，约 483 行 → 约 310 行。

---

## 改动范围汇总

**删除文件**

| 文件 | 说明 |
|------|------|
| `metadata/MappingManager.java`（接口） | 只有一个实现，无需保留 |

**新增文件**

| 文件 | 说明 |
|------|------|
| `processor/downgrade/DowngradeStrategy.java` | 降级策略接口 |
| `processor/downgrade/DowngradeStrategyRegistry.java` | 注册表 |
| `processor/downgrade/DailyDowngradeStrategy.java` | 日粒度降级策略 |
| `processor/downgrade/MonthlyDowngradeStrategy.java` | 月粒度降级策略 |
| `processor/downgrade/YearlyDowngradeStrategy.java` | 年粒度降级策略 |
| `support/IndexDateHelper.java` | 日期/字符串工具 |
| `support/XContentReflectionHelper.java` | XContent 反射工具 |
| `support/DslCompatibilityHelper.java` | DSL 兼容性工具 |
| `metadata/parser/FieldMetadataParser.java` | 字段元数据解析器 |

**修改文件**

| 文件 | 改动 |
|------|------|
| `processor/IndexRouteProcessor.java` | 重命名为 `IndexRouteDowngradeProcessor`，委托给 DowngradeStrategyRegistry，614 行 → 160 行 |
| `support/ElasticsearchCompatibilityHelper.java` | 委托给 XContentReflectionHelper / DslCompatibilityHelper，476 行 → 150 行 |
| `metadata/MappingManagerImpl.java` | 重命名为 `MappingManager`，委托给 FieldMetadataParser，483 行 → 310 行 |
| `version.properties` | 1.5.0 → 1.5.1 |

---

## 向后兼容性

✅ **完全向后兼容**

- 所有对外 API 行为不变
- 所有请求/响应模型不变
- 配置项不变
- `MappingManager` 类名不变（接口删除，类名保留）
- `IndexRouteDowngradeProcessor` 为重命名，原 `IndexRouteProcessor` 已不存在（内部 Bean，不影响使用方）

---

## 升级指南

```gradle
implementation "io.github.surezzzzzz:simple-elasticsearch-search-starter:1.5.1"
```

无需修改任何代码或配置。

## 贡献者

- @surezzzzzz
