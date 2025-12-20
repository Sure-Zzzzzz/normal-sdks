# [1.0.2]

## 🚀 架构升级

- **全面整合 route-starter 1.0.3**：
  - 移除了 search-starter 中反射获取 RestHighLevelClient 的逻辑（60+ 行代码简化）
  - 通过 `SimpleElasticsearchRouteRegistry` 统一管理多数据源客户端
  - 使用 `RouteResolver` 进行索引路由解析
  - 所有 Elasticsearch 操作自动路由到对应数据源的版本自适应客户端

## ✨ 版本兼容性

- **ES 6.x 兼容性支持**：
  - 解决用户反馈的 ES 6.2.2 参数兼容性问题：
    ```
    Elasticsearch exception [type=illegal_argument_exception,
    reason=request contains unrecognized parameters: [include_type_name], [master_timeout]]
    ```
  - `MappingManagerImpl` 新增版本检测逻辑：
    - ES 6.x：使用 RestClient 低级 API 获取 mapping，绕过高级 API 自动添加的不兼容参数
    - ES 7.x+：使用标准的高级 API
    - 版本未知：先尝试高级 API，失败后自动降级到低级 API（fallback 机制）
  - 支持通过配置 `server-version` 显式指定 ES 版本，避免异步检测延迟

## 🔧 代码优化

- **资源管理改进**：
  - `MappingManagerImpl.getMappingViaLowLevelApi()`：使用 try-with-resources 确保 XContentParser 正确关闭
  - 规范资源管理，避免潜在的资源泄漏

- **简化配置类**：
  - `SimpleElasticsearchSearchAutoConfiguration`：移除 `restHighLevelClient()` @Bean 方法
  - 配置类职责更清晰，仅负责组件扫描和初始化日志

- **核心执行器改造**：
  - `QueryExecutorImpl`：使用 RouteResolver + Registry 替代单一 client 注入
  - `AggExecutorImpl`：同上，支持多数据源聚合查询
  - 每次请求动态获取对应数据源的版本自适应客户端

## ✅ 测试完善

- **多数据源路由测试**（新增 5 个测试用例）：
  - 7.1：查询 secondary 数据源索引（ES 6.2.2）
  - 7.2：secondary 索引范围查询
  - 7.3：secondary 索引聚合
  - 7.4：获取 secondary 索引字段信息
  - 7.5：版本兼容性验证（GetMappings 请求正常工作）

- **测试工具类**（新增）：
  - `TestIndexHelper`：版本自适应的索引创建工具
    - 自动检测 ES 版本（从 ClusterInfo 获取）
    - ES 6.x：mapping 包装在 `_doc` type 中 `{"_doc": {"properties": {...}}}`
    - ES 7.x+：直接使用 properties `{"properties": {...}}`
    - 仅用于测试，不属于 search-starter 正式 API

- **测试配置优化**：
  - `application.yaml`：配置 secondary 数据源（ES 6.2.2）
  - 索引配置添加 `lazy-load: true`，避免初始化时索引不存在的问题

## 📝 文档更新

- **README.md**：
  - 更新版本号至 1.0.2
  - 新增"多数据源路由"和"版本兼容性"特性说明
  - 添加多数据源配置示例（ES 6.2.2 + ES 7.x）
  - 补充版本兼容性说明和注意事项

- **JavaDoc 完善**：
  - `MappingManagerImpl`：添加版本兼容性说明
  - `QueryExecutorImpl` / `AggExecutorImpl`：注释路由逻辑
  - `TestIndexHelper`：详细说明版本差异处理

## 🐛 Bug 修复

- **测试用例修正**：
  - `SearchEndToEndTest.testMultiDatasourceQueryRange()`：修正预期值从 3 改为 4
    - 价格范围 [6000, 9000] 实际匹配 4 个产品（iPhone 15 Pro, MacBook Air, iPad Pro, Apple Watch Ultra）

## 📊 技术细节

- **依赖关系**：
  - 强依赖 `simple-elasticsearch-route-starter:1.0.3+`
  - route-starter 会自动被依赖引入，无需手动添加
- **向后兼容**：
  - 配置格式完全向后兼容 1.0.1
  - API 接口无变更
  - 现有业务代码无需修改
- **客户端管理**：
  - Registry 在启动时初始化所有数据源的 client 并缓存在 ConcurrentHashMap 中
  - 每次请求通过路由解析（规则缓存）+ Map 查找（O(1)）获取对应 client
  - 相比单一 client 注入多了路由步骤，但开销可忽略
- **版本检测**：
  - 异步执行，不阻塞 Spring 启动
  - 建议配置 `server-version` 以避免检测延迟
- **设计原则**：
  - search-starter 专注于 **search、aggregation、getMapping**（只读操作）
  - 不处理 createIndex、deleteIndex 等索引管理操作

## 🎯 升级指南

### 从 1.0.1 升级到 1.0.2

1. **更新依赖**：
   ```gradle
   implementation 'io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.0.2'
   ```

2. **配置文件**：
   - 无需修改配置，完全向后兼容
   - 如需多数据源，参考 README 中的"多数据源配置"示例

3. **代码变更**：
   - 业务代码无需任何修改
   - API 接口保持不变

4. **版本兼容性**（可选）：
   - 如果使用 ES 6.x，建议配置 `server-version`，避免异步检测延迟
   ```yaml
   sources:
     legacy:
       urls: http://legacy-es:9200
       server-version: 6.2.2
   ```

## 💡 使用建议

1. **推荐配置 server-version**：
   - 显式指定 ES 版本可以避免启动时的异步检测延迟
   - 确保框架立即使用正确的 API 适配策略

2. **多数据源场景**：
   - 使用 route-starter 的 `rules` 配置索引路由规则
   - 不同版本的 ES 集群可以共存，框架自动适配

3. **索引管理**：
   - search-starter 不负责索引创建/删除
   - 测试中使用 TestIndexHelper 仅作为示例
   - 生产环境建议使用专门的索引管理工具或脚本
