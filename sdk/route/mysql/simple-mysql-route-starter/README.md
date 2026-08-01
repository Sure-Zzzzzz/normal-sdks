# Simple MySQL Route Starter

面向 Spring Boot 的显式 MySQL 路由组件。业务通过 `routeKey`，或 Ops 等管理侧通过完整 `datasourceKey`，选择已配置的 **固定 cluster + 固定 database** 目标，并在受控 callback 中执行 JDBC 操作。

接入路径：引入依赖 → 提供凭据解析 SPI → 配置 cluster、datasource 和 rule → 使用 `MySqlRouteTemplate` 执行。

- 路由入口是显式 callback，不使用业务注解或 AOP。
- 一个 `datasourceKey` 对应一个固定 database 的物理 DataSource，不会在运行期切换 database。
- 支持 `JdbcTemplate`、`NamedParameterJdbcTemplate`，也可以将路由 DataSource 显式绑定到业务自己的 MyBatis 配置。
- 不存在默认 datasource 回退；未命中 routeKey 或未知 datasourceKey 会直接失败。
- 不替换宿主默认 `DataSource`，也不创建事务管理器。

## 依赖与前置条件

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-mysql-route-starter:1.0.0'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    runtimeOnly 'mysql:mysql-connector-java'
}
```

启用模块：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        mysql:
          route:
            enable: true
```

启用后，应用必须提供一个 `MySqlRouteCredentialResolver` Bean。Route 配置中的 `credential-ref` 只是凭据定位符，不是用户名或密码。

## 快速接入

### 1. 配置固定目标与路由规则

以下示例使用两个 MySQL cluster 和三个固定 database 目标。示例中的名称仅用于说明，不代表业务库表或真实环境。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        mysql:
          route:
            enable: true
            clusters:
              test-primary:
                host: mysql-primary
                port: 3306
                credential-ref: test-primary-reader
                driver-class-name: com.mysql.cj.jdbc.Driver
                connection-properties:
                  useUnicode: true
                  characterEncoding: UTF-8
              test-archive:
                host: mysql-archive
                credential-ref: test-archive-reader
            datasources:
              test-record-primary:
                cluster-key: test-primary
                database: test_record
              test-audit-primary:
                cluster-key: test-primary
                database: test_audit
              test-record-archive:
                cluster-key: test-archive
                database: test_record
            rules:
              - pattern: test_record
                match-type: exact
                datasource-key: test-record-primary
                priority: 1000
              - pattern: test_record_batch
                match-type: exact
                datasource-key: test-record-primary
                priority: 1000
              - pattern: test_audit
                match-type: exact
                datasource-key: test-audit-primary
                priority: 1000
              - pattern: test_record_archive
                match-type: exact
                datasource-key: test-record-archive
                priority: 1000
```

配置模型如下：

| 配置 | 作用 |
|---|---|
| `clusters` | 定义 MySQL 连接地址、驱动、连接属性和凭据定位符。`port` 默认 `3306`，驱动默认 `com.mysql.cj.jdbc.Driver`。 |
| `datasources` | 将一个 `datasourceKey` 绑定到已配置 `clusterKey` 下的固定 `database`。同一 cluster 不允许重复绑定同一 database。 |
| `rules` | 将业务 `routeKey` 解析到 `datasourceKey`。一个 target 可以被多个 rule 复用。 |

`datasourceKey` 表达完整目标，不只是 MySQL 实例。例如 `test-record-primary` 与 `test-audit-primary` 虽在同一 cluster，仍是两个不同的固定 database 目标。

### 2. 提供连接凭据

凭据由应用在启动期按 `credential-ref` 解析。生产实现应接入应用已有的密钥管理机制，不能把密码写入普通 Route 配置或日志。

```java
@Configuration
public class MySqlRouteCredentialConfiguration {

    @Bean
    public MySqlRouteCredentialResolver mySqlRouteCredentialResolver(
            MySqlCredentialProvider credentialProvider) {
        return credentialProvider::resolve;
    }
}
```

`MySqlCredentialProvider` 是应用自己的密钥访问组件；其 `resolve(String credentialRef)` 应返回 `MySqlRouteCredential`。未知的 `credentialRef` 应明确失败，不能回退到任意默认账号。

### 3. 按业务路由键执行

`execute(routeKey, callback)` 先解析规则，再在对应路由作用域内执行 callback。应在 callback 内使用 `routingJdbcTemplate()` 或 `namedParameterJdbcTemplate()`。

```java
@Service
public class TestRecordService {

    private final MySqlRouteTemplate mySqlRouteTemplate;

    public TestRecordService(MySqlRouteTemplate mySqlRouteTemplate) {
        this.mySqlRouteTemplate = mySqlRouteTemplate;
    }

    public Integer countRecords() {
        return mySqlRouteTemplate.execute("test_record", () ->
                mySqlRouteTemplate.routingJdbcTemplate().queryForObject(
                        "SELECT COUNT(*) FROM test_record", Integer.class));
    }
}
```

callback 完成后，当前线程原有的 Route 上下文会被恢复。未命中启用规则的 `routeKey` 会失败，不会落到宿主默认 DataSource 或任意 Route target。

### 4. 显式选择目标

Ops 等管理侧可直接使用完整 `datasourceKey`，无需构造业务 `routeKey`。

```java
Integer count = mySqlRouteTemplate.executeOn("test-audit-primary", () ->
        mySqlRouteTemplate.routingJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM test_audit", Integer.class));
```

这仍然使用 Route callback 和路由 DataSource；`datasourceKey` 不存在时，callback 不会执行。

## 使用建议

- **业务使用 routeKey，Ops 使用 datasourceKey。** `routeKey` 是稳定的业务输入，业务代码优先使用 `execute(routeKey, ...)`；需要明确选择物理目标的管理、排障场景才使用 `executeOn(datasourceKey, ...)`。不要把表名、SQL 文本或用户输入直接当作 routeKey。
- **datasourceKey 要能表达完整目标。** 名称应同时区分 cluster 和固定 database，例如 `test-record-primary`，而不是只写 `primary`。同一 MySQL 实例下的不同 database 也是不同 target。
- **事务放在 Route callback 内。** 宿主使用自己的 `PlatformTransactionManager` 时，先进入 `execute` 或 `executeOn`，再开启事务；一个事务回调只访问一个 datasourceKey。

```java
mySqlRouteTemplate.execute("test_record", () -> transactionTemplate.execute(status ->
        mySqlRouteTemplate.routingJdbcTemplate().update(
                "UPDATE test_record SET status = ? WHERE id = ?", "ACTIVE", 1L)));
```

- **多业务键操作先做同目标预检。** 同一次逻辑操作涉及多个 routeKey 时，使用 `executeOnSameDatasource`，不要先后独立进入多个 Route callback 再假设它们处于同一数据库。
- **凭据最小权限且按 cluster 隔离。** `MySqlRouteCredentialResolver` 应返回仅满足该 cluster/database 操作范围的账号；生产凭据由既有密钥机制托管，不写入普通 YAML、日志、审计事件或异常文本。
- **规则优先清晰、范围收敛。** 优先采用 `exact` 或 `prefix`；只有确有模式需求时使用 `wildcard` 或 `regex`。重叠规则用 priority 明确决策，避免依赖声明顺序作为业务语义。
- **MyBatis 与 JDBC 显式绑定。** 只有明确绑定 `mysqlRouteRoutingDataSource` 的组件才会路由；保留宿主默认 DataSource 给不需要 Route 的组件，避免无意扩大路由范围。
- **上线前验证每个固定 target。** 对每个 datasourceKey 分别验证连接、固定 database、最小权限和关键 routeKey 命中；未命中 routeKey 不会回退，应用应在发布前发现缺失规则。

## 选择访问 API

| 诉求 | API | 使用条件 |
|---|---|---|
| 按业务路由键执行 | `execute(routeKey, callback)` | `routeKey` 必须命中启用的 rule。 |
| 显式选择已注册目标 | `executeOn(datasourceKey, callback)` | `datasourceKey` 必须已注册。 |
| 预检多个业务键是否同目标 | `executeOnSameDatasource(routeKeys, callback)` | 所有 routeKey 必须解析到同一个 datasourceKey。 |
| 使用位置参数 JDBC | `routingJdbcTemplate()` | 必须在 `execute` 或 `executeOn` 的 callback 内。 |
| 使用命名参数 JDBC | `namedParameterJdbcTemplate()` | 必须在 `execute` 或 `executeOn` 的 callback 内。 |
| 直接取得单个 target | `jdbcTemplate(datasourceKey)`、`dataSource(datasourceKey)` | 只适用于非事务中的显式目标访问。 |

```java
mySqlRouteTemplate.executeOnSameDatasource(
        Arrays.asList("test_record", "test_record_batch"),
        () -> mySqlRouteTemplate.routingJdbcTemplate().queryForObject("SELECT 1", Integer.class));
```

`executeOnSameDatasource` 只在 callback 前确认多个业务键属于同一目标；它不提供跨 datasource 协调、跨库结果合并或分布式事务。

## 配置参考

### 顶层配置

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `enable` | `false` | 是否启用 MySQL Route 自动配置。 |
| `clusters` | 空 | clusterKey 到连接配置的映射。 |
| `datasources` | 空 | datasourceKey 到固定 cluster/database 目标的映射。 |
| `rules` | 空 | 业务 routeKey 的解析规则。 |

### cluster 配置

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `host` | 无 | MySQL 主机，必填。 |
| `port` | `3306` | MySQL 端口。 |
| `credential-ref` | 无 | 应用凭据 SPI 使用的定位符，必填。 |
| `driver-class-name` | `com.mysql.cj.jdbc.Driver` | JDBC 驱动类。 |
| `connection-properties` | 空 | 追加到 JDBC URL 的连接属性。 |

### datasource 与 rule 配置

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `datasources.<key>.cluster-key` | 无 | datasource 所属 cluster，必填。 |
| `datasources.<key>.database` | 无 | 固定 database，必填。 |
| `rules[].pattern` | 无 | 匹配表达式，必填。 |
| `rules[].match-type` | `exact` | `exact`、`prefix`、`suffix`、`wildcard` 或 `regex`。 |
| `rules[].datasource-key` | 无 | rule 命中的 datasourceKey，必填。 |
| `rules[].priority` | `1000` | 数值越大越优先；数值相同时按 YAML 声明顺序匹配。 |
| `rules[].enable` | `true` | 是否参与路由解析。 |

`wildcard` 仅将 `*` 和 `?` 视为通配符，其他正则特殊字符按字面匹配；`regex` 使用 Java 正则表达式。

## JDBC 与 MyBatis 集成

模块启用后注册以下命名资源：

| Bean 名称 | 类型 | 用途 |
|---|---|---|
| `mysqlRouteRoutingDataSource` | `DataSource` | 仅在 Route scope 内确定实际 target 的路由 DataSource。 |
| `mysqlRouteJdbcTemplate` | `JdbcTemplate` | 基于 Route DataSource 的 JDBC 模板。 |
| `mysqlRouteNamedParameterJdbcTemplate` | `NamedParameterJdbcTemplate` | 基于 Route DataSource 的命名参数 JDBC 模板。 |

宿主应用如需让自己的 JDBC 或 MyBatis 组件走 Route，必须显式引用 `mysqlRouteRoutingDataSource`：

```java
@Bean
public JdbcTemplate routedJdbcTemplate(
        @Qualifier("mysqlRouteRoutingDataSource") DataSource routingDataSource) {
    return new JdbcTemplate(routingDataSource);
}
```

MyBatis 同理：由宿主创建 `SqlSessionFactory` 时显式设置该 DataSource。starter 不创建 `SqlSessionFactory` 或事务管理器，也不标记任何 Route Bean 为 `@Primary`；宿主默认 DataSource 可以继续独立使用。

应用若已定义同名的三个命名 Bean，starter 会保留应用的定义，不进行覆盖。

## 扩展与审计

| 扩展点 | 是否必须 | 作用 |
|---|---|---|
| `MySqlRouteCredentialResolver` | 是 | 按 `credential-ref` 解析启动期连接凭据。 |
| `MySqlRouteAuditPublisher` | 否 | 接收 Route 执行审计事件；默认 `NoopMySqlRouteAuditPublisher` 不保存事件。 |
| `MySqlRouteResolver` | 否 | 替换业务 routeKey 到 datasourceKey 的解析策略。 |
| `MySqlRouteDataSourceFactory` | 否 | 替换物理 DataSource 的创建、校验和关闭策略。 |
| `MySqlRoutePropertiesValidator` | 否 | 替换或增强配置校验。 |
| `MySqlRouteTemplate` | 否 | 替换 Route 执行门面。 |

每次 Route 执行成功或失败都会尝试发布脱敏 `MySqlRouteAuditEvent`；默认实现是 Noop。审计发布失败不会改变业务调用结果。

事件不包含 SQL、SQL 参数、结果、凭据、JDBC URL、原始 routeKey 或原始异常。调用方可以通过 `MySqlRouteAuditContext` 补充主体、能力、请求 ID 和资源摘要；未设置资源摘要时，Template 会对 `routeKey` 或 `datasourceKey` 计算 SHA-256 摘要。

## 事务与能力边界

> 一个 Spring 事务只能绑定一个 `datasourceKey`。事务期间尝试切换到另一个 target 会立即失败，不支持跨 datasource 事务。

- 事务中不得使用 `jdbcTemplate(datasourceKey)` 或 `dataSource(datasourceKey)` 直接取得物理 target，避免绕过 Route 事务边界。
- 不支持 SQL 解析或改写、分库分表、跨库 Join、跨库结果合并、XA / 2PC / Seata、动态 `USE database`、动态刷新、业务注解或 AOP。
- 每个 datasource 都是启动时创建的固定 cluster + 固定 database 目标；模块不提供默认 target 或默认 DataSource 回退。

## 已验证兼容性与本地验证

| Spring Boot | Java | MySQL Community Server | 结果 |
|---|---:|---|---|
| `2.2.13.RELEASE` | 8 | `5.7.44`、`8.4.2` | 通过 |
| `2.3.12.RELEASE` | 8 | `5.7.44`、`8.4.2` | 通过 |
| `2.4.5` | 8 | `5.7.44`、`8.4.2` | 通过 |
| `2.7.9` | 11 | `5.7.44`、`8.4.2` | 通过 |

每个 Spring Boot 版本均执行同一套真实双实例 E2E：同一 MySQL 实例下的固定 database 路由，以及 MySQL `5.7.44` / `8.4.2` 两个独立物理实例之间的 Route 均单独验证。
