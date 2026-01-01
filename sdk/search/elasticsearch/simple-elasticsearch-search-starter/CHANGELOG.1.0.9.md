# [1.0.9]

## 🐛 Bug 修复

### 1. ignore-unavailable-indices 在所有索引都不可用时不生效

**问题描述**：
当配置 `ignore-unavailable-indices: true` 时，如果查询的**所有索引**都不存在，仍然会返回 404 错误：
```
MappingException [SEARCH_MAPPING_001]: 索引 [test_log_2099.01.01] 不存在或没有 mapping
```

例如查询日期分割索引 `test_log_*`，指定查询范围为 `2099-01-01 ~ 2099-01-31`，由于这些索引都不存在，MappingManager 在加载元数据时抛出异常，查询无法执行。

**根本原因**：
1. 在 `MappingManagerImpl.loadMetadata()` 中，当 GetMapping API 返回空 mappings 时，直接抛出 `MappingException`
2. 即使查询时设置了 `IndicesOptions.lenientExpandOpen()`，但在加载元数据阶段就已经失败
3. 配置的 `ignore-unavailable-indices` 仅在查询阶段生效，未在元数据加载阶段生效

**解决方案**：

**修复 MappingManagerImpl.java**（3 处修改）：

1. **高级 API 添加 IndicesOptions**（Lines 217-221）：
```java
if (properties.getQueryLimits().isIgnoreUnavailableIndices()) {
    request.indicesOptions(org.elasticsearch.action.support.IndicesOptions.lenientExpandOpen());
    log.trace("Enabled ignoreUnavailableIndices for GetMapping request on index: {}", indexName);
}
```

2. **低级 API 添加查询参数**（Lines 337-342）：
```java
if (ignoreUnavailable) {
    request.addParameter(SimpleElasticsearchSearchConstant.ES_PARAM_IGNORE_UNAVAILABLE,
            SimpleElasticsearchSearchConstant.ES_PARAM_VALUE_TRUE);
    request.addParameter(SimpleElasticsearchSearchConstant.ES_PARAM_ALLOW_NO_INDICES,
            SimpleElasticsearchSearchConstant.ES_PARAM_VALUE_TRUE);
}
```

3. **空 mappings 容错处理**（Lines 258-277）：
```java
if (mappings.isEmpty()) {
    if (properties.getQueryLimits().isIgnoreUnavailableIndices()) {
        log.debug("No mappings found for index [{}], returning empty metadata", indexName);
        return IndexMetadata.builder()
                .alias(alias)
                .indexName(indexName)
                .dateSplit(indexConfig.isDateSplit())
                .datePattern(indexConfig.getDatePattern())
                .dateField(indexConfig.getDateField())
                .actualIndices(new ArrayList<>())
                .fields(new ArrayList<>())
                .cachedAt(System.currentTimeMillis())
                .build();
    } else {
        throw new MappingException(ErrorCode.INDEX_MAPPING_NOT_FOUND,
                String.format(ErrorMessage.INDEX_MAPPING_NOT_FOUND, indexName));
    }
}
```

**修复效果**：
- ✅ **部分索引不存在**：正常查询已存在的索引（1.0.5 已支持）
- ✅ **所有索引都不存在**：返回空结果，不抛异常（新增修复）
- ✅ **混合场景**：自动忽略不存在的索引，仅查询存在的索引
- ✅ **向后兼容**：默认 `ignore-unavailable-indices: false` 时，行为不变

## ✨ 新特性

### Multi-fields（子字段）完整支持

**背景**：
Elasticsearch 的 multi-fields 特性允许一个字段以多种方式索引，最常见的场景是 text 字段同时拥有 keyword 子字段：

```json
{
  "mappings": {
    "properties": {
      "username": {
        "type": "text",
        "fields": {
          "keyword": {
            "type": "keyword"
          }
        }
      }
    }
  }
}
```

用户反馈三个问题：
1. `/fields` API 不返回 keyword 子字段，导致前端无法构建正确的查询条件
2. 使用 `username.keyword` 字段进行查询时，提示 "字段未找到"
3. 无法对 `username.keyword` 进行聚合和排序

**解决方案**：

#### 1. 元数据模型支持子字段

**FieldMetadata.java**（Line 75）：
```java
/**
 * 子字段（multi-fields，如 text 字段的 keyword 子字段）
 * key: 子字段名（如 "keyword"）
 * value: 子字段元数据
 */
private Map<String, FieldMetadata> subFields;
```

#### 2. 从 ES mapping 解析子字段

**MappingManagerImpl.java**（Lines 375-403）：
```java
// 解析 multi-fields（如 text 字段的 keyword 子字段）
Map<String, FieldMetadata> subFields = null;
if (fieldDef.containsKey(SimpleElasticsearchSearchConstant.ES_MAPPING_FIELDS)) {
    Map<String, Object> fieldsMap = (Map<String, Object>) fieldDef.get(SimpleElasticsearchSearchConstant.ES_MAPPING_FIELDS);
    subFields = new java.util.HashMap<>();
    for (Map.Entry<String, Object> subFieldEntry : fieldsMap.entrySet()) {
        String subFieldName = subFieldEntry.getKey();
        Map<String, Object> subFieldDef = (Map<String, Object>) subFieldEntry.getValue();
        String subFieldTypeStr = (String) subFieldDef.get(SimpleElasticsearchSearchConstant.ES_MAPPING_TYPE);
        FieldType subFieldType = FieldType.fromString(subFieldTypeStr);
        String fullSubFieldName = fieldName + "." + subFieldName;

        FieldMetadata subFieldMetadata = FieldMetadata.builder()
                .name(fullSubFieldName)
                .type(subFieldType)
                .array(false)
                .searchable(!isForbidden)
                .sortable(!isForbidden && subFieldType.isSortable())
                .aggregatable(!isForbidden && subFieldType.isAggregatable())
                .sensitive(isSensitive)
                .masked(isMasked)
                .reason(isForbidden ? SimpleElasticsearchSearchConstant.SENSITIVE_FIELD_REASON : null)
                .build();

        subFields.put(subFieldName, subFieldMetadata);
    }
}
```

#### 3. 子字段查找支持

**IndexMetadata.java**（Lines 82-98）：
```java
public void buildFieldMap() {
    if (fieldMap == null) {
        fieldMap = new ConcurrentHashMap<>();
    }
    if (fields != null) {
        for (FieldMetadata field : fields) {
            fieldMap.put(field.getName(), field);

            // 添加 multi-fields（子字段）到 map
            if (field.getSubFields() != null) {
                for (FieldMetadata subField : field.getSubFields().values()) {
                    fieldMap.put(subField.getName(), subField);
                }
            }
        }
    }
}
```

现在 `getField("username.keyword")` 可以正确找到子字段。

#### 4. API 响应包含子字段

**FieldInfoResponse.java**（Lines 75, 98-105）：
```java
/**
 * 子字段（multi-fields，如 text 字段的 keyword 子字段）
 */
private Map<String, FieldInfoResponse> subFields;

// 递归处理子字段
if (field.getSubFields() != null && !field.getSubFields().isEmpty()) {
    Map<String, FieldInfoResponse> subFieldsResponse = field.getSubFields().entrySet().stream()
            .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> FieldInfoResponse.from(e.getValue())
            ));
    builder.subFields(subFieldsResponse);
}
```

**API 响应示例**：
```json
{
  "data": {
    "fields": [
      {
        "name": "username",
        "type": "text",
        "searchable": true,
        "sortable": false,
        "aggregatable": false,
        "array": false,
        "subFields": {
          "keyword": {
            "name": "username.keyword",
            "type": "keyword",
            "searchable": true,
            "sortable": true,
            "aggregatable": true,
            "array": false
          }
        }
      }
    ]
  }
}
```

**功能验证**：

新增完整的端到端测试 `testMultiFieldsSupport()`，覆盖以下场景：

1. ✅ **Fields API**：验证 `/fields` 接口返回 keyword 子字段
2. ✅ **Text 字段模糊查询**：`username LIKE "alice"` → match query
3. ✅ **Keyword 子字段精确查询**：`username.keyword = "alice"` → term query
4. ✅ **Keyword 子字段聚合**：`username.keyword` terms aggregation
5. ✅ **Keyword 子字段排序**：按 `username.keyword` ASC 排序

**影响范围**：
- ✅ **查询**：可以使用 `username.keyword` 进行精确匹配
- ✅ **聚合**：可以对 `username.keyword` 进行 terms/cardinality 聚合
- ✅ **排序**：可以按 `username.keyword` 排序（text 字段不支持排序）
- ✅ **API**：`/fields` 接口返回完整的字段结构，包括子字段
- ✅ **向后兼容**：现有代码无需修改，仅新增功能

## 🔧 代码质量提升

### 消除硬编码字符串

**新增常量到 SimpleElasticsearchSearchConstant**：

**ES Mapping 字段名**（3 个）：
- `ES_MAPPING_FIELDS` = `"fields"`（用于解析 multi-fields）
- `ES_PARAM_IGNORE_UNAVAILABLE` = `"ignore_unavailable"`
- `ES_PARAM_ALLOW_NO_INDICES` = `"allow_no_indices"`
- `ES_PARAM_VALUE_TRUE` = `"true"`

**替换硬编码的文件**：
- `MappingManagerImpl.java`：5+ 处替换
- `SearchEndToEndTest.java`：10+ 处替换（使用 `QueryOperator.EQ.getOperator()` 和 `AggType.TERMS.getType()` 替代硬编码字符串）

## 📋 测试用例改进

### 新增端到端测试

**SearchEndToEndTest.java**：
- 新增 `testMultiFieldsSupport()` 测试方法（~130 行）
- 覆盖 multi-fields 的 5 种使用场景
- 验证 Fields API、查询、聚合、排序功能

**测试数据**：
- 在 `test_user_index` 中添加 `username` 字段（text + keyword 子字段）
- 使用简洁的用户名（"alice", "bob", "charlie" 等）便于测试

**代码优化**：
- 使用枚举常量替代硬编码字符串（`QueryOperator.LIKE.getOperator()`）
- 修复 API 端点路径（`/api/agg` 而非 `/api/aggregate`）
- 修复排序 API 调用（`.pagination().sort()` 而非 `.sorts()`）

## 📝 向后兼容

- ✅ **API 接口**：无变更，仅扩展 `FieldInfoResponse` 增加 `subFields` 字段
- ✅ **配置格式**：无新增配置，使用现有的 `ignore-unavailable-indices` 配置
- ✅ **功能行为**：
  - 默认行为不变（`ignore-unavailable-indices: false`）
  - 启用后，新增对"所有索引都不存在"场景的容错
- ✅ **响应格式**：`subFields` 仅在字段有子字段时返回，不影响现有字段
- ✅ **现有用户**：无需修改任何配置和代码

## 🎯 升级指南

### 从 1.0.8 升级到 1.0.9

1. **更新依赖**：
   ```gradle
   implementation 'io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.0.9'
   ```

2. **无需配置调整**：
   - 所有新功能自动生效
   - 如果已配置 `ignore-unavailable-indices: true`，现在对"所有索引都不存在"场景也生效

3. **测试验证**：
   - **Multi-fields**：
     - 调用 `/fields` API，验证包含 `subFields`
     - 使用 `xxx.keyword` 进行查询、聚合、排序，验证功能正常
   - **Ignore unavailable indices**：
     - 查询不存在的日期范围（如 2099 年），验证返回空结果而非 404

## 💡 使用建议

### Multi-fields 使用场景

**Text 字段模糊搜索**：
```java
QueryRequest request = QueryRequest.builder()
    .index("user")
    .query(QueryCondition.builder()
        .field("username")  // text 字段
        .op("like")
        .value("alice")
        .build())
    .build();
```

**Keyword 子字段精确查询**：
```java
QueryRequest request = QueryRequest.builder()
    .index("user")
    .query(QueryCondition.builder()
        .field("username.keyword")  // keyword 子字段
        .op("eq")
        .value("alice")
        .build())
    .build();
```

**Keyword 子字段聚合**：
```java
AggRequest request = AggRequest.builder()
    .index("user")
    .aggs(Arrays.asList(
        AggDefinition.builder()
            .name("username_terms")
            .type("terms")
            .field("username.keyword")  // keyword 子字段用于聚合
            .build()
    ))
    .build();
```

**Keyword 子字段排序**：
```java
QueryRequest request = QueryRequest.builder()
    .index("user")
    .pagination(PaginationInfo.builder()
        .size(10)
        .sort(Arrays.asList(
            PaginationInfo.SortField.builder()
                .field("username.keyword")  // keyword 子字段用于排序
                .order("ASC")
                .build()
        ))
        .build())
    .build();
```

### 日期分割索引容错

**配置示例**：
```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          search:
            query-limits:
              ignore-unavailable-indices: true  # 推荐启用
            indices:
              - name: "log_*"
                alias: log
                date-split: true
                date-pattern: "yyyy.MM.dd"
```

**查询示例**：
```java
// 查询未来日期范围（索引不存在）
QueryRequest request = QueryRequest.builder()
    .index("log_*")
    .dateRange(QueryRequest.DateRange.builder()
        .from("2099-01-01T00:00:00")
        .to("2099-01-31T23:59:59")
        .build())
    .build();
// 结果：返回空列表，不抛异常
```

## ⚠️ 注意事项

1. **Multi-fields 性能**：
   - Keyword 子字段会占用额外存储空间（完整索引）
   - 聚合和排序性能优于 text 字段（keyword 已分词）
   - 精确查询优先使用 keyword 子字段

2. **字段命名规范**：
   - 子字段使用 `.` 分隔（如 `username.keyword`）
   - SDK 自动识别并解析子字段结构
   - 前端可通过 `/fields` API 获取完整字段树

3. **ignore-unavailable-indices 数据完整性**：
   - 启用后，查询不会因索引缺失而报错，但可能返回不完整的数据
   - 建议在业务层做好数据完整性检查
   - 监控索引创建情况，及时发现索引缺失问题

4. **API 响应格式**：
   - `subFields` 仅在字段有子字段时返回
   - 无子字段的字段，`subFields` 为 `null`（不序列化）
   - 子字段递归嵌套（子字段也可以有子字段）

## 🔗 相关链接

- **问题报告**：
  1. 用户反馈：`ignore-unavailable-indices` 在所有索引都不存在时不生效
  2. 用户反馈：`/fields` API 不返回 keyword 子字段
  3. 用户反馈：无法使用 `xxx.keyword` 字段进行查询

- **解决方案**：
  1. 在元数据加载阶段也应用 `ignore-unavailable-indices` 配置
  2. 完整实现 multi-fields 解析、查找、API 响应
  3. 添加端到端测试覆盖所有使用场景
