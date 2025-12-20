# [1.0.5]

## 🐛 Bug 修复

### ES 6.x 聚合响应解析失败

**问题描述**：
在使用 Spring Boot 2.4.x（ES 6.x 客户端）查询 ES 6.x 集群时，聚合查询返回错误：
```
IOException: Failed to parse search response using XContent API
Root cause: XContentParseException: [1:308] [aggregations] unknown field [avg_price]
```

**根本原因**：
1. ES 7.x 客户端的 `SearchResponse.fromXContent()` 依赖 `NamedXContentRegistry` 解析聚合
2. 使用 `NamedXContentRegistry.EMPTY` 时缺少聚合解析器，无法识别聚合字段
3. 即使使用 `SearchModule` 创建 registry，ES 7.x 客户端仍无法正确解析 ES 6.x 的聚合响应格式

**解决方案**：

**方案 1：低级 API 检测 + 手动 JSON 解析**（已实现）

在 `ElasticsearchCompatibilityHelper.executeSearchViaLowLevelApi()` 中：
1. 先读取 HTTP 响应体到字节数组（避免流只能读一次）
2. 检测响应是否包含 `"aggregations"` 字段
3. 如果包含聚合，抛出 `Es6xAggregationResponseException`，携带原始 JSON
4. `AggExecutorImpl` 捕获此异常，使用 Jackson 手动解析聚合数据

**修复文件**：
- `ElasticsearchCompatibilityHelper.java`：
  - 新增 `Es6xAggregationResponseException` 异常类，携带原始 JSON
  - 在 `executeSearchViaLowLevelApi()` 中检测聚合并抛出异常

- `AggExecutorImpl.java`：
  - 捕获 `Es6xAggregationResponseException`，使用 Jackson 解析原始 JSON
  - 新增 `parseEs6xAggregationResponse()` 方法处理 ES 6.x 聚合响应
  - 新增 `parseAggregationValue()` 方法统一处理嵌套值格式：
    - Metrics 聚合：`{"value": 123.0}` → 提取为 `123.0`
    - Stats 聚合：`{"count": 10, "min": 1, "max": 100, ...}` → 统一字段名
    - Bucket 聚合：递归处理 `buckets` 数组

**影响范围**：
- ✅ **ES 7.x+**：使用高级 API 正常解析，性能无影响
- ✅ **ES 6.x**：自动使用低级 API + 手动 JSON 解析，聚合查询正常
- ✅ **多数据源混合版本**：自动适配每个数据源的版本
- ✅ **向后兼容**：完全兼容现有版本，无需修改配置

## ✨ 新特性

### 1. rawResponse 支持（ES 6.x 聚合场景）

**背景**：
用户从 Spring Boot 2.4.5（使用 RestTemplate/TransportClient 直接访问 ES）迁移到 search-starter 时，希望：
1. 业务代码**零侵入**，无需修改现有的 JSON 解析逻辑
2. 能够对比 SDK 解析后的数据和原始 ES 响应，验证数据正确性
3. 自主选择使用解析后的统一格式或原始 ES 格式

**新增配置**：

在 `api` 配置中新增 `include-raw-response` 选项：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          search:
            api:
              include-raw-response: false  # 默认 false，仅返回解析后数据
```

**配置说明**：

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `include-raw-response` | boolean | `false` | 是否返回原始聚合响应（仅 ES 6.x 聚合场景生效） |

**响应格式**：

**false（默认）**：仅返回解析后的统一格式
```json
{
  "data": {
    "aggregations": {
      "avg_price": 6439.0,
      "total_stock": 550.0
    },
    "took": 15
  }
}
```

**true**：同时返回原始响应和解析后数据
```json
{
  "data": {
    "aggregations": {
      "avg_price": 6439.0,
      "total_stock": 550.0
    },
    "rawResponse": {
      "avg_price": {"value": 6439.0},
      "total_stock": {"value": 550.0}
    },
    "took": 15
  }
}
```

**使用场景**：

✅ **推荐启用**（`true`）的场景：
- 从 Spring Boot 2.4.x + RestTemplate/TransportClient 迁移到 SDK
- 需要兼容现有的 JSON 解析逻辑（如直接读取 `{"value": xxx}` 结构）
- 需要对比验证 SDK 解析结果的正确性

❌ **不推荐启用**（保持 `false`）的场景：
- 新项目，直接使用 SDK 的统一格式
- 不需要访问原始 ES 响应
- 查询 ES 7.x+ 数据源（rawResponse 仅在 ES 6.x 聚合场景下有值）

**技术实现**：
- 仅在 ES 6.x 聚合场景下，`AggExecutorImpl` 会将原始 JSON 解析结果填充到 `rawResponse` 字段
- ES 7.x+ 聚合场景下，`rawResponse` 始终为 `null`（不会序列化到响应中）

### 2. 日期分割索引查询容错配置

**背景**：
在查询日期分割索引时，如果查询范围内某些日期的索引不存在（例如查询最近 90 天数据，但只有 60 天的索引），Elasticsearch 默认会返回 404 错误：
```
index_not_found_exception: no such index [user_behavior_2025.10.17]
```

这在日期分割场景下非常常见：
- 查询范围覆盖未来日期（索引尚未创建）
- 查询历史数据（索引已删除或归档）
- 新索引刚创建，历史数据还未完全导入

**新增配置**：

在 `query-limits` 中新增 `ignore-unavailable-indices` 配置项：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          search:
            query-limits:
              ignore-unavailable-indices: true  # 默认 false
```

**配置说明**：

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `ignore-unavailable-indices` | boolean | `false` | 是否忽略不存在的索引 |

**行为差异**：

- **false**（默认）：严格模式，查询不存在的索引时抛出 `index_not_found_exception`
- **true**：宽松模式，忽略不存在的索引，仅查询已存在的索引，不报错

**使用场景**：

✅ **推荐启用**（`true`）的场景：
- 日期分割索引查询（如 `log_2025.12.20`）
- 查询范围经常覆盖不存在的索引
- 希望查询在部分索引缺失时仍能返回已有数据

❌ **不推荐启用**（保持 `false`）的场景：
- 普通索引查询（非日期分割）
- 希望在索引不存在时立即感知并处理
- 需要严格的索引存在性校验

**技术实现**：

当配置为 `true` 时，`QueryExecutorImpl` 和 `AggExecutorImpl` 会为 `SearchRequest` 设置：

```java
searchRequest.indicesOptions(IndicesOptions.lenientExpandOpen());
```

这会让 Elasticsearch 采用宽松策略：
- `lenient`：忽略不可用的索引，不抛出异常
- `expandOpen`：自动展开通配符，仅匹配打开的索引

**注意事项**：
1. **数据完整性**：启用后，查询不会因索引缺失而报错，但可能返回不完整的数据
2. **索引命名规范**：确保日期分割索引严格遵循 `date-pattern` 配置的格式
3. **监控告警**：建议监控索引创建情况，及时发现索引缺失问题

## 🔧 代码质量提升

### 1. 消除重复代码

**重构 ElasticsearchCompatibilityHelper**：
- 抽取 `createXContentParser()` 方法：统一 XContent parser 创建逻辑（~40 行）
- 抽取 `closeParser()` 方法：安全关闭 parser（~10 行）
- 重构 `parseSearchResponseWithXContent()` 和 `parseResponse()` 方法，消除 **~150 行重复代码**

**代码复用效果**：
- 两个方法从 ~260 行减少到 ~110 行（净减少 150 行）
- 90% 的重复代码被抽取到公共方法
- 提高可维护性：修改 XContent API 逻辑只需改一处

### 2. 消除硬编码字符串

**新增 40+ 个常量到 SimpleElasticsearchSearchConstant**：

**ES JSON 字段名**（13 个）：
- `ES_JSON_AGGREGATIONS` = `"aggregations"`
- `ES_JSON_VALUE` = `"value"`
- `ES_JSON_BUCKETS` = `"buckets"`
- `ES_JSON_KEY` = `"key"`
- `ES_JSON_KEY_AS_STRING` = `"key_as_string"`
- `ES_JSON_DOC_COUNT` = `"doc_count"`
- `ES_JSON_COUNT` = `"count"`
- `ES_JSON_MIN` = `"min"`
- `ES_JSON_MAX` = `"max"`
- `ES_JSON_AVG` = `"avg"`
- `ES_JSON_SUM` = `"sum"`

**ES Mapping 字段名**（4 个）：
- `ES_MAPPING_PROPERTIES` = `"properties"`
- `ES_MAPPING_TYPE` = `"type"`
- `ES_MAPPING_FORMAT` = `"format"`
- `ES_DEFAULT_DOC_TYPE` = `"_doc"`

**ES API 参数名**（3 个）：
- `ES_PARAM_INCLUDE_TYPE_NAME` = `"include_type_name"`
- `ES_PARAM_MASTER_TIMEOUT` = `"master_timeout"`
- `ES_ERROR_UNRECOGNIZED_PARAMETER` = `"unrecognized parameter"`

**Java 反射相关常量**（20+ 个）：
- XContent 包路径：`XCONTENT_PACKAGE_ES7`, `XCONTENT_PACKAGE_ES6`
- 类名后缀：`XCONTENT_CLASS_TYPE`, `XCONTENT_CLASS_FACTORY`, `XCONTENT_CLASS_PARSER` 等
- ES 内部类：`ES_CLASS_SEARCH_MODULE`, `ES_CLASS_SETTINGS`
- 字段名：`FIELD_EMPTY`, `FIELD_JSON`, `FIELD_THROW_UNSUPPORTED_OPERATION`
- 方法名：`METHOD_GET_NAMED_XCONTENTS`, `METHOD_XCONTENT`, `METHOD_CREATE_PARSER`, `METHOD_FROM_XCONTENT`

**替换硬编码的文件**：
- `ElasticsearchCompatibilityHelper.java`：15+ 处替换
- `MappingManagerImpl.java`：7+ 处替换
- `AggExecutorImpl.java`：25+ 处替换
- `IndexRouteProcessor.java`：1 处替换

### 3. 优化日志级别

**修改不当的 WARN 日志**：
- `ElasticsearchCompatibilityHelper.java`：
  - Line 282: `log.warn("⚠ Detected aggregations...")` → `log.debug("Detected aggregations...")`

- `AggExecutorImpl.java`：
  - Line 124: `log.warn("ES 6.x aggregation response detected...")` → `log.debug("ES 6.x aggregation response detected...")`

**原则**：
- **DEBUG**：正常流程的兼容性处理（如检测到 ES 6.x 聚合）
- **WARN**：需要关注但不影响功能的问题（如无法创建 NamedXContentRegistry）

## 📝 向后兼容

- ✅ **API 接口**：无变更
- ✅ **配置格式**：新增可选配置项 `include-raw-response`，未配置时使用默认值 `false`
- ✅ **功能行为**：默认行为不变（`false`），完全向后兼容
- ✅ **现有用户**：无需修改任何配置，行为与 1.0.4 完全一致
- ✅ **响应格式**：未启用 `include-raw-response` 时，响应格式与 1.0.4 完全一致

## 🎯 升级指南

### 从 1.0.4 升级到 1.0.5

1. **更新依赖**：
   ```gradle
   implementation 'io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.0.5'
   ```

2. **配置调整**（可选）：

   **如果从 Spring Boot 2.4.x + RestTemplate/TransportClient 迁移，且需要兼容现有 JSON 解析逻辑**：
   ```yaml
   io:
     github:
       surezzzzzz:
         sdk:
           elasticsearch:
             search:
               api:
                 include-raw-response: true  # 启用原始响应
   ```

   **如果使用日期分割索引且希望容错查询**：
   ```yaml
   io:
     github:
       surezzzzzz:
         sdk:
           elasticsearch:
             search:
               query-limits:
                 ignore-unavailable-indices: true  # 忽略不存在的索引
   ```

3. **测试验证**：
   - ES 6.x 聚合查询：验证聚合结果正确性
   - ES 7.x+ 聚合查询：验证不受影响
   - 启用 `include-raw-response` 后，验证 `rawResponse` 字段存在且包含原始格式
   - 启用 `ignore-unavailable-indices` 后，验证查询跨越不存在索引的范围时不报错

## 💡 使用建议

### ES 6.x 聚合查询
- SDK 已自动兼容 ES 6.x 聚合响应，无需特殊配置
- 默认返回统一格式的解析结果，推荐直接使用
- 如需原始响应，启用 `include-raw-response: true`

### 代码迁移
如果从旧代码迁移，现有的 JSON 解析逻辑可以这样兼容：

**旧代码**（使用 RestTemplate 直接访问 ES）：
```java
// 旧代码直接读取 ES 原始响应
Map<String, Object> aggregations = response.get("aggregations");
Double avgPrice = ((Map<String, Object>) aggregations.get("avg_price")).get("value");
```

**方案 1：启用 rawResponse（零侵入）**
```yaml
api:
  include-raw-response: true
```
```java
// 业务代码完全不用改
Map<String, Object> rawResponse = aggResponse.getRawResponse();
Double avgPrice = ((Map<String, Object>) rawResponse.get("avg_price")).get("value");
```

**方案 2：使用解析后数据（推荐）**
```java
// 修改业务代码，使用统一格式
Map<String, Object> aggregations = aggResponse.getAggregations();
Double avgPrice = (Double) aggregations.get("avg_price");  // 直接获取值
```

### 日志调整
- 升级后 ES 6.x 聚合查询不再产生 WARN 日志
- 如需查看兼容性处理细节，开启 DEBUG 日志：
  ```yaml
  logging:
    level:
      io.github.surezzzzzz.sdk.elasticsearch.search: DEBUG
  ```

### 日期分割索引容错
- **推荐启用** `ignore-unavailable-indices: true`，避免因部分索引不存在导致查询失败
- 特别适合查询范围较大的场景（如查询最近 90 天）
- 配置示例：
  ```yaml
  query-limits:
    ignore-unavailable-indices: true
  indices:
    - name: "log_*"
      alias: log
      date-split: true
      date-pattern: "yyyy.MM.dd"
  ```

## ⚠️ 注意事项

1. **rawResponse 仅在 ES 6.x 聚合场景下有值**：
   - ES 7.x+ 聚合场景：`rawResponse` 为 `null`（不会序列化到响应）
   - 非聚合查询（如普通 query）：`rawResponse` 为 `null`

2. **数据格式差异**：
   - `aggregations`（解析后）：`{"avg_price": 6439.0}`（扁平格式）
   - `rawResponse`（原始）：`{"avg_price": {"value": 6439.0}}`（嵌套格式）

3. **性能影响**：
   - 启用 `include-raw-response` 后，响应体会略微增大（增加原始聚合数据）
   - 解析性能无明显影响（Jackson 解析速度快）

4. **ignore-unavailable-indices 数据完整性**：
   - 启用后，查询不会因索引缺失而报错，但可能返回不完整的数据
   - 建议在业务层做好数据完整性检查
   - 监控索引创建情况，及时发现索引缺失问题

## 🔗 相关链接

- **问题报告**：用户反馈 ES 6.x 聚合响应解析失败
- **根本原因**：ES 7.x 客户端的 NamedXContentRegistry 无法解析 ES 6.x 聚合格式
- **解决方案**：低级 API + 手动 JSON 解析
- **迁移支持**：rawResponse 功能帮助用户零侵入迁移
