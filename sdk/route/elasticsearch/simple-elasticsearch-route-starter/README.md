# Simple Elasticsearch Route Starter

基于 Spring Boot 的 Elasticsearch 多数据源路由自动配置组件，支持根据索引名称自动路由到不同的 Elasticsearch 集群。

## ✨ 核心特性

- 🚀 **零代码侵入**：基于 Spring Boot AutoConfiguration，无需修改业务代码
- 🎯 **智能路由**：支持 5 种匹配模式（精确、前缀、后缀、通配符、正则）
- 📅 **日期分片**：写操作自动路由至当日索引，读操作通配模式查询历史分片（1.1.0+）
- ⚡ **异步写**：规则级别启用异步写，写请求提交线程池立即返回，适合日志/审计场景（1.1.0+）
- 🔌 **多版本兼容**：支持 Elasticsearch 6.x / 7.x，自动版本探测
- 💪 **版本自适应客户端**：提供 RestHighLevelClient，屏蔽版本差异
- ⚡ **高性能**：路由规则缓存、SpEL 表达式缓存、正则编译缓存
- 🛡️ **健壮性**：完善的配置验证、自定义异常体系、智能版本兼容性检测
- 📝 **完善文档**：详细的 JavaDoc、使用示例、版本兼容性说明

## 📦 依赖配置

### Gradle
```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-elasticsearch-route-starter:1.1.0'
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
- 探测失败且未配置 `server-version` 时，默认标记为 UNKNOWN 并继续启动；`fail-fast-on-detect-error=true` 时启动失败

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

## ⚠️ Spring Boot 版本兼容性

### ElasticsearchRestTemplate 路由代理

route-starter 使用动态代理创建 `ElasticsearchRestTemplate`，并按当前 Spring Data Elasticsearch API 形态选择直接调用、反射调用或 HTTP 兼容调用。

| Spring Boot 版本 | Spring Data Elasticsearch API 风格 | 路由代理 | 当前状态 |
|-----------------|------------------------------------|----------|----------|
| 2.2.x | 3.x | JDK 代理 + HTTP 兼容调用 | ✅ 支持 |
| 2.3.12 | 3.x / 4.x 过渡 | JDK 代理 + 反射兼容 | ✅ 支持 |
| 2.4.5 | 4.x | AUTO：CGLIB 失败时回退到 JDK | ✅ 支持 |
| 2.7.9（主版本） | 4.x | AUTO：优先 CGLIB | ✅ 支持，默认验证版本 |

**兼容说明：**

- Spring Boot 2.7.9 是主版本，默认优先保障该版本行为不退化。
- Spring Boot 2.2.x / 2.3.12 下，Spring Data Elasticsearch API 缺少部分新版方法或类型，例如 `IndexCoordinates`、接口级 `save(Object)`、接口级 `get(String, Class)`。
- 1.1.0 通过反射检测和 HTTP 兼容路径补齐旧版 API 能力，低版本也纳入正式适配范围。
- `proxy-type=AUTO` 优先使用 CGLIB；CGLIB 创建失败时回退到 JDK 代理。
- JDK 代理会额外挂载 `SaveAndGetInterface`，保证旧版 Spring Data Elasticsearch 中未声明在接口上的 `save/get` 也能被路由拦截。

### route-starter 的版本屏蔽职责边界

**✅ route-starter 负责的版本屏蔽：**
- ES 服务端版本：通过 `server-version` 或自动探测得到，用于记录集群版本与辅助兼容判断。
- RestHighLevelClient / RestClient：提供原生客户端访问入口，适合版本敏感操作。
- Spring Data Elasticsearch API 形态：通过反射和 HTTP 兼容调用适配 2.2.x / 2.3.12 / 2.4.5 / 2.7.9。
- `read-index-pattern` 下的 `get(id, Class)`：通过 `_search` + `ids` 查询支持通配索引读取。

**⚠️ 仍需注意的边界：**
- `ElasticsearchRestTemplate` 本身属于 Spring Data Elasticsearch 封装，个别 Spring Data API 与特定 ES 服务端版本组合可能仍存在参数或语义差异。
- 版本敏感操作建议优先使用 `registry.getHighLevelClient(datasourceKey)` 或 `registry.getLowLevelClient(datasourceKey)`。

**示例：**
```java
// 常规 CRUD：走路由代理
elasticsearchTemplate.save(document);
Document readBack = elasticsearchTemplate.get(id, Document.class);

// 版本敏感操作：使用原生客户端
RestHighLevelClient client = registry.getHighLevelClient("default");
GetSettingsRequest request = new GetSettingsRequest().indices("test");
GetSettingsResponse response = client.indices().getSettings(request, RequestOptions.DEFAULT);
```

## 🛡️ 异常处理

### 自定义异常体系（1.0.3+）

route-starter 提供了 4 个自定义异常类，支持更精确的异常捕获：

```java
// 1. 配置异常（启动时由 SimpleElasticsearchRouteValidator 抛出）
try {
    // 配置错误会在应用启动时自动检测并抛出详细错误
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
4. **代理创建**：根据 `proxyType` 配置（默认 AUTO）创建路由代理：优先 CGLIB，失败后自动回退到 JDK
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

### 3. Spring Data API 兼容处理

不同 Spring Boot 版本绑定的 Spring Data Elasticsearch API 不完全一致，route-starter 会在启动时检测当前 API 形态：

- 检测到 `IndexCoordinates` 时，优先使用 Spring Data Elasticsearch 4.x 的索引路由能力。
- 未检测到 `IndexCoordinates` 时，按 Spring Data Elasticsearch 3.x 兼容路径处理。
- 当前 API 未声明 `save(Object)` / `get(String, Class)` 时，使用 HTTP 兼容调用完成写入和按 ID 查询。
- `read-index-pattern` 场景下的 `get(id, Class)` 使用 `_search` + `ids` 查询，以支持通配索引读取。

## 🆕 1.1.0 新功能

### 1. 日期分片索引路由

针对日志、审计等按天分片的索引，写操作自动路由至当日索引，读操作使用通配符覆盖历史分片。

**配置示例：**

```yaml
rules:
  - pattern: "app-log-*"
    type: wildcard
    datasource: logging
    write-index-template: "app-log-{yyyy.MM.dd}"   # 写入当日索引，如 app-log-2026.07.01
    read-index-pattern: "app-log-*"                 # 查询覆盖所有历史分片
```

**行为说明：**

| 操作 | 使用的索引 | 说明 |
|------|-----------|------|
| save / index（写） | `app-log-2026.07.01`（当日） | 日期在主线程渲染，再提交执行 |
| search / get（读） | `app-log-*`（通配） | 覆盖所有历史分片 |
| 未配置 template/pattern | 原始索引名 | 按普通路由逻辑处理 |

**占位符格式（`{pattern}`）：**

| 占位符 | 渲染结果示例 | 说明 |
|--------|------------|------|
| `{yyyy.MM.dd}` | `2026.07.01` | 最常见，Logstash 风格 |
| `{yyyy-MM-dd}` | `2026-07-01` | ISO 日期 |
| `{yyyyMMdd}` | `20260701` | 紧凑格式 |

非法 DateTimeFormatter pattern（如 `{invalid!!}`）会打 WARN 日志并原样使用模板，不抛异常。

---

### 2. 异步写

规则级别启用异步写：写请求提交到独立线程池后立即返回 `null`，不等待 ES 响应。适合日志、埋点、审计等允许少量丢失的写场景。

**配置示例：**

```yaml
sources:
  logging:
    urls: http://log-es:9200
    async-write-thread-pool-size: 8    # 异步写线程池大小，默认 8

rules:
  - pattern: "app-log-*"
    type: wildcard
    datasource: logging
    async-write: true                  # 启用异步写
    write-index-template: "app-log-{yyyy.MM.dd}"
```

**行为：**
- `async-write=true` 时：主线程渲染模板后立即提交线程池，方法返回 `null`
- 异步执行异常会记录错误日志，不向上传播，不影响主业务
- 线程池按 datasource 隔离，互不影响
- Spring 容器关闭时优雅等待 30s，超时强制中断

**⚠️ 不适用场景：** 需要确认写入结果、强一致性要求、事务语义的场景。

**线程池默认参数（per datasource）：**

| 参数 | 默认值 |
|------|--------|
| 核心线程数 | `async-write-thread-pool-size`（默认 8）|
| 最大线程数 | `poolSize * 2` |
| 队列容量 | `1000`（超出走 CallerRunsPolicy） |
| keepAlive | 60s |
| 关闭等待 | 30s |

---

### 3. 可配置代理类型

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          route:
            proxy-type: AUTO    # CGLIB / JDK / AUTO（默认）
```

| 模式 | 说明 | 适用场景 |
|------|------|---------|
| `CGLIB`（默认优先） | 动态子类代理，方法覆盖最全 | Spring Boot 2.7.x + ES 7.17+ |
| `JDK` | 基于接口的动态代理 | 特殊兼容需求 |
| `AUTO` | 优先 CGLIB，失败后自动回退到 JDK | **推荐，保持默认** |

`AUTO` 在 Spring Boot 2.4.x 下 CGLIB 创建失败时会自动回退到 JDK 代理，多数据源路由正常工作。

---

## ⚙️ 完整配置示例

### application.yml

```yaml
spring:
  application:
    name: elasticsearch-route-demo

io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          route:
            # 是否启用路由功能
            enable: true

            # 默认数据源 key；未命中路由规则时使用
            default-source: default

            # 代理模式：CGLIB / JDK / AUTO，推荐保持 AUTO
            proxy-type: AUTO

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
                async-write-thread-pool-size: 8

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
                async-write-thread-pool-size: 8

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
                async-write-thread-pool-size: 16

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

              # 3. 应用日志 - 日期分片 + 异步写
              - pattern: app-log-*
                type: wildcard
                datasource: logging
                priority: 3
                enable: true
                write-index-template: app-log-{yyyy.MM.dd}
                read-index-pattern: app-log-*
                async-write: true

              # 4. 审计日志 - 按月分片 + 异步写
              - pattern: audit-log-*
                type: wildcard
                datasource: logging
                priority: 4
                enable: true
                write-index-template: audit-log-{yyyy.MM}
                read-index-pattern: audit-log-*
                async-write: true

              # 5. 年度归档 - 按年分片
              - pattern: archive-*
                type: wildcard
                datasource: business
                priority: 5
                enable: true
                write-index-template: archive-{yyyy}
                read-index-pattern: archive-*

              # 6. 错误日志 - 后缀匹配
              - pattern: -error
                type: suffix
                datasource: logging
                priority: 6
                enable: true

              # 7. 监控指标 - 正则表达式匹配
              - pattern: "^metric\\.\\w+\\.\\d{4}-\\d{2}-\\d{2}$"
                type: regex
                datasource: logging
                priority: 7
                enable: true

            # ES 服务端版本探测配置
            version-detect:
              enabled: true
              timeout-ms: 1500
              fail-fast-on-detect-error: false

logging:
  level:
    io.github.surezzzzzz.sdk.elasticsearch.route: DEBUG
    org.springframework.data.elasticsearch: INFO
```

这个示例包含 1.1.0 的新增配置：`proxy-type`、`async-write-thread-pool-size`、`write-index-template`、`read-index-pattern`、`async-write`，并同时覆盖精确、前缀、后缀、通配符、正则 5 种匹配类型。

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

- [CHANGELOG 1.1.0](./CHANGELOG.1.1.0.md) - **最新版本**
- [CHANGELOG 1.0.10](./CHANGELOG.1.0.10.md)
- [CHANGELOG 1.0.9](./CHANGELOG.1.0.9.md)
- [CHANGELOG 1.0.5](./CHANGELOG.1.0.5.md)
- [CHANGELOG 1.0.4](./CHANGELOG.1.0.4.md)
- [CHANGELOG 1.0.3](./CHANGELOG.1.0.3.md)
- [CHANGELOG 1.0.2](./CHANGELOG.1.0.2.md)
- [CHANGELOG 1.0.1](./CHANGELOG.1.0.1.md)

## 📄 License

Apache License 2.0

## 👤 Author

Sure-Zzzzzz (https://github.com/Sure-Zzzzzz)
