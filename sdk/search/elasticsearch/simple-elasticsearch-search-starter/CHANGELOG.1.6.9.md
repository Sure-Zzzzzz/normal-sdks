# v1.6.9 更新日志

**发布日期：** 2026-07-08

**类型：** Compatibility / Maintenance Release - 适配 route-starter 1.1.2，补齐多 Spring Boot 版本测试与 ES 6.x 兼容修复

**依赖升级：**

| 依赖 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-elasticsearch-search-core` | 1.0.12 | 1.0.12（不变） |
| `simple-elasticsearch-route-starter` | 1.0.10 | 1.1.2 |

---

## 适配说明

### 1. 升级 route-starter 至 1.1.2

`simple-elasticsearch-route-starter` 1.0.10 -> 1.1.2。1.1.x 向前兼容，日期分片 / 异步写 / 时区等新能力均配置驱动，不配不启用。

search-starter 继续复用 router 公开能力：

- `SimpleElasticsearchRouteRegistry`
- `RouteResolver`
- `SimpleElasticsearchRouteConstant`
- `ClusterInfo`

### 2. 补齐多 Spring Boot 版本测试

参照 route-starter 的多版本测试机制，search-starter 补齐 Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9 四版本测试矩阵：

- 新增 `SearchTestProfilesResolver`，按 `-Dspring.profiles.active=<版本>` 激活 `test-<版本>` profile
- 新增 `application-test-{2.7.9,2.4.5,2.3.12,2.2.x}.yaml` 版本专属测试配置
- primary 数据源随 Spring Boot 版本切换，secondary 固定 ES 6.2.2，保留 6.x 服务端兼容测试

### 3. 测试代码去除 ES 7.x 专属 API

6 个 e2e 测试类（CollapseTest / EventPublishEndToEndTest / ExpressionTest / IndexDowngradeEndToEndTest / NLQueryEndToEndTest / SearchEndToEndTest）原用 `org.elasticsearch.client.indices.CreateIndexRequest` / `GetIndexRequest`、`org.elasticsearch.xcontent.XContentType` 等 ES 7.x 专属包路径，在 Spring Boot 2.2.x（ES 6.x 客户端）下编译失败。

改造：统一走 low-level `RestClient`（`org.elasticsearch.client.RestClient`，6.x/7.x 包路径稳定），新增 `EsApiHelper` 提供跨版本索引管理（`indexExists` / `createIndex` / `deleteIndex` / `indexDoc` / `deleteDoc` / `refresh`），自带 6.x/7.x mapping 自适应（`_doc` 包装 vs `properties` 直铺）。`TestIndexHelper` 保留为薄封装并委托 `EsApiHelper`。

---

## 兼容修复

### 1. ES 6.x 低级查询补齐 IndicesOptions 参数

**问题**：ES 6.x 查询走 low-level `RestClient` 时，`SearchRequest.indicesOptions()` 不会自动序列化，导致 `ignore_unavailable` / `allow_no_indices` / `expand_wildcards` 丢失。调用方虽然配置了忽略不存在索引，ES 6.2.2 仍会对不存在的具体索引返回 404。

**修复**：`ElasticsearchCompatibilityHelper.executeSearchViaLowLevelApi()` 新增 `applyIndicesOptions()`，将 `IndicesOptions` 手动转为 low-level request query 参数。

### 2. 聚合响应解析适配 ES 6.x / 7.x 包路径差异

**问题**：ES 6.x 与 7.x 的聚合类包路径不同，直接 import 7.x metrics 类会导致 Spring Boot 2.2.x / ES 6.x 客户端下编译失败或解析不完整。

**修复**：`AggregationResponseParser` 改为反射识别并解析：

- `Stats` / `ExtendedStats`
- `Percentiles` / `PercentileRanks`
- `NumericMetricsAggregation.SingleValue`
- ES 6.x 原始 JSON 聚合响应

### 3. ES 6.x filters 聚合 buckets Map 结构解析

**问题**：ES 6.x 的 filters 聚合响应中 `buckets` 是对象 Map（key 为过滤器名），不是 terms/date_histogram 那样的数组。旧解析只处理数组，导致 filters 聚合结果为空。

**修复**：`parseEs6xBuckets()` 同时支持 List 与 Map 两种结构。

### 4. bucket_sort 反射参数类型修复

**问题**：`BucketSortPipelineAggregationBuilder.size()` 方法参数类型是 `Integer`，不是 `int`。反射查找 `int.class` 会失败。

**修复**：`BucketSortPipelineStrategy` 中 `size()` 反射参数类型改为 `Integer.class`。

### 5. percentile 迭代元素取值方法修复

**问题**：Percentile 迭代元素取值方法是 `getValue()`，不是 `value()`。旧逻辑会导致百分位结果退化为字符串或解析失败。

**修复**：新增 `AGG_METHOD_GET_VALUE = "getValue"` 常量，`parsePercentileIterable()` 使用 `getPercent()` + `getValue()`。

### 6. XContent 反射异常统一接入模块异常体系

**问题**：XContent 反射路径存在 `IllegalStateException`，不符合模块自定义异常规范。

**修复**：改为 `MappingException` + `ErrorCode` / `ErrorMessage`，并补齐 XContent 相关错误码与错误消息常量。

---

## ES 6.2.2 composite after_key 限制

ES 6.2.2 的 composite 聚合仍处于较早实现阶段，当返回 bucket 数量等于请求 `size` 时，不保证返回 `after_key`。因此在 Spring Boot 2.2.x / ES 6.2.2 组合下，composite 第一页可以返回 bucket，但不能稳定依赖 `afterKey` 做全量翻页。

处理方式：

- 主代码不伪造 `afterKey`，避免制造错误游标
- 2.2.x 测试跳过 composite after_key 游标断言
- README 明确记录：ES 6.2.2 下 composite 翻页不作为支持能力；需要全量聚合遍历时建议使用 ES 7.x 服务端

---

## 新增/变更文件

| 文件 | 类型 | 说明 |
|------|------|------|
| `build.gradle` | 修改 | route-starter 1.0.10 -> 1.1.2 |
| `version.properties` | 修改 | 1.6.8 -> 1.6.9 |
| `src/main/java/.../support/ElasticsearchCompatibilityHelper.java` | 修改 | ES 6.x low-level 查询补齐 IndicesOptions 参数 |
| `src/main/java/.../support/XContentReflectionHelper.java` | 修改 | 去除 `IllegalStateException`，统一异常体系 |
| `src/main/java/.../agg/executor/AggregationResponseParser.java` | 修改 | 聚合响应解析跨 ES 6.x/7.x 包路径与 JSON 结构适配 |
| `src/main/java/.../agg/builder/strategy/pipeline/BucketSortPipelineStrategy.java` | 修改 | `size()` 反射参数改为 `Integer.class` |
| `src/main/java/.../constant/ErrorCode.java` | 修改 | 新增聚合反射 / XContent 反射错误码 |
| `src/main/java/.../constant/ErrorMessage.java` | 修改 | 新增聚合反射 / XContent 反射错误消息 |
| `test/helper/EsApiHelper.java` | 新增 | RestClient 跨版本索引管理工具 |
| `test/helper/EsApiTestException.java` | 新增 | 测试侧 ES API 异常 |
| `test/helper/TestIndexHelper.java` | 修改 | 委托 EsApiHelper，移除 ES 7.x 专属 import |
| `test/SearchTestProfilesResolver.java` | 新增 | 多版本 profile 解析器 |
| `test/resources/application-test-2.7.9.yaml` | 新增 | 2.7.9 专属测试配置 |
| `test/resources/application-test-2.4.5.yaml` | 新增 | 2.4.5 专属测试配置 |
| `test/resources/application-test-2.3.12.yaml` | 新增 | 2.3.12 专属测试配置 |
| `test/resources/application-test-2.2.x.yaml` | 新增 | 2.2.x 专属测试配置 |
| `test/cases/*.java`（6 个 e2e） | 修改 | 移除 ES 7.x 专属 API，改用 EsApiHelper；加 `@ActiveProfiles(resolver = SearchTestProfilesResolver.class)` |
| `README.md` | 修改 | 更新版本选型、兼容矩阵与 ES 6.2.2 composite after_key 限制 |
| `LOCAL_TEST_COMMANDS.md` | 新增 | 本地多版本测试命令模板 |

---

## 向后兼容性

✅ **对外 API 向后兼容**

- 查询 / 聚合接口请求结构不变
- route-starter 1.1.2 向前兼容，新增能力配置驱动，不配不启用
- ES 6.x 低级查询参数补齐属于兼容修复
- ES 6.2.2 composite after_key 限制为服务端版本行为，1.6.9 仅明确文档与测试预期，不伪造游标

---

## 多版本验证

| Spring Boot | Profile | Gradle / Java | 验证范围 | 结果 |
|-------------|---------|---------------|----------|------|
| 2.7.9 | `2.7.9` | Gradle 8.5 / Java 11 | 全量 test | 通过（BUILD SUCCESSFUL） |
| 2.4.5 | `2.4.5` | Gradle 7.6 / Java 8 | 全量 test | 通过（BUILD SUCCESSFUL） |
| 2.3.12 | `2.3.12` | Gradle 7.6 / Java 8 | 全量 test | 通过（BUILD SUCCESSFUL） |
| 2.2.x | `2.2.x` | Gradle 7.6 / Java 8 | 全量 test | 通过（BUILD SUCCESSFUL；composite after_key 游标断言跳过） |

---

## 升级指南

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.6.9'
}
```

依赖的 `simple-elasticsearch-search-core:1.0.12` 不变，`simple-elasticsearch-route-starter` 升级至 1.1.2（向前兼容）。
