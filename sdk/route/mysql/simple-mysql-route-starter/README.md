# Simple MySQL Route Starter

面向 Spring Boot 的固定 MySQL datasource 路由 Starter。一个 datasource 始终代表确定的 JDBC URL、database 与连接账号；Route 只在已登记 datasource 之间选择，不在运行期执行 `USE database`、SQL 改写、自动跨库迁移、分库分表或跨 datasource Join。

## 适用范围与兼容性

| 项目 | 说明 |
|---|---|
| JDK | Java 8 |
| Spring Boot | `2.2.13.RELEASE`、`2.3.12.RELEASE`、`2.4.5`、`2.7.9` |
| 连接池 | 仅 HikariCP |
| 数据访问 | Spring Boot JDBC、`NamedParameterJdbcTemplate`、默认 `DataSourceTransactionManager`、单 datasource MyBatis 自动配置 |
| 事务 | 一个 Spring 事务只允许一个 datasource |

不支持 XA、2PC、Seata、跨 datasource 事务、动态切换 database、跨 datasource 自动迁移、自动高可用切换或分库分表。

## 引入依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-mysql-route-starter:1.1.1'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    runtimeOnly 'mysql:mysql-connector-java'
}
```

启用后，Starter 创建唯一的 `@Primary` Bean：`mysqlRouteRoutingDataSource`。Spring Boot 自动创建的 `JdbcTemplate`、`NamedParameterJdbcTemplate`、默认事务管理器和单 datasource MyBatis 自动配置都使用这个路由 DataSource。

## 最小配置

一个 datasource 也必须显式声明 `primary-datasource`。无 Route scope 的 JDBC 或 MyBatis 调用访问该 datasource。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        mysql:
          route:
            enable: true
            primary-datasource: test-order
            datasources:
              test-order:
                url: jdbc:mysql://mysql.example.com:3306/test_order?useUnicode=true&characterEncoding=UTF-8&useSSL=true&serverTimezone=UTC
                username: test_order_user
                password: ${TEST_ORDER_PASSWORD}
                driver-class-name: com.mysql.cj.jdbc.Driver
```

`datasources` 是唯一物理连接配置来源。Starter 为其中每一个 datasource 创建、验证、登记和关闭 Hikari 连接池；宿主不能将独立声明的物理连接交由 Route 复用或管理。

## 完整配置

下面的例子包含两个固定 datasource、完整的 datasource 字段、路由规则和常用 Hikari 参数。密码仅使用部署环境变量占位符。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        mysql:
          route:
            enable: true
            primary-datasource: test-order
            datasources:
              test-order:
                url: jdbc:mysql://mysql.example.com:3306/test_order?useUnicode=true&characterEncoding=UTF-8&useSSL=true&serverTimezone=UTC
                username: test_order_user
                password: ${TEST_ORDER_PASSWORD}
                driver-class-name: com.mysql.cj.jdbc.Driver
                hikari:
                  pool-name: test-order-route
                  maximum-pool-size: 12
                  minimum-idle: 3
                  connection-timeout: 5000
                  validation-timeout: 3000
                  idle-timeout: 600000
                  max-lifetime: 1800000
                  auto-commit: false
                  transaction-isolation: TRANSACTION_READ_COMMITTED
                  data-source-properties.cachePrepStmts: true
                  data-source-properties.prepStmtCacheSize: 250
              test-user:
                url: jdbc:mysql://mysql.example.com:3306/test_user?useUnicode=true&characterEncoding=UTF-8&useSSL=true&serverTimezone=UTC
                username: test_user_user
                password: ${TEST_USER_PASSWORD}
                driver-class-name: com.mysql.cj.jdbc.Driver
                hikari:
                  maximum-pool-size: 8
                  minimum-idle: 2
                  connection-timeout: 5000
            rules:
              - pattern: test_order
                match-type: exact
                datasource: test-order
                priority: 1000
                enable: true
              - pattern: test_user_*
                match-type: wildcard
                datasource: test-user
                priority: 1000
                enable: true
```

### 顶层与 datasource 配置

| 配置项 | 必填 | 默认值 | 说明 |
|---|---:|---|---|
| `enable` | 否 | `false` | 是否启用 MySQL Route。 |
| `primary-datasource` | 是 | 无 | 无显式 Route scope 时使用的 datasource 名称，必须精确命中 `datasources`。 |
| `datasources` | 是 | 无 | datasource 名称到完整连接定义的映射。名称只能包含字母、数字、`_`、`-`，且必须以字母或数字开头。 |
| `datasources.<name>.url` | 是 | 无 | 固定 JDBC URL。 |
| `datasources.<name>.username` | 是 | 无 | 固定连接账号，Route 只校验非空，不评估账号权限。 |
| `datasources.<name>.password` | 是 | 无 | 固定连接密码。 |
| `datasources.<name>.driver-class-name` | 否 | `com.mysql.cj.jdbc.Driver` | JDBC 驱动类名。 |
| `datasources.<name>.hikari` | 否 | 空 | Hikari 配置映射。 |

每个 datasource 都是独立连接身份。即使两个 datasource 指向同一 MySQL 实例，也应分别定义 database、账号和密码；Route 不会替调用方修改连接的 database 或权限。

### 路由规则配置

| 配置项 | 必填 | 默认值 | 说明 |
|---|---:|---|---|
| `rules[].pattern` | 是 | 无 | 业务 routeKey 模式。 |
| `rules[].match-type` | 否 | `exact` | 匹配类型。 |
| `rules[].datasource` | 是 | 无 | 命中规则后使用的 datasource 名称，必须已登记。 |
| `rules[].priority` | 否 | `1000` | 优先级，数值越大越先匹配。 |
| `rules[].enable` | 否 | `true` | 是否启用当前规则。 |

仅启用的规则参与解析。规则按 `priority` 降序匹配；优先级相同时，按 YAML 声明顺序匹配。

| `match-type` | 说明 | `pattern` 示例 | 可匹配 routeKey 示例 |
|---|---|---|---|
| `exact` | 精确匹配 | `test_order` | `test_order` |
| `prefix` | 前缀匹配 | `test_order` | `test_order_daily` |
| `suffix` | 后缀匹配 | `_archive` | `test_order_archive` |
| `wildcard` | `*` 通配符匹配 | `test_user_*` | `test_user_profile` |
| `regex` | Java 正则匹配 | `test_(order|user)` | `test_order` |

空白 datasource、未知 datasource、空白 routeKey、未命中规则和无效匹配规则都会直接失败，绝不回退到其他 datasource。显式 `executeOn(...)` scope 优先于 `primary-datasource`。

## Hikari 连接池配置

`hikari` 是 `Map<String, String>`。camelCase、kebab-case、snake_case 写法等价，例如 `maximumPoolSize`、`maximum-pool-size`、`maximum_pool_size` 等价。所有时长均以毫秒为单位。

连接身份只能写在 datasource 外层的 `url`、`username`、`password`、`driver-class-name`。Hikari 配置不能覆盖这些值。

| 配置项 | 类型 | 说明 |
|---|---|---|
| `connection-timeout` | long | 获取连接的最长等待时间。 |
| `validation-timeout` | long | 校验连接的最长等待时间。 |
| `connection-test-query` | String | JDBC 驱动没有 JDBC4 校验能力时执行的校验 SQL。 |
| `connection-init-sql` | String | 新建物理连接后执行的初始化 SQL。 |
| `maximum-pool-size` | int | 最大连接数。 |
| `minimum-idle` | int | 最小空闲连接数，必须不大于 `maximum-pool-size`。 |
| `idle-timeout` | long | 空闲连接回收时间。 |
| `max-lifetime` | long | 物理连接最大生命周期。 |
| `initialization-fail-timeout` | long | 启动期连接初始化失败等待策略。 |
| `auto-commit` | boolean | 连接默认自动提交行为。 |
| `read-only` | boolean | 连接默认只读标记。 |
| `transaction-isolation` | String | JDBC 事务隔离级别名称。 |
| `catalog` | String | 默认 catalog。 |
| `schema` | String | 默认 schema。 |
| `isolate-internal-queries` | boolean | 是否隔离连接池内部查询。 |
| `allow-pool-suspension` | boolean | 是否允许暂停连接池。 |
| `pool-name` | String | 连接池名称。 |
| `leak-detection-threshold` | long | 连接泄漏检测阈值。 |
| `register-mbeans` | boolean | 是否注册 Hikari JMX MBean。 |
| `exception-override-class-name` | String | `SQLExceptionOverride` 实现类的全限定类名。 |
| `data-source-properties.<driver-property>` | String | MySQL JDBC 驱动属性，不是 Hikari setter。 |

Hikari 的其余默认值和超时组合约束由当前 Spring Boot 管理的 Hikari 版本决定。Route 额外校验 `minimum-idle <= maximum-pool-size`，并在首次连接前调用 Hikari 校验；配置不支持热刷新。

以下配置会被拒绝：

- 连接身份或物理 DataSource 替换项：`jdbc-url`、`username`、`password`、`driver-class-name`、`data-source-class-name`、`data-source-jndi`、`data-source`；
- 仅在较新 Hikari 版本中可用的 `keepalive-time`；
- 需要对象或回调的配置：`metric-registry`、`metrics-tracker-factory`、`health-check-registry`、`health-check-properties`、`scheduled-executor`、`thread-factory`、`exception-override`、整体 `data-source-properties`。

未知、空白、类型不匹配或组合非法的 Hikari 配置都会失败。错误只包含 datasource 名称，不会回显 JDBC URL、账号、密码或 Hikari 配置值。

## 使用方式

### 按业务 routeKey 路由

```java
@Service
public class TestOrderQueryService {

    private final MySqlRouteTemplate mySqlRouteTemplate;

    public TestOrderQueryService(MySqlRouteTemplate mySqlRouteTemplate) {
        this.mySqlRouteTemplate = mySqlRouteTemplate;
    }

    public Integer countOrders() {
        return mySqlRouteTemplate.execute("test_order", () ->
                mySqlRouteTemplate.routingJdbcTemplate().queryForObject(
                        "SELECT COUNT(*) FROM test_order", Integer.class));
    }
}
```

### 显式选择 datasource

```java
public Integer countUsers(MySqlRouteTemplate mySqlRouteTemplate) {
    return mySqlRouteTemplate.executeOn("test-user", () ->
            mySqlRouteTemplate.namedParameterJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM test_user",
                    new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(), Integer.class));
}
```

### 确认多个 routeKey 使用同一 datasource

```java
public Integer countSameDatasource(MySqlRouteTemplate mySqlRouteTemplate) {
    return mySqlRouteTemplate.executeOnSameDatasource(
            java.util.Arrays.asList("test_order", "test_order_archive"),
            () -> mySqlRouteTemplate.routingJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM test_order", Integer.class));
}
```

`executeOnSameDatasource(...)` 只确认多个 routeKey 解析到同一个 datasource，不协调跨 datasource 执行或结果。

`routingJdbcTemplate()`、`namedParameterJdbcTemplate()` 和 `routingDataSource()` 都遵循当前 Route scope；无 scope 时使用 `primary-datasource`。`jdbcTemplate(datasource)` 与 `dataSource(datasource)` 仅用于非事务的显式目标访问，活动 Spring 事务中调用会失败，避免绕过事务路由边界。

## 事务、MyBatis 与迁移边界

同一 Spring 事务首次绑定 datasource 后，尝试切换至另一个 datasource 会在 callback 或 SQL 执行前失败。不要在事务内通过 `executeOn(...)`、直接目标模板或嵌套 scope 切换 datasource。

单 datasource MyBatis 自动配置会自动使用 `mysqlRouteRoutingDataSource`。宿主自行声明多个 DataSource、手工 `JdbcTemplate`、事务管理器、`SqlSessionFactory` 或多 datasource JPA 时，必须显式接入 `mysqlRouteRoutingDataSource`，并自行验证事务边界。

Flyway 或 Liquibase 无 Route scope 时只处理 `primary-datasource`。其他 datasource 的 schema 迁移必须由部署流程显式执行。

## 扩展与审计

| 扩展点 | 用途 |
|---|---|
| `MySqlRouteResolver` | 自定义 routeKey 到 datasource 名称的解析策略。 |
| `MySqlRouteDataSourceFactory` | 自定义物理 DataSource 的创建、连通性验证和关闭。 |
| `MySqlRoutePropertiesValidator` | 替换或增强配置校验。 |
| `MySqlRouteAuditPublisher` | 接收 Template 显式 callback 的脱敏审计事件。 |
| `MySqlRouteTemplate` | 替换显式 Route 执行门面。 |

自定义 `MySqlRouteDataSourceFactory` 必须完整承担 `create(...)`、`verify(...)`、`close(...)` 三个职责，并对其创建的 DataSource 生命周期负责。对象型 Hikari 能力只能通过该 SPI 自行创建、验证和关闭连接池。

`MySqlRouteAuditPublisher` 只在 `MySqlRouteTemplate.execute*()` 的 callback 边界发布事件。普通 JDBC、MyBatis、Flyway 和 Liquibase 调用不会自动发布事件；事件不包含 SQL、SQL 参数、结果、凭据、JDBC URL、原始 routeKey 或原始异常。

## 最佳实践

1. 部署时为每个 datasource 配置独立的最小权限账号；读写职责不同的 database 使用不同账号与授权。该建议由部署侧落实，Route 不按账号名称推断权限。
2. URL、database、账号和密码属于 datasource 的固定身份；同一 MySQL 实例的不同 database 仍应分别配置 datasource。
3. 密码只通过部署环境变量、密钥服务或受控配置注入；不要提交真实密码、JDBC URL 中的敏感参数或连接池诊断信息。
4. `primary-datasource` 只服务无 scope 调用；有明确业务归属时优先使用 routeKey 或 `executeOn(datasource, ...)`，不要依赖注册顺序。
5. 一个业务事务只访问一个 datasource。确有跨 datasource 一致性需求时，在业务层拆分流程、补偿和可观测性，不要尝试通过本 Starter 伪造分布式事务。
6. 连接池容量、超时和泄漏检测阈值按每个 datasource 的真实负载独立设置；上线前验证账号权限、连接数上限和 Hikari 超时组合。
7. 将逻辑多 datasource 覆盖与真实多 MySQL 实例覆盖分开验收；前者只证明 database/账号隔离，后者才证明物理实例路由正确。
