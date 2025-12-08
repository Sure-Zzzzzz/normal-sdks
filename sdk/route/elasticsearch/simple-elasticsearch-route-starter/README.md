# Simple Elasticsearch Route Starter

基于 Spring Boot 的 Elasticsearch 多数据源路由自动配置组件，支持根据索引名称自动路由到不同的 Elasticsearch 集群。

## 📦 依赖配置

### Gradle
```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-elasticsearch-route-starter:1.0.0'
    implementation "org.springframework.boot:spring-boot-starter-data-elasticsearch"
    implementation "org.apache.httpcomponents:httpclient"
    implementation "org.apache.httpcomponents:httpcore"
}
```

## 🔧 快速开始

### 1. 启用路由功能
在 `application.yml` 中启用 Elasticsearch 路由功能：

```yaml
# 
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
                hosts: localhost:9200
                username: elastic
                password: changeme
                connect-timeout: 5000      # 连接超时（毫秒）
                socket-timeout: 30000      # 读取超时（毫秒）
                use-ssl: false             # 是否使用 SSL
                skip-ssl-validation: false # 是否跳过 SSL 验证（仅开发环境）
                max-conn-total: 100        # 最大连接数
                max-conn-per-route: 10     # 每个路由的最大连接数
                enable-connection-reuse: true  # 是否启用连接重用
                keep-alive-strategy: 300   # Keep-Alive 保持时间（秒）
              
              cluster2:
                hosts: 192.168.1.100:9200,192.168.1.101:9200
                username: elastic
                password: cluster2pass
                connect-timeout: 10000
                socket-timeout: 60000
                use-ssl: false
                max-conn-total: 200
                max-conn-per-route: 20
              
              cluster3:
                hosts: es-prod.company.com:9200
                username: prod_user
                password: prod_pass
                connect-timeout: 5000
                socket-timeout: 30000
                use-ssl: true              # 生产环境建议使用 SSL
                skip-ssl-validation: false
                max-conn-total: 150
                max-conn-per-route: 15
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

### 4. 使用示例

#### 基本使用
```java
@Service
public class UserService {
    
    @Autowired
    private ElasticsearchRestTemplate elasticsearchTemplate;
    
    public void saveUser(UserDocument user) {
        // 根据索引名称自动路由到对应的数据源
        // 索引名称 "user-123" 会根据路由规则匹配到 cluster2 数据源
        elasticsearchTemplate.save(user); // 索引: user-001 -> 路由到 cluster2
    }
    
    public List<UserDocument> searchUsers(String keyword) {
        // 同样支持查询操作
        Query query = new NativeSearchQueryBuilder()
            .withQuery(QueryBuilders.matchQuery("name", keyword))
            .build();
            
        return elasticsearchTemplate.search(query, UserDocument.class).getContent();
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
              # 默认数据源
              default:
                hosts: localhost:9200
                username: elastic
                password: changeme
                connect-timeout: 5000      # 连接超时（毫秒）
                socket-timeout: 30000      # 读取超时（毫秒）
                use-ssl: false             # 是否使用 SSL
                skip-ssl-validation: false # 是否跳过 SSL 验证（仅开发环境）
                max-conn-total: 100        # 最大连接数
                max-conn-per-route: 10     # 每个路由的最大连接数
                enable-connection-reuse: true  # 是否启用连接重用
                keep-alive-strategy: 300   # Keep-Alive 保持时间（秒）
              
              # 业务数据源 1
              business:
                hosts: 192.168.1.100:9200,192.168.1.101:9200
                username: business_user
                password: business_pass
                connect-timeout: 10000
                socket-timeout: 60000
                use-ssl: false
                skip-ssl-validation: false
                max-conn-total: 200
                max-conn-per-route: 20
                enable-connection-reuse: true
                keep-alive-strategy: 300
              
              # 日志数据源
              logging:
                hosts: log-es.company.com:9200
                username: log_user
                password: log_pass
                connect-timeout: 5000
                socket-timeout: 30000
                use-ssl: true              # 生产环境建议使用 SSL
                skip-ssl-validation: false
                max-conn-total: 150
                max-conn-per-route: 15
                enable-connection-reuse: true
                keep-alive-strategy: 300
              
              # 监控数据源
              monitoring:
                hosts: monitor-es.company.com:9200
                username: monitor_user
                password: monitor_pass
                connect-timeout: 3000
                socket-timeout: 20000
                use-ssl: true
                skip-ssl-validation: false
                max-conn-total: 50
                max-conn-per-route: 10
                enable-connection-reuse: true
                keep-alive-strategy: 300
            
            # 路由规则配置
            rules:
              # 1. 系统配置数据 - 精确匹配
              - pattern: system_config
                type: exact         # 匹配类型（注意是 type 不是 match-type）
                datasource: default # 目标数据源
                priority: 1         # 优先级（数字越小优先级越高）
                enable: true       # 是否启用
              
              # 2. 用户相关数据 - 前缀匹配
              - pattern: user-
                type: prefix
                datasource: business
                priority: 2
                enable: true
              
              # 3. 产品数据 - 前缀匹配
              - pattern: product-
                type: prefix
                datasource: business
                priority: 3
                enable: true
              
              # 4. 订单数据 - 前缀匹配
              - pattern: order-
                type: prefix
                datasource: business
                priority: 4
                enable: true
              
              # 5. 应用日志 - 前缀匹配
              - pattern: app-log-
                type: prefix
                datasource: logging
                priority: 5
                enable: true
              
              # 6. 系统日志 - 前缀匹配
              - pattern: system-log-
                type: prefix
                datasource: logging
                priority: 6
                enable: true
              
              # 7. 错误日志 - 后缀匹配
              - pattern: -error
                type: suffix
                datasource: logging
                priority: 7
                enable: true
              
              # 8. 测试数据 - 通配符匹配
              - pattern: test_*
                type: wildcard
                datasource: default
                priority: 8
                enable: true
              
              # 9. 临时数据 - 通配符匹配
              - pattern: temp_*_backup
                type: wildcard
                datasource: default
                priority: 9
                enable: true
              
              # 10. 监控指标 - 正则表达式匹配
              - pattern: "^metric\\.\\w+\\.\\d{4}-\\d{2}-\\d{2}$"
                type: regex
                datasource: monitoring
                priority: 10
                enable: true
              
              # 11. 性能数据 - 正则表达式匹配
              - pattern: "^perf_.*_\\d{8}$"
                type: regex
                datasource: monitoring
                priority: 11
                enable: true

# 日志配置
logging:
  level:
    io.github.surezzzzzz.sdk.elasticsearch.route: DEBUG
    org.springframework.data.elasticsearch: INFO
```

## 🔍 工作原理

### 1. 自动配置流程

1. **条件激活**：当 `io.github.surezzzzzz.sdk.elasticsearch.route.enable=true` 时激活
2. **数据源初始化**：根据配置创建多个 `ElasticsearchRestTemplate` 实例
3. **代理创建**：使用 CGLIB 创建 `ElasticsearchRestTemplate` 的动态代理
4. **Bean 注册**：将代理对象注册为 Spring Bean，替换默认的 `ElasticsearchRestTemplate`

### 2. 路由决策流程

```
方法调用 → 提取索引名称 → 路由规则匹配 → 选择数据源 → 执行实际操作
```

1. **索引提取**：从方法参数中提取索引名称
   - `IndexCoordinates` 类型：直接获取索引名
   - `Class<?>` 类型：通过 `@Document` 注解获取索引名

2. **规则匹配**：按优先级遍历所有启用的路由规则
   - 精确匹配 (`EXACT`)：完全相等的字符串匹配
   - 前缀匹配 (`PREFIX`)：索引名称以指定前缀开始
   - 后缀匹配 (`SUFFIX`)：索引名称以指定后缀结束
   - 通配符匹配 (`WILDCARD`)：Ant 风格的通配符匹配
   - 正则匹配 (`REGEX`)：使用正则表达式匹配

3. **数据源选择**：根据匹配结果选择对应的 `ElasticsearchRestTemplate`
   - 匹配成功：使用规则指定的数据源
   - 匹配失败：使用默认数据源
