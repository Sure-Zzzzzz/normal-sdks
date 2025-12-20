# [1.0.3]

## 🔥 异常体系重构

- **自定义异常体系**：
  - 新增异常基类 `SimpleElasticsearchSearchException`，携带 `errorCode` 字段便于精确定位问题
  - 新增业务异常类，实现业务闭环：
    - `ConfigurationException`：配置相关异常
    - `QueryException`：查询相关异常
    - `AggregationException`：聚合相关异常
    - `MappingException`：Mapping 相关异常
    - `FieldException`：字段相关异常

- **全面替换通用异常**：
  - 业务代码中所有 `IllegalArgumentException` 和 `RuntimeException` 替换为自定义异常
  - 涉及 7 个核心类：
    - `SimpleElasticsearchSearchProperties`：配置验证 → `ConfigurationException`
    - `QueryExecutorImpl`：查询执行 → `QueryException`
    - `AggExecutorImpl`：聚合执行 → `AggregationException`
    - `MappingManagerImpl`：Mapping 管理 → `MappingException`
    - `QueryDslBuilder`：查询 DSL 构建 → `FieldException` + `SimpleElasticsearchSearchException`
    - `AggregationDslBuilder`：聚合 DSL 构建 → `FieldException` + `SimpleElasticsearchSearchException`
    - `IndexRouteProcessor`：索引路由 → `SimpleElasticsearchSearchException`

## 🔧 API 异常处理优化

- **HTTP 状态码精确映射**：
  - `SimpleElasticsearchSearchApiEndpoint` 捕获 `SimpleElasticsearchSearchException` 返回 **400 Bad Request**
  - 业务验证错误（参数错误、字段不存在等）正确返回 400 而非 500
  - 保留通用 `Exception` 捕获，未预期错误返回 **500 Internal Server Error**

- **涉及 4 个端点**：
  - `POST /api/query`：查询数据
  - `POST /api/agg`：聚合查询
  - `GET /api/indices/{alias}/fields`：获取字段信息
  - `POST /api/indices/{alias}/refresh`：刷新 Mapping

## 📝 常量优化

- **ErrorMessage 精简**：
  - 删除未使用的常量：`INDEX_ALIAS_CONFIG_REQUIRED`、`DATE_FIELD_REQUIRED`
  - 统一错误消息为中文：
    - `LOAD_MAPPING_FAILED`：`Failed to load mapping for index: %s` → `索引 [%s] 的 mapping 加载失败`
    - `REFRESH_MAPPING_FAILED`：`Failed to refresh mapping for index: %s` → `索引 [%s] 的 mapping 刷新失败`

- **ErrorCode 新增**：
  - `SENSITIVE_FIELD_NAME_REQUIRED`：敏感字段名为空
  - `SENSITIVE_FIELD_STRATEGY_REQUIRED`：敏感字段策略为空
  - `SENSITIVE_FIELD_STRATEGY_INVALID`：敏感字段策略值非法

## ✨ 业务价值

- **更精确的错误定位**：每个异常携带 `errorCode`，便于日志追踪和问题排查
- **更清晰的异常分类**：按业务模块（配置、查询、聚合、Mapping、字段）分类，职责明确
- **更友好的 API 响应**：业务错误返回 400 状态码，符合 RESTful 规范
- **更好的国际化准备**：错误消息统一为中文，为后续多语言支持奠定基础

## 📊 技术细节

- **异常继承关系**：
  ```
  RuntimeException
    └── SimpleElasticsearchSearchException (errorCode: String)
          ├── ConfigurationException
          ├── QueryException
          ├── AggregationException
          ├── MappingException
          └── FieldException
  ```

- **错误码命名规范**：`SEARCH_{模块}_{序号}`
  - 配置：`SEARCH_CONFIG_001` ~ `SEARCH_CONFIG_009`
  - 查询：`SEARCH_QUERY_001` ~ `SEARCH_QUERY_008`
  - 聚合：`SEARCH_AGG_001` ~ `SEARCH_AGG_003`
  - Mapping：`SEARCH_MAPPING_001` ~ `SEARCH_MAPPING_004`
  - 字段：`SEARCH_FIELD_001` ~ `SEARCH_FIELD_003`

- **向后兼容**：
  - API 接口无变更
  - 配置格式无变更
  - 异常消息文本保持一致（仅类型变更）
  - 现有业务代码无需修改

## 🎯 升级指南

### 从 1.0.2 升级到 1.0.3

1. **更新依赖**：
   ```gradle
   implementation 'io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.0.3'
   ```

2. **配置文件**：
   - 无需修改，完全向后兼容

3. **代码变更**：
   - 业务代码无需修改
   - 如果有自定义异常处理逻辑，可以捕获具体的异常类型（如 `QueryException`）而非通用的 `RuntimeException`

4. **异常处理（可选优化）**：
   ```java
   try {
       queryExecutor.execute(request);
   } catch (QueryException e) {
       // 查询相关异常，可以获取 errorCode
       log.error("Query failed: {}, errorCode: {}", e.getMessage(), e.getErrorCode());
   } catch (MappingException e) {
       // Mapping 相关异常
       log.error("Mapping error: {}, errorCode: {}", e.getMessage(), e.getErrorCode());
   }
   ```

## 💡 使用建议

1. **日志记录**：
   - 异常日志中包含 `errorCode`，便于快速定位问题类型
   - 建议在监控系统中按 `errorCode` 进行错误统计和告警

2. **错误处理**：
   - 业务代码可以根据异常类型进行差异化处理
   - 捕获基类 `SimpleElasticsearchSearchException` 可以统一处理所有业务异常

3. **API 调用**：
   - 客户端根据 HTTP 状态码判断请求是否成功
   - 400 状态码表示参数错误，检查请求参数
   - 500 状态码表示服务器内部错误，联系服务端排查
