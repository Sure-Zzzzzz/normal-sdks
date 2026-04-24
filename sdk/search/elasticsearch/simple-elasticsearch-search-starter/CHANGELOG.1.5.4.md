# Changelog - v1.5.4

## 发布日期

2026-04-24

## 版本类型

**Patch Release** - Spring Boot 2.2.5 / ES 6.8.x 兼容性修复，测试优化

## 变更概述

将三处 ES 7.x 专属 API 改为反射调用，消除 6.8.x classpath 下的类加载硬依赖，使 starter 可在 Spring Boot 2.2.5 环境下正常启动和运行。同步升级 route-starter 至 1.0.8。

对 2.7.9 / 2.4.5 用户完全透明，行为不变。

依赖升级：
- `simple-elasticsearch-route-starter` 1.0.7 → 1.0.8

---

## 兼容性修复

### 1. PointInTimeBuilder 反射化

**文件**: `query/pagination/PitPaginationStrategy.java`

**问题**: `PointInTimeBuilder` 是 ES 7.10+ 才有的类，6.8.x classpath 下类加载即 `NoClassDefFoundError`。

**修复**: 通过静态初始化块反射检测 `PointInTimeBuilder` 是否可用，同时将 `SearchSourceBuilder.pointInTimeBuilder()` 的调用也改为反射，确保字节码中无任何对该类的硬引用。6.8.x 下 PIT 分页在 `validate()` 阶段即被版本检测拒绝，不会走到实际构建逻辑。

### 2. getTotalHits().value 反射化

**文件**: `query/executor/QueryExecutor.java`、`support/ElasticsearchCompatibilityHelper.java`

**问题**: ES 6.8.x 中 `SearchHits.getTotalHits()` 返回 `long`，ES 7.x+ 返回 `TotalHits` 对象，直接访问 `.value` 字段在 6.8.x 下编译不通过。

**修复**: 提取 `ElasticsearchCompatibilityHelper.extractTotalHits()` 静态方法，通过 `instanceof Long` 分支兼容 6.x，通过反射取 `value` 字段兼容 7.x+。

### 3. DateHistogramInterval.QUARTER 反射化

**文件**: `support/DateIntervalHelper.java`

**问题**: `DateHistogramInterval.QUARTER` 常量在 ES 6.8.x 中不存在，直接引用会在类加载时抛 `NoSuchFieldError`。

**修复**: 通过静态初始化块反射获取 `QUARTER` 常量，不可用时降级为 `new DateHistogramInterval("1q")`（字符串构造器在 6.8.x 中存在）。

---

## 测试优化

### 4. IndexDowngradeEndToEndTest 提速

**问题**: `@BeforeAll` 创建 465 个日粒度索引（2024-01-01 ~ 2025-03-31），setup 约 930 次 ES HTTP 请求，耗时极长。`@AfterAll` 逐天遍历 exists + delete，又是 465 次。

**优化**:
- 测试配置 `auto-downgrade-index-count-threshold` 从 200 调整为 35（单月 31 天不触发，2 个月 60 天触发，语义不变）
- 数据范围从 15 个月压缩到 3 个月（2024-01 ~ 2024-03，91 个索引），setup 调用量降至约 180 次
- `@AfterAll` 改用通配符删除（`DELETE test_downgrade_log--*`），一次请求替代 465 次遍历

### 5. DowngradeStrategyTest 补全

**问题**: 降级逻辑的 LEVEL_2 / LEVEL_3 路径无测试覆盖，端到端测试只验证"查询成功"，未断言实际降级级别。

**新增**: `DowngradeStrategyTest` 直接单测三种粒度策略（日/月/年）× 四个降级级别（LEVEL_0~3），覆盖跨年边界、同月/同年边界、横杠分隔符等场景，不依赖 ES 连接。

---

## 改动范围汇总

**修改文件**

| 文件 | 改动 |
|------|------|
| `build.gradle` | route-starter 1.0.7 → 1.0.8 |
| `version.properties` | 1.5.3 → 1.5.4 |
| `query/pagination/PitPaginationStrategy.java` | PointInTimeBuilder 全反射化（含 SearchSourceBuilder.pointInTimeBuilder 调用） |
| `query/executor/QueryExecutor.java` | getTotalHits().value → ElasticsearchCompatibilityHelper.extractTotalHits() |
| `support/ElasticsearchCompatibilityHelper.java` | 新增 extractTotalHits() 静态方法 |
| `support/DateIntervalHelper.java` | QUARTER 常量反射化，降级为字符串构造 |
| `constant/SimpleElasticsearchSearchConstant.java` | 新增 DATE_INTERVAL_QUARTER_VALUE 常量 |
| `test/.../IndexRouteProcessorTest.java` | testLargeRangeMonthlyQuery 改用 routeWithDowngrade(LEVEL_0) |
| `test/.../IndexDowngradeEndToEndTest.java` | 数据范围缩减、cleanup 优化 |
| `test/resources/application.yaml` | threshold 200 → 35 |

**新增文件**

| 文件 | 说明 |
|------|------|
| `test/.../DowngradeStrategyTest.java` | 降级策略 LEVEL_0~3 全路径单测 |

---

## 向后兼容性

✅ **完全向后兼容**

- Spring Boot 2.7.9 / 2.4.5 用户：反射调用结果与直接调用一致，无感知
- Spring Boot 2.2.5 用户：可正常启动，PIT 分页因服务端不支持会在 validate 阶段被拒绝（预期行为）
- 所有已有 API 行为不变

---

## 升级指南

```gradle
implementation "io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.5.4"
```

无需任何配置变更。

## 贡献者

- @surezzzzzz
