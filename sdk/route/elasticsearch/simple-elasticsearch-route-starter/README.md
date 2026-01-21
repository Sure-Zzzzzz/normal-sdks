# Simple Elasticsearch Route Starter

基于 Spring Boot 的 Elasticsearch 多数据源路由自动配置组件，支持根据索引名称自动路由到不同的 Elasticsearch 集群。

## ✨ 核心特性

- 🚀 **零代码侵入**：基于 Spring Boot AutoConfiguration，无需修改业务代码
- 🎯 **智能路由**：支持 5 种匹配模式（精确、前缀、后缀、通配符、正则）
- 🔌 **多版本兼容**：支持 Elasticsearch 6.x / 7.x，自动版本探测
- 💪 **版本自适应客户端**：提供 RestHighLevelClient，屏蔽版本差异
- ⚡ **高性能**：路由规则缓存、SpEL 表达式缓存、正则编译缓存
- 🛡️ **健壮性**：完善的配置验证、自定义异常体系、智能版本兼容性检测
- 📝 **完善文档**：详细的 JavaDoc、使用示例、版本兼容性说明

## 📦 依赖配置

### Gradle
```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-elasticsearch-route-starter:1.0.6'
    implementation "org.springframework.boot:spring-boot-starter-data-elasticsearch"
    implementation "org.apache.httpcomponents:httpclient"
    implementation "org.apache.httpcomponents:httpcore"
}
```

## 🔧 快速开始

### 1. 启用路由功能
在 `application.yml` 中启用 Elasticsearch 路由功能：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          route:
            enable: true  # 启用路由功能（注意是 enable 不是 enabled）
            default-source: default  # 默认数据源 key
```

### 2. 配置多数据源
配置多个 Elasticsearch 数据源：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          route:
            enable: true
            default-source: default
            sources:
              default:
                urls: http://localhost:9200              # 推荐使用 urls（完整 URL）
                # hosts: localhost:9200                  # 或使用 hosts（向后兼容）
                server-version: 7.17.9                   # 可选：ES 服务端版本（如：6.2.2），配置后作为有效版本使用
                username: elastic
                password: changeme
                connect-timeout: 5000                    # 连接超时（毫秒）
                socket-timeout: 30000                    # 读取超时（毫秒）
                use-ssl: false                           # 是否使用 SSL
                skip-ssl-validation: false               # 是否跳过 SSL 验证（仅开发环境）
                max-conn-total: 100                      # 最大连接数
                max-conn-per-route: 10                   # 每个路由的最大连接数
                enable-connection-reuse: true            # 是否启用连接重用
                keep-alive-strategy: 300                 # Keep-Alive 保持时间（秒）

              cluster2:
                urls: http://192.168.1.100:9200,http://192.168.1.101:9200
                server-version: 6.2.2                    # 支持 ES 6.x
                username: elastic
                password: cluster2pass
                connect-timeout: 10000
                socket-timeout: 60000
                use-ssl: false
                max-conn-total: 200
                max-conn-per-route: 20
```

### 3. 配置路由规则
根据索引名称配置路由规则：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          route:
            enable: true
            default-source: default
            sources:
              # ... 数据源配置 ...
            rules:
              # 系统日志路由到默认集群
              - pattern: system-log-*
                type: prefix        # 匹配类型（注意是 type 不是 match-type）
                datasource: default # 目标数据源
                priority: 1         # 优先级（数字越小优先级越高）
                enable: true       # 是否启用

              # 用户数据路由到集群2
              - pattern: user-*
                type: prefix
                datasource: cluster2
                priority: 2
                enable: true

              # 订单数据精确匹配路由到集群3
              - pattern: orders
                type: exact         # 精确匹配
                datasource: cluster3
                priority: 3
                enable: true

              # 以 _test 结尾的索引路由到默认集群
              - pattern: *_test
                type: wildcard      # 通配符匹配
                datasource: default
                priority: 4
                enable: true

              # 正则表达式匹配日期格式的索引
              - pattern: "^log-\\d{4}-\\d{2}-\\d{2}$"
                type: regex         # 正则表达式匹配
                datasource: cluster2
                priority: 5
                enable: true
```

### 4. ES 服务端版本探测（可选）

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          route:
            version-detect:
              enabled: true                         # 是否启用版本探测（默认 true）
              timeout-ms: 1500                      # 探测超时时间（默认 1500ms）
              fail-fast-on-detect-error: false      # 探测失败时是否快速失败（默认 false）
```

**版本探测说明：**
- 如果配置了 `server-version`，则使用配置值，探测结果仅用于校验（不一致时告警）
- 如果未配置 `server-version`，则使用探测结果
- 探测失败且未配置 `server-version` 时，启动会失败

### 5. 使用示例

#### 方式 1：使用 ElasticsearchRestTemplate（常规 CRUD）

```java
@Service
public class UserService {

    @Autowired
    private ElasticsearchRestTemplate elasticsearchTemplate;

    public void saveUser(UserDocument user) {
        // 根据索引名称自动路由到对应的数据源
        // 索引名称 "user-123" 会根据路由规则匹配到 cluster2 数据源
        elasticsearchTemplate.save(user);
    }

    public List<UserDocument> searchUsers(String keyword) {
        Query query = new NativeSearchQueryBuilder()
            .withQuery(QueryBuilders.matchQuery("name", keyword))
            .build();

        return elasticsearchTemplate.search(query, UserDocument.class).getContent();
    }
}
```

#### 方式 2：使用 Registry 获取版本自适应客户端（推荐）

```java
@Service
public class ElasticsearchService {

    @Autowired
    private SimpleElasticsearchRouteRegistry registry;

    /**
     * 使用原生 RestHighLevelClient（版本自适应，推荐）
     */
    public void getIndexSettings(String datasourceKey, String indexName) {
        // 获取版本自适应的原生客户端
        RestHighLevelClient client = registry.getHighLevelClient(datasourceKey);

        try {
            GetSettingsRequest request = new GetSettingsRequest().indices(indexName);
            GetSettingsResponse response = client.indices().getSettings(request, RequestOptions.DEFAULT);

            Settings settings = response.getIndexToSettings().get(indexName);
            log.info("Index settings: {}", settings);
        } catch (IOException e) {
            log.error("Failed to get index settings", e);
        }
    }

    /**
     * 获取集群信息（包括版本）
     */
    public void printClusterInfo(String datasourceKey) {
        ClusterInfo clusterInfo = registry.getClusterInfo(datasourceKey);
        if (clusterInfo != null) {
            log.info("Cluster version: {}", clusterInfo.getServerVersion());
        }
    }

    /**
     * 使用 ElasticsearchRestTemplate（适合常规 CRUD）
     */
    public void useTemplate(String datasourceKey) {
        ElasticsearchRestTemplate template = registry.getTemplate(datasourceKey);
        // 使用 template 进行操作
    }
}
```

#### 指定索引操作
```java
// 直接指定索引名称
IndexCoordinates index = IndexCoordinates.of("user-2024");
elasticsearchTemplate.index(indexOps -> indexOps.create(index));

// 使用实体类（@Document 注解）
@Document(indexName = "orders")
public class OrderDocument {
    // ... 实体定义
}

// 自动从实体类提取索引名称
OrderDocument order = new OrderDocument();
elasticsearchTemplate.save(order); // 索引: orders -> 路由到 cluster3
```

## 📝 版本兼容性说明

### route-starter 的版本屏蔽职责边界

**✅ route-starter 负责的版本屏蔽：**
- `RestHighLevelClient` 层面：根据 `server-version` 配置创建对应版本的客户端
- `RestClient` 层面：低级 HTTP 客户端，版本自适应
- 通过 `SimpleElasticsearchRouteRegistry.getHighLevelClient()` 获取的客户端是版本自适应的

**❌ route-starter 无法屏蔽的版本差异：**
- `ElasticsearchRestTemplate` 层面：Spring Data Elasticsearch 的封装，某些 API 不支持所有 ES 版本
- 例如：`IndexOperations.getSettings()` 在 ES 6.x 会因 `master_timeout` 参数报错

**💡 使用建议：**
- **常规 CRUD 操作**：可以使用 `ElasticsearchRestTemplate`（save、search、delete 等）
- **版本敏感操作**：使用 `registry.getHighLevelClient(datasourceKey)` 获取原生客户端
- **遇到版本兼容性错误**：route-starter 会自动检测并打印友好的 WARN 日志，提示使用原生客户端

**示例：**
```java
// ❌ 不推荐：某些版本下会报错
IndexOperations indexOps = template.indexOps(IndexCoordinates.of("test"));
Settings settings = indexOps.getSettings();  // ES 6.x 会报 master_timeout 错误

// ✅ 推荐：使用原生客户端，版本自适应
RestHighLevelClient client = registry.getHighLevelClient("default");
GetSettingsRequest request = new GetSettingsRequest().indices("test");
GetSettingsResponse response = client.indices().getSettings(request, RequestOptions.DEFAULT);
```

## 🛡️ 异常处理

### 自定义异常体系（1.0.3+）

route-starter 提供了 4 个自定义异常类，支持更精确的异常捕获：

```java
// 1. 配置异常
try {
    properties.init();
} catch (ConfigurationException e) {
    String errorCode = e.getErrorCode();  // 如：CONFIG_001
    String message = e.getCause().getMessage();  // 原始错误消息
    log.error("配置错误 [{}]: {}", errorCode, message);
}

// 2. 版本异常
try {
    ServerVersion.parse("invalid-version");
} catch (VersionException e) {
    String errorCode = e.getErrorCode();  // 如：VERSION_001
    log.error("版本解析失败 [{}]: {}", errorCode, e.getMessage());
}

// 3. 路由异常
try {
    String datasource = registry.resolveDataSourceOrThrow(new String[]{"index1", "index2"});
} catch (RouteException e) {
    String errorCode = e.getErrorCode();  // 如：ROUTE_001
    log.error("路由错误 [{}]: {}", errorCode, e.getMessage());
}

// 4. 基础异常（捕获所有 route-starter 异常）
try {
    // ... route-starter 相关操作
} catch (SimpleElasticsearchRouteException e) {
    String errorCode = e.getErrorCode();
    log.error("Route-starter 错误 [{}]: {}", errorCode, e.getMessage());
}
```

### 错误代码说明

| 错误代码前缀 | 说明 | 示例 |
|------------|------|------|
| `CONFIG_*` | 配置相关错误 | `CONFIG_001`: sources 为空 |
| `VERSION_*` | 版本相关错误 | `VERSION_001`: 版本为空 |
| `ROUTE_*` | 路由相关错误 | `ROUTE_001`: 数据源不存在 |
| `OTHER_*` | 其他错误 | `OTHER_001`: Client 提取失败 |

完整错误代码列表请参考 `io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode`

## 🔍 工作原理

### 1. 自动配置流程

1. **条件激活**：当 `io.github.surezzzzzz.sdk.elasticsearch.route.enable=true` 时激活
2. **数据源初始化**：根据配置创建多个 `RestHighLevelClient` 和 `ElasticsearchRestTemplate` 实例
3. **版本探测**：自动探测或使用配置的 ES 版本，创建对应版本的客户端
4. **代理创建**：使用 CGLIB 创建 `ElasticsearchRestTemplate` 的动态代理
5. **Bean 注册**：将代理对象注册为 Spring Bean，替换默认的 `ElasticsearchRestTemplate`

### 2. 路由决策流程

```
方法调用 → 提取索引名称 → 路由规则匹配 → 选择数据源 → 执行实际操作
```

1. **索引提取**：从方法参数中提取索引名称（责任链模式，动态加载）
   - `IndexCoordinatesExtractor` (Order 1)：从 `IndexCoordinates` 类型参数提取索引名
   - `EntityObjectExtractor` (Order 2)：从实体对象的 `@Document` 注解提取索引名（支持 SpEL）
   - `ClassTypeExtractor` (Order 3)：从 `Class<?>` 类型参数的 `@Document` 注解提取索引名（支持 SpEL）
   - `IndexQueryExtractor` (Order 4)：从 `IndexQuery` 参数提取手动指定的索引名（批量索引场景）
   - **支持自定义提取器**：实现 `IndexNameExtractor` 接口并标注 `@SimpleElasticsearchRouteComponent` + `@Order` 即可自动加载

2. **规则匹配**：按优先级遍历所有启用的路由规则
   - 精确匹配 (`exact`)：完全相等的字符串匹配
   - 前缀匹配 (`prefix`)：索引名称以指定前缀开始
   - 后缀匹配 (`suffix`)：索引名称以指定后缀结束
   - 通配符匹配 (`wildcard`)：Ant 风格的通配符匹配
   - 正则匹配 (`regex`)：使用正则表达式匹配

3. **数据源选择**：根据匹配结果选择对应的 `ElasticsearchRestTemplate`
   - 匹配成功：使用规则指定的数据源
   - 匹配失败：使用默认数据源

### 3. 版本兼容性检测

当使用 `ElasticsearchRestTemplate` 遇到版本兼容性问题时，`RouteTemplateProxy` 会自动检测并打印友好的 WARN 日志：

```
WARN  i.g.s.s.e.r.s.RouteTemplateProxy : 检测到 Elasticsearch 版本兼容性问题: method=[getSettings], index=[test_index].
这不是 simple-elasticsearch-route-starter 的问题，而是 Spring Data Elasticsearch API 与特定 ES 版本不兼容导致的.
建议使用 SimpleElasticsearchRouteRegistry.getHighLevelClient() 获取原生客户端进行版本敏感的操作.
原始错误: request [/test_index/_settings] contains unrecognized parameter: [master_timeout]
```

## ⚙️ 完整配置示例

### application.yml
```yaml
# 应用配置
spring:
  application:
    name: elasticsearch-route-demo

# Elasticsearch 路由配置
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          route:
            # 是否启用路由功能
            enable: true

            # 默认数据源 key（当没有匹配到任何规则时使用）
            default-source: default

            # 数据源配置
            sources:
              # 默认数据源（ES 7.x）
              default:
                urls: http://localhost:9200
                server-version: 7.17.9
                username: elastic
                password: changeme
                connect-timeout: 5000
                socket-timeout: 30000
                use-ssl: false
                skip-ssl-validation: false
                max-conn-total: 100
                max-conn-per-route: 10
                enable-connection-reuse: true
                keep-alive-strategy: 300

              # 业务数据源（ES 6.x）
              business:
                urls: http://192.168.1.100:9200,http://192.168.1.101:9200
                server-version: 6.2.2
                username: business_user
                password: business_pass
                connect-timeout: 10000
                socket-timeout: 60000
                use-ssl: false
                max-conn-total: 200
                max-conn-per-route: 20

              # 日志数据源（ES 7.x）
              logging:
                urls: https://log-es.company.com:9200
                server-version: 7.17.9
                username: log_user
                password: log_pass
                connect-timeout: 5000
                socket-timeout: 30000
                use-ssl: true
                skip-ssl-validation: false
                max-conn-total: 150
                max-conn-per-route: 15

            # 路由规则配置
            rules:
              # 1. 系统配置数据 - 精确匹配
              - pattern: system_config
                type: exact
                datasource: default
                priority: 1
                enable: true

              # 2. 用户相关数据 - 前缀匹配
              - pattern: user-
                type: prefix
                datasource: business
                priority: 2
                enable: true

              # 3. 应用日志 - 前缀匹配
              - pattern: app-log-
                type: prefix
                datasource: logging
                priority: 3
                enable: true

              # 4. 错误日志 - 后缀匹配
              - pattern: -error
                type: suffix
                datasource: logging
                priority: 4
                enable: true

              # 5. 测试数据 - 通配符匹配
              - pattern: test_*
                type: wildcard
                datasource: default
                priority: 5
                enable: true

              # 6. 监控指标 - 正则表达式匹配
              - pattern: "^metric\\.\\w+\\.\\d{4}-\\d{2}-\\d{2}$"
                type: regex
                datasource: logging
                priority: 6
                enable: true

            # 版本探测配置
            version-detect:
              enabled: true
              timeout-ms: 1500
              fail-fast-on-detect-error: false

# 日志配置
logging:
  level:
    io.github.surezzzzzz.sdk.elasticsearch.route: DEBUG
    org.springframework.data.elasticsearch: INFO
```

## 🚀 高级特性

### 1. SpEL 表达式支持

支持在 `@Document` 注解中使用 SpEL 表达式动态解析索引名称：

```java
@Document(indexName = "#{T(io.github.surezzzzzz.sdk.elasticsearch.route.test.DocumentIndexHelper).processIndexName('test_index')}")
public class TestDocument {
    // ...
}
```

### 2. 自定义索引名称提取器（1.0.6+）

支持动态加载自定义的索引名称提取器，无需修改框架代码：

```java
import io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent;
import io.github.surezzzzzz.sdk.elasticsearch.route.extractor.IndexNameExtractor;
import org.springframework.core.annotation.Order;

/**
 * 自定义提取器示例：从自定义注解中提取索引名
 */
@SimpleElasticsearchRouteComponent
@Order(10)  // 设置优先级，数字越小优先级越高（内置提取器已占用 1-4）
public class CustomAnnotationExtractor implements IndexNameExtractor {

    @Override
    public String extract(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        for (Object arg : args) {
            if (supports(arg)) {
                // 从自定义注解提取索引名
                MyIndexAnnotation annotation = arg.getClass().getAnnotation(MyIndexAnnotation.class);
                return annotation.indexName();
            }
        }

        return null;
    }

    @Override
    public boolean supports(Object arg) {
        return arg != null && arg.getClass().isAnnotationPresent(MyIndexAnnotation.class);
    }
}
```

**启动日志示例：**
```
INFO  SimpleElasticsearchRouteConfiguration : Loaded 5 IndexNameExtractor(s):
  [IndexCoordinatesExtractor, EntityObjectExtractor, ClassTypeExtractor, IndexQueryExtractor, CustomAnnotationExtractor]
```

### 3. 性能优化

- **SpEL 表达式缓存**：缓存编译后的 Expression 对象
- **Pattern 编译缓存**：正则表达式编译结果缓存
- **路由规则排序缓存**：启动时缓存排序后的规则列表

### 4. 配置验证

启动时进行 20+ 项配置检查，快速失败定位问题：

- 数据源配置验证
- 路由规则验证
- URL 格式验证
- 正则表达式语法验证
- 优先级范围验证
- 重复规则检测

## 📚 API 文档

### SimpleElasticsearchRouteRegistry

```java
@Autowired
private SimpleElasticsearchRouteRegistry registry;

// 获取版本自适应的 RestHighLevelClient（推荐）
RestHighLevelClient client = registry.getHighLevelClient("datasourceKey");

// 获取 ElasticsearchRestTemplate
ElasticsearchRestTemplate template = registry.getTemplate("datasourceKey");

// 获取低级 RestClient
RestClient lowLevelClient = registry.getLowLevelClient("datasourceKey");

// 获取集群信息（包括版本）
ClusterInfo clusterInfo = registry.getClusterInfo("datasourceKey");

// 解析唯一数据源（不支持跨数据源）
String datasource = registry.resolveDataSourceOrThrow(new String[]{"index1", "index2"});

// 获取所有 Template
Map<String, ElasticsearchRestTemplate> templates = registry.getTemplates();
```

## 🔗 相关链接

- [CHANGELOG 1.0.6](./CHANGELOG.1.0.6.md) - **最新版本**
- [CHANGELOG 1.0.5](./CHANGELOG.1.0.5.md)
- [CHANGELOG 1.0.4](./CHANGELOG.1.0.4.md)
- [CHANGELOG 1.0.3](./CHANGELOG.1.0.3.md)
- [CHANGELOG 1.0.2](./CHANGELOG.1.0.2.md)
- [CHANGELOG 1.0.1](./CHANGELOG.1.0.1.md)

## 📄 License

Apache License 2.0

## 👤 Author

Sure-Zzzzzz (https://github.com/Sure-Zzzzzz)
