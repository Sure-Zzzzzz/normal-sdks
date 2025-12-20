# [1.0.3]

## 🎯 重大改进

### 1. 自定义异常体系
- **新增 4 个自定义异常类**，替代 `java.lang` 包异常，支持更精确的异常捕获和处理：
  - `SimpleElasticsearchRouteException`：基础异常类，包含 `errorCode` 字段
  - `ConfigurationException`：配置相关异常（替代 `IllegalArgumentException`、`IllegalStateException`）
  - `VersionException`：版本解析/探测异常
  - `RouteException`：路由相关异常

- **异常包装机制**：配置验证异常会被包装在 `ConfigurationException` 中，通过 `getCause()` 获取原始错误信息

### 2. 常量类抽取
- **新增 4 个常量类**，消除硬编码，提升代码可维护性：
  - `ErrorCode`：错误代码常量（如 `CONFIG_001`、`VERSION_001`、`ROUTE_001` 等）
  - `ErrorMessage`：中文错误消息模板
  - `ElasticsearchApiConstant`：ES API 相关常量（HTTP 方法、端点、正则表达式等）
  - `ConfigConstant`：配置默认值常量（超时时间、连接数、协议等）

### 3. 版本兼容性增强
- **RouteTemplateProxy 智能检测**：
  - 自动检测 Elasticsearch 版本兼容性问题（如 `unrecognized parameter: [master_timeout]`）
  - 发现版本兼容性错误时打印 WARN 日志，明确说明这是 Spring Data API 限制而非 route-starter 问题
  - 建议用户使用 `SimpleElasticsearchRouteRegistry.getHighLevelClient()` 获取版本自适应的原生客户端

- **完善的 JavaDoc 文档**：
  - `RouteTemplateProxy`：明确说明只负责路由，不负责版本差异屏蔽
  - `SimpleElasticsearchRouteRegistry.getTemplate()`：标注版本兼容性警告
  - `SimpleElasticsearchRouteRegistry.getHighLevelClient()`：强调版本自适应能力，推荐版本敏感操作使用

### 4. Registry 能力增强（延续）
- 新增 `SimpleElasticsearchRouteRegistry`：统一管理多数据源的 template / client
- 新增 `resolveDataSourceOrThrow(indices)`：校验并解析唯一数据源（不支持跨数据源）
- 新增 `getHighLevelClient(String datasourceKey)`：获取版本自适应的原生客户端（推荐）
- 新增 `getLowLevelClient(String datasourceKey)`：获取低级客户端
- 新增 `getClusterInfo(String datasourceKey)`：获取集群信息（包括版本）

### 5. 版本探测能力（延续）
- 新增 `sources.<key>.server-version`：手动配置 ES 服务端版本，配置后作为有效版本使用
- 新增 `version-detect.*` 配置：
  - `enabled`：是否启用版本探测（默认 true）
  - `timeout-ms`：探测超时时间（默认 1500ms）
  - `fail-fast-on-detect-error`：探测失败时是否快速失败（默认 false）
- 通过 `GET /` 自动探测 `version.number`，与配置版本不一致时告警（不覆盖配置）

## 🔧 架构调整

### 1. 异常处理重构
- **所有配置验证**：从抛出 `IllegalArgumentException`/`IllegalStateException` 改为 `ConfigurationException`
- **版本解析**：从抛出 `IllegalArgumentException` 改为 `VersionException`
- **路由查找**：从抛出 `IllegalArgumentException` 改为 `RouteException`
- **错误消息统一**：所有错误消息使用 `ErrorMessage` 常量，所有错误代码使用 `ErrorCode` 常量

### 2. 默认值规范化
- 所有默认值统一使用 `ConfigConstant` 常量类定义
- 包括：超时时间、连接数、Keep-Alive 时间、优先级范围、端口号、协议等

### 3. 日志级别优化
- `RouteResolver` 和 `RouteTemplateProxy` 中 null 索引路由日志从 DEBUG 降为 TRACE
- 减少默认日志输出，提升日志可读性

## ✅ 测试完善

### 1. 异常断言更新
- **ConfigValidationTest**（15 个测试用例）：
  - 从断言 `IllegalStateException` 改为 `ConfigurationException`
  - 通过 `exception.getCause().getMessage()` 检查原始错误消息

- **ServerVersionTest**：
  - 从断言 `IllegalArgumentException` 改为 `VersionException`

- **RouteRegistryResolveTest**：
  - 从断言 `IllegalArgumentException` 改为 `RouteException`

### 2. 版本兼容性测试
- **RoutingTest**：
  - 移除版本不兼容的 API 调用（`indexOps().getSettings()`）
  - 仅使用版本兼容的 API（`exists()`、`create()`）
  - 添加注释说明如何使用原生客户端进行版本敏感操作
  - 添加完整的版本兼容性说明文档

### 3. Spring Boot 测试注解
- 所有测试类添加 `@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)`
- 启用 Spring 上下文，支持 log4j2 日志输出

## 📝 版本兼容性说明

### route-starter 的版本屏蔽职责边界

**✅ route-starter 负责的版本屏蔽：**
- `RestHighLevelClient` 层面：根据 `server-version` 配置创建对应版本的客户端
- `RestClient` 层面：低级 HTTP 客户端，版本自适应
- 通过 `SimpleElasticsearchRouteRegistry.getHighLevelClient()` 获取的客户端是版本自适应的

**❌ route-starter 无法屏蔽的版本差异：**
- `ElasticsearchRestTemplate` 层面：Spring Data Elasticsearch 的封装，某些 API 不支持所有 ES 版本
- 例如：`IndexOperations.getSettings()` 在 ES 6.x 会因 `master_timeout` 参数报错

**💡 建议：**
- 常规 CRUD 操作：可以使用 `ElasticsearchRestTemplate`
- 版本敏感操作：使用 `registry.getHighLevelClient(datasourceKey)` 获取原生客户端
- 遇到版本兼容性错误时，route-starter 会自动检测并打印友好的 WARN 日志

## 🐛 问题修复
- 修复 `ErrorCode.OTHER_URL_EMPTY` 和 `ErrorCode.OTHER_URL_INVALID` 未实现的问题
- 完善 `RouteResolver` 和 `RouteTemplateProxy` 中 null 索引名称的处理逻辑

## 📚 文档更新
- 完善 `SimpleElasticsearchRouteRegistry` 的所有公开方法的 JavaDoc
- 新增版本兼容性说明（类级别和方法级别）
- 完善测试类的文档注释

## ⚠️ 破坏性变更

### 1. 异常类型变更
如果您的代码中捕获了以下异常，需要更新：

```java
// 旧代码（1.0.2 及之前）
try {
    properties.init();
} catch (IllegalStateException e) {
    // 处理配置错误
}

// 新代码（1.0.3+）
try {
    properties.init();
} catch (ConfigurationException e) {
    // 可以获取错误代码
    String errorCode = e.getErrorCode();
    // 获取原始错误消息
    String message = e.getCause().getMessage();
}
```

### 2. 异常消息位置变更
配置验证异常的消息现在在 `cause` 中：

```java
// 1.0.2 及之前
String message = exception.getMessage();

// 1.0.3+
String message = exception.getCause().getMessage();
```

## 🔄 升级指南

### 从 1.0.2 升级到 1.0.3

1. **更新依赖版本**：
```gradle
implementation 'io.github.sure-zzzzzz:simple-elasticsearch-route-starter:1.0.3'
```

2. **更新异常捕获代码**（如果有）：
```java
// 配置相关异常
catch (IllegalStateException e) → catch (ConfigurationException e)
catch (IllegalArgumentException e) → catch (ConfigurationException e)

// 版本相关异常
catch (IllegalArgumentException e) → catch (VersionException e)

// 路由相关异常
catch (IllegalArgumentException e) → catch (RouteException e)
```

3. **获取异常消息**：
```java
// 旧方式
String message = exception.getMessage();

// 新方式（配置验证异常）
String message = exception.getCause().getMessage();
String errorCode = exception.getErrorCode();
```

4. **版本敏感操作调整**（可选但推荐）：
```java
// 如果遇到版本兼容性问题（如 master_timeout 错误）
// 改为使用原生客户端：

@Autowired
private SimpleElasticsearchRouteRegistry registry;

RestHighLevelClient client = registry.getHighLevelClient("datasourceKey");
// 使用原生 ES API 进行操作
```

## 🎉 总结

**1.0.3 版本核心价值：**
- ✅ 更精确的异常类型，支持错误代码
- ✅ 零硬编码，所有常量统一管理
- ✅ 智能版本兼容性检测，友好的错误提示
- ✅ 完善的 JavaDoc 文档，明确职责边界
- ✅ 更健壮的测试覆盖

**适用场景：**
- 需要精确捕获和处理不同类型错误的应用
- 需要在多 ES 版本环境下稳定运行的应用
- 需要清晰了解版本兼容性边界的团队
