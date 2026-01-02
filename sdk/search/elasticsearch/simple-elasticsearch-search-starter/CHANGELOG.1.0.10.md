# 1.0.10 更新日志

## 发布日期

2026-01-01

## 主要功能

### 索引路由智能降级

解决查询大范围日期分割索引时，因 HTTP 请求行过长导致的 `too_long_frame_exception` 异常。

#### 问题背景

当查询包含大量日期分割索引时（如查询一年365天的日志数据），索引名过多会导致 HTTP 请求行超过 Elasticsearch 默认的 4096 字节限制，触发 `too_long_frame_exception` 异常。

**示例场景：**
- 索引格式：`log--2025.01.01`, `log--2025.01.02`, ..., `log--2025.12.31`
- 索引数量：365个
- HTTP 请求行长度：~5889 字节 > 4096 字节

#### 解决方案

采用**多级智能降级策略**，根据索引粒度和日期范围自动调整查询的索引通配符粒度。

#### 降级级别

定义4个降级级别，从精确到粗粒度：

| 降级级别 | 说明 | 适用场景 |
|---------|------|---------|
| LEVEL_0 | 不降级，使用具体索引名 | 索引数量较少，请求行未超限 |
| LEVEL_1 | 一级降级，使用更粗粒度的通配符 | 请求行超限，降低通配符粒度 |
| LEVEL_2 | 二级降级，使用更粗粒度的通配符 | 一级降级后仍超限 |
| LEVEL_3 | 三级降级，使用最粗粒度的通配符 | 二级降级后仍超限 |

#### 按索引粒度的降级策略

**日粒度索引（如：log--2025.01.01）**

| 降级级别 | 索引格式示例 | 说明 |
|---------|-------------|------|
| LEVEL_0 | `log--2025.01.01` | 具体日期索引 |
| LEVEL_1 | `log--2025.01.*` | 按月通配符（月级通配符）|
| LEVEL_2 | `log--2025.*` | 按年通配符 |
| LEVEL_3 | `log--*` | 全通配符 |

**月粒度索引（如：log--2025.01）**

| 降级级别 | 索引格式示例 | 说明 |
|---------|-------------|------|
| LEVEL_0 | `log--2025.01` | 具体月份索引 |
| LEVEL_1 | `log--2025.*` | 按年通配符 |
| LEVEL_2/3 | `log--*` | 全通配符 |

**年粒度索引（如：log--2025）**

| 降级级别 | 索引格式示例 | 说明 |
|---------|-------------|------|
| LEVEL_0 | `log--2025` | 具体年份索引 |
| LEVEL_1/2/3 | `log--*` | 全通配符 |

#### 降级触发方式

采用**混合触发策略**：

1. **预估触发（默认启用）**：
   - 在发起查询前预估 HTTP 请求行长度
   - 检查索引数量是否超过阈值（默认200个）
   - 检查 HTTP 请求行长度是否超过限制（默认4096字节）
   - 如果超限，直接使用合适的降级级别

2. **异常触发（兜底机制）**：
   - 捕获 `too_long_frame_exception` 异常
   - 自动降级到下一级别重试
   - 最多降级到配置的最大级别（默认 LEVEL_3）

#### 配置项

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          search:
            downgrade:
              # 是否启用降级功能（默认：true）
              enabled: true
              # HTTP 请求行最大长度（字节，默认：4096）
              max-http-line-length: 4096
              # 最大降级级别（0-3，默认：3）
              max-level: 3
              # 是否启用预估触发（默认：true）
              enable-estimate: true
              # 索引数量阈值（默认：200）
              auto-downgrade-index-count-threshold: 200
```

#### 使用示例

**场景1：查询一年数据（日粒度索引）**

```java
QueryRequest request = QueryRequest.builder()
    .index("daily_log")
    .dateRange(QueryRequest.DateRange.builder()
        .from("2025-01-01")
        .to("2025-12-31")
        .build())
    .build();

QueryResponse response = queryExecutor.execute(request);
```

**执行流程：**
1. 初始尝试 LEVEL_0：365个具体索引（超过阈值200，且 HTTP 请求行 > 4096字节）
2. 自动降级到 LEVEL_1：12个月级通配符（`log--2025.01.*`, ..., `log--2025.12.*`）
3. 查询成功

**场景2：查询多年数据（月粒度索引）**

```java
QueryRequest request = QueryRequest.builder()
    .index("monthly_log")
    .dateRange(QueryRequest.DateRange.builder()
        .from("2023-01-01")
        .to("2025-12-31")
        .build())
    .build();

QueryResponse response = queryExecutor.execute(request);
```

**执行流程：**
1. 初始尝试 LEVEL_0：36个具体月份索引（超过阈值或HTTP请求行超限）
2. 自动降级到 LEVEL_1：3个年通配符（`log--2023.*`, `log--2024.*`, `log--2025.*`）
3. 查询成功

## 新增类

### 1. DowngradeLevel（枚举类）

**路径：** `io.github.surezzzzzz.sdk.elasticsearch.search.constant.DowngradeLevel`

**功能：** 定义4个降级级别及相关操作

**核心方法：**
- `hasNext()`：判断是否还有下一级别
- `next()`：获取下一降级级别
- `fromValue(int value)`：从数值转换为枚举

### 2. DowngradeConfig（静态内部类）

**路径：** `SimpleElasticsearchSearchProperties.DowngradeConfig`

**功能：** 降级配置

**配置项：**
- `enabled`：是否启用降级（默认：true）
- `maxHttpLineLength`：HTTP 请求行最大长度（默认：4096）
- `maxLevel`：最大降级级别（默认：3）
- `enableEstimate`：是否启用预估触发（默认：true）
- `autoDowngradeIndexCountThreshold`：索引数量阈值（默认：200）

### 3. DowngradeFailedException（自定义异常）

**路径：** `io.github.surezzzzzz.sdk.elasticsearch.search.exception.DowngradeFailedException`

**功能：** 当所有降级级别都失败时抛出

**特性：**
- 继承自 `SimpleElasticsearchSearchException`
- 包含 `finalLevel` 字段，记录最终达到的降级级别

### 4. IndexRouteException（自定义异常）

**路径：** `io.github.surezzzzzz.sdk.elasticsearch.search.exception.IndexRouteException`

**功能：** 索引路由失败时抛出

**特性：**
- 继承自 `SimpleElasticsearchSearchException`
- 包含 `indexAlias` 字段，记录失败的索引别名

### 5. UnsupportedDowngradeLevelException（自定义异常）

**路径：** `io.github.surezzzzzz.sdk.elasticsearch.search.exception.UnsupportedDowngradeLevelException`

**功能：** 当降级级别不适用于当前日期粒度时抛出

**特性：**
- 继承自 `SimpleElasticsearchSearchException`
- 包含 `level` 和 `granularity` 字段

## 代码质量改进

### 1. Bug 修复

**修复编译错误（IndexRouteProcessor.java:280）**
- **问题**：`level.values()` 应该是静态方法调用 `DowngradeLevel.values()`
- **影响**：导致代码无法编译
- **修复**：将实例方法调用改为静态方法调用

### 2. 代码重构

**消除代码重复（60+ 行）**
- **问题**：QueryExecutorImpl 和 AggExecutorImpl 中存在完全相同的降级级别检测逻辑
- **方案**：提取公共方法 `detectDowngradeLevelFromIndices()` 到 IndexRouteProcessor
- **收益**：
  - 减少重复代码 60+ 行
  - 提高可维护性
  - 统一降级级别检测逻辑

### 3. 配置验证

**新增配置启动时验证（SimpleElasticsearchSearchProperties）**
- **新增方法**：`validateDowngradeConfig()`
- **验证项**：
  - `maxLevel` 必须在 [0, 3] 范围内
  - `maxHttpLineLength` 必须为正数
  - `autoDowngradeIndexCountThreshold` 必须为正数
- **时机**：@PostConstruct，在应用启动时立即发现配置错误

### 4. 性能优化

**新增性能预警（IndexRouteProcessor.generateDailyIndices）**
- **场景**：当日期范围超过 365 天时
- **行为**：记录警告日志，提示可能影响性能
- **建议**：考虑使用降级或缩小日期范围

### 5. 文档增强

**完善核心方法文档**
- **DowngradeLevel.next()**：详细说明幂等性行为，避免 NPE
- **IndexRouteProcessor.estimateDowngradeLevel()**：添加完整的预估逻辑说明
- **格式**：使用 HTML 标签（`<p>`, `<ul>`, `<li>`）增强可读性

### 6. 测试健壮性

**更新单元测试**
- 支持中英文双语错误消息断言
- 适配降级预估导致的索引数量变化
- 所有 19 个测试用例通过

## 核心改动

### 1. IndexRouteProcessor

**新增方法：**
- `routeWithDowngrade(metadata, dateRange, downgradeLevel)`：带降级支持的索引路由
- `detectDowngradeLevelFromIndices(indices)`：从索引数组中检测降级级别（新增，消除重复代码）
- `estimateDowngradeLevel(metadata, dateRange)`：预估降级级别
- `estimateHttpLineLength(indices)`：估算 HTTP 请求行长度
- `applyDowngrade(...)`：应用降级策略
- `applyDailyDowngrade(...)`：日粒度降级策略
- `applyMonthlyDowngrade(...)`：月粒度降级策略
- `applyYearlyDowngrade(...)`：年粒度降级策略
- `generateDailyIndices(...)`：生成日粒度索引列表（新增性能警告）
- `generateMonthlyIndices(...)`：生成月粒度索引列表
- `generateYearlyIndices(...)`：生成年粒度索引列表
- `generateMonthlyWildcards(...)`：生成月级通配符列表
- `generateYearlyWildcards(...)`：生成年通配符列表
- `extractSeparator(datePattern)`：提取日期格式中的分隔符
- `buildMonthPattern(datePattern)`：构建月级别的日期格式
- `buildYearPattern(datePattern)`：构建年级别的日期格式

**修改方法：**
- `route(metadata, dateRange)`：支持预估降级，默认调用 `routeWithDowngrade` 方法

### 2. QueryExecutorImpl

**新增方法：**
- `executeWithDowngradeRetry(...)`：执行查询（带降级重试）
- `executeOnce(...)`：执行一次查询
- `isTooLongFrameException(e)`：判断异常是否为 too_long_frame_exception

**修改方法：**
- `execute(request)`：支持降级重试
- `buildSearchRequest(...)`：新增 `downgradeLevel` 参数

**重构改进：**
- 使用 `IndexRouteProcessor.detectDowngradeLevelFromIndices()` 替代重复的检测逻辑

### 3. AggExecutorImpl

**新增方法：**
- `executeWithDowngradeRetry(...)`：执行聚合查询（带降级重试）
- `executeOnce(...)`：执行一次聚合查询
- `isTooLongFrameException(e)`：判断异常是否为 too_long_frame_exception

**修改方法：**
- `execute(request)`：支持降级重试
- `buildSearchRequest(...)`：新增 `downgradeLevel` 参数

**重构改进：**
- 使用 `IndexRouteProcessor.detectDowngradeLevelFromIndices()` 替代重复的检测逻辑

### 4. SimpleElasticsearchSearchProperties

**新增方法：**
- `validateDowngradeConfig()`：验证降级配置的合法性

**修改方法：**
- `validate()`：调用 `validateDowngradeConfig()` 进行降级配置验证

### 5. ErrorCode

**新增错误码：**
- `DOWNGRADE_FAILED`（SEARCH_DOWNGRADE_001）：降级失败
- `INDEX_ROUTE_FAILED`（SEARCH_DOWNGRADE_002）：索引路由失败
- `UNSUPPORTED_DOWNGRADE_LEVEL`（SEARCH_DOWNGRADE_003）：不支持的降级级别
- `INVALID_PARAMETER`（SEARCH_COMMON_001）：无效参数

### 6. ErrorMessage

**新增错误消息：**
- `DOWNGRADE_FAILED`：查询降级失败，已达到最大降级级别但仍然失败
- `INDEX_ROUTE_FAILED`：索引 [%s] 路由失败
- `UNSUPPORTED_DOWNGRADE_LEVEL`：降级级别 [%s] 不适用于日期粒度 [%s]

## 注意事项

### 1. 精度损失

使用通配符查询时可能会查询到不在原始日期范围内的索引。建议在查询条件中添加时间过滤：

```java
QueryRequest request = QueryRequest.builder()
    .index("daily_log")
    .dateRange(QueryRequest.DateRange.builder()
        .from("2025-01-15T00:00:00")
        .to("2025-01-20T23:59:59")
        .build())
    .query(QueryCondition.builder()
        .field("timestamp")
        .op("BETWEEN")
        .values(Arrays.asList("2025-01-15", "2025-01-20"))
        .build())
    .build();
```

### 2. 性能影响

降级级别越高，查询精度越低，性能可能下降：

| 降级级别 | 查询精度 | 查询性能 | 索引数量 |
|---------|---------|---------|---------|
| LEVEL_0 | 最高 | 最快 | 具体索引 |
| LEVEL_1 | 高 | 快 | 月级通配符 |
| LEVEL_2 | 中 | 中 | 年级通配符 |
| LEVEL_3 | 低 | 慢 | 全通配符 |

### 3. 版本兼容性

- Elasticsearch 6.x：完全兼容，HTTP请求行限制 4096字节
- Elasticsearch 7.x：完全兼容，HTTP请求行限制 4096字节
- Elasticsearch 8.x：兼容，HTTP请求行限制 8192字节（可配置更大阈值）

### 4. 日志输出

降级时会输出日志，便于排查问题：

```
WARN  IndexRouteProcessor - Query failed with too_long_frame_exception at level LEVEL_0, downgrading to LEVEL_1: index=daily_log
```

## 升级建议

### 从 1.0.9 升级

1. **无需修改代码**：降级功能默认启用，自动生效
2. **可选配置**：如需自定义降级行为，在 `application.yml` 中添加配置
3. **日志监控**：建议监控降级日志，了解降级触发情况

### 性能调优建议

1. **合理设置日期范围**：避免查询过长时间范围（如超过1年）
2. **监控降级频率**：频繁降级可能说明索引设计需要优化
3. **调整阈值**：根据实际情况调整 `autoDowngradeIndexCountThreshold` 和 `maxHttpLineLength`

## 兼容性

- 向后兼容：完全兼容 1.0.9 及之前版本
- 默认行为：降级功能默认启用，不影响现有查询
- 配置可选：所有配置项都有默认值，无需强制配置

## 依赖变更

无

## 贡献者

- surezzzzzz
