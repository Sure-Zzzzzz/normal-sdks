# Simple MySQL Route Starter

面向 Spring Boot 的显式 MySQL 路由组件。业务通过 `routeKey`，或 Ops 等管理侧通过完整 `datasourceKey`，选择已经配置的固定 MySQL target，并在受控 callback 中执行 JDBC 或 MyBatis 操作。

接入路径：引入依赖 → 配置固定 target → 配置路由规则 → 在 `MySqlRouteTemplate` scope 内执行。

- 一个 target 是固定 `cluster address + database + username + password` 的完整连接定义；不会在运行期切换 database。
- `datasourceKey` 由 `clusterKey.datasourceName` 自动生成，例如 `mysql57.ops`。
- 注册所有 target 时都会创建并验证连接；任一个地址、库名、账号、密码或权限错误都会使应用启动失败。
- 提供命名、非 `@Primary` 的路由 `DataSource`、`JdbcTemplate` 和 `NamedParameterJdbcTemplate`；不替换宿主默认数据源，也不创建事务管理器。
- 不存在默认 target 回退；未知 `routeKey` 或 `datasourceKey` 直接失败。

## 依赖与启用

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-mysql-route-starter:1.0.1'
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    runtimeOnly 'mysql:mysql-connector-java'
}
```

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        mysql:
          route:
            enable: true
```

## 兼容性与验证

1.0.1 已完成以下 Spring Boot 精确版本的完整模块测试：`2.2.13.RELEASE`、`2.3.12.RELEASE`、`2.4.5` 与 `2.7.9`。每个版本均执行全部单元测试和真实 MySQL E2E，不通过系统属性跳过 E2E。

真实 E2E 同时连接 MySQL `5.7.44` 与 `8.4.2` 两个独立实例；四个固定 target 使用各自账号，覆盖连接身份、路由与显式 target、命名参数 JDBC、完整 CRUD、同实例跨 database 越权拒绝，以及单事务切换 target 拦截。

## 配置固定 target 与路由规则

每个 `datasources` 项在所属 cluster 下定义自身的固定 database、username 与 password。示例名称为中性示例，不代表真实业务库表或环境。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        mysql:
          route:
            enable: true
            clusters:
              mysql57:
                host: mysql57.example.internal
                port: 3306
                driver-class-name: com.mysql.cj.jdbc.Driver
                connection-properties:
                  useUnicode: true
                  characterEncoding: UTF-8
                  useSSL: true
                datasources:
                  ops:
                    database: test_ops
                    username: mysql57_ops_route
                    password: ${MYSQL57_OPS_PASSWORD}
                  audit:
                    database: test_audit
                    username: mysql57_audit_route
                    password: ${MYSQL57_AUDIT_PASSWORD}
              mysql84:
                host: mysql84.example.internal
                datasources:
                  ops:
                    database: test_ops
                    username: mysql84_ops_route
                    password: ${MYSQL84_OPS_PASSWORD}
                  audit:
                    database: test_audit
                    username: mysql84_audit_route
                    password: ${MYSQL84_AUDIT_PASSWORD}
            rules:
              - pattern: test_order
                match-type: exact
                datasource-key: mysql57.ops
                priority: 1000
              - pattern: test_user
                match-type: exact
                datasource-key: mysql57.audit
                priority: 1000
              - pattern: test_wildcard
                match-type: exact
                datasource-key: mysql84.ops
                priority: 1000
              - pattern: test_extra_field
                match-type: exact
                datasource-key: mysql84.audit
                priority: 1000
```

| 配置 | 作用 |
|---|---|
| `clusters` | 定义 MySQL 实例共用的 host、port、driver 与连接属性。`port` 默认 `3306`，驱动默认 `com.mysql.cj.jdbc.Driver`。 |
| `clusters.<cluster>.datasources` | 定义该实例内的完整固定连接 target。每项必须有 `database`、`username`、`password`。同一 cluster 内不能重复绑定同一 database。 |
| `rules` | 将业务 `routeKey` 解析到自动生成的 `datasourceKey`。一个 target 可被多个规则复用。 |

`datasourceKey` 不在 YAML 中重复配置，而是稳定生成：`clusterKey.datasourceName`。例如 `mysql57.ops` 表示 mysql57 实例下名为 ops、固定连接 test_ops 的 target。cluster 名和 datasource 名应使用稳定技术标识，不能直接使用用户输入。

## 密码配置与最小权限

已提交的 YAML 仅保留 `${MYSQL57_OPS_PASSWORD}` 一类占位符，不提交真实密码。生产环境可通过标准 Spring 属性覆盖提供密码：

```bash
MYSQL57_OPS_PASSWORD='secret' java -jar app.jar
```

```bash
java -DMYSQL57_OPS_PASSWORD=secret -jar app.jar
```

每个 target 使用自己的数据库账号。账号只授予目标 database、目标表和实际操作需要的权限；不要为同实例内其他 database 授权。模块不提供、不需要 `credential-ref`、凭据解析器、KMS/Vault SPI 或默认账号。

starter 自身仅在默认物理 DataSource 工厂创建连接时读取账号和密码；替换 `MySqlRouteDataSourceFactory` 的应用代码应自行遵守相同边界。公开 target 元数据、starter 审计事件、starter 日志和 starter 主动构造的异常消息不会输出 host、JDBC URL、username、password 或连接属性；业务执行期由 JDBC 驱动抛出的异常应由宿主按自身日志规范处理。

## 按业务路由键执行

`execute(routeKey, callback)` 先解析规则，再在对应路由作用域内执行 callback。应在 callback 内使用 `routingJdbcTemplate()` 或 `namedParameterJdbcTemplate()`。

```java
@Service
public class TestOrderService {

    private final MySqlRouteTemplate mySqlRouteTemplate;

    public TestOrderService(MySqlRouteTemplate mySqlRouteTemplate) {
        this.mySqlRouteTemplate = mySqlRouteTemplate;
    }

    public Integer countOrders() {
        return mySqlRouteTemplate.execute("test_order", () ->
                mySqlRouteTemplate.routingJdbcTemplate().queryForObject(
                        "SELECT COUNT(*) FROM test_order", Integer.class));
    }
}
```

callback 完成后，线程原有 Route 上下文会被恢复。未命中启用规则的 `routeKey` 会失败，不会落到宿主默认 DataSource 或任意 Route target。

## 显式选择 target

Ops 等管理侧可直接使用完整 `datasourceKey`，无需构造业务 `routeKey`。

```java
Integer count = mySqlRouteTemplate.executeOn("mysql57.audit", () ->
        mySqlRouteTemplate.routingJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM test_audit", Integer.class));
```

`datasourceKey` 不存在时 callback 不会执行。`executeOnSameDatasource` 仅用于预检多个 routeKey 是否解析到同一 target；它不提供跨 datasource 协调、跨库结果合并或分布式事务。

## JDBC、MyBatis 与事务

模块注册以下命名资源：

| Bean 名称 | 类型 | 用途 |
|---|---|---|
| `mysqlRouteRoutingDataSource` | `DataSource` | 仅在 Route scope 内确定实际 target 的严格路由 DataSource。 |
| `mysqlRouteJdbcTemplate` | `JdbcTemplate` | 基于严格路由 DataSource 的 JDBC 模板。 |
| `mysqlRouteNamedParameterJdbcTemplate` | `NamedParameterJdbcTemplate` | 基于严格路由 DataSource 的命名参数 JDBC 模板。 |

这三个名称是启用 MySQL Route 时由 starter 独占的保留基础设施名称，不能由应用定义同名 Bean；发生冲突时应用会启动失败。它们必须保持为同一条严格路由链，才能保证 starter 默认 `MySqlRouteTemplate` 的作用域校验和单个 Spring 事务只能绑定一个 target。

路由 `DataSource` 是普通命名 Bean，且刻意不标记 `@Primary`：

- 应用只有这个 `DataSource` 候选时，MyBatis Spring Boot 自动配置可以将其作为唯一候选。
- 应用已有默认 `DataSource` 时，Route Bean 与它并存；MyBatis 不应依赖隐式候选选择，而应由应用显式绑定自己的路由 `SqlSessionFactory`。
- starter 不引入 MyBatis 生产依赖、不创建 `SqlSessionFactory`、不扫描 Mapper，也不改变宿主 JDBC、事务或 MyBatis 的默认数据源。

```java
import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@Bean
public SqlSessionFactory routedSqlSessionFactory(
        @Qualifier("mysqlRouteRoutingDataSource") DataSource routingDataSource) throws Exception {
    SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
    factory.setDataSource(routingDataSource);
    return factory.getObject();
}

@Bean
public DataSourceTransactionManager routedTransactionManager(
        @Qualifier("mysqlRouteRoutingDataSource") DataSource routingDataSource) {
    return new DataSourceTransactionManager(routingDataSource);
}
```

调用路由 Mapper 前必须进入 `execute(routeKey, ...)` 或 `executeOn(datasourceKey, ...)`。多数据源宿主使用 `@Transactional` 时，必须显式指定 `routedTransactionManager`，并确保首次 JDBC 或 Mapper 调用发生在 Route callback 内；不要在进入 callback 前访问路由 Mapper。一个 Spring 事务只能绑定一个 `datasourceKey`，尝试切换 target 会立即失败。不支持跨 datasource 事务、XA / 2PC / Seata、动态 `USE database`、SQL 改写、分库分表、跨库 Join、动态刷新、业务注解或 AOP。

## 配置参考

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `enable` | `false` | 是否启用 MySQL Route 自动配置。 |
| `clusters` | 空 | clusterKey 到实例与嵌套 target 配置的映射。 |
| `clusters.<cluster>.host` | 无 | MySQL 主机，必填。 |
| `clusters.<cluster>.port` | `3306` | MySQL 端口。 |
| `clusters.<cluster>.driver-class-name` | `com.mysql.cj.jdbc.Driver` | JDBC 驱动类。 |
| `clusters.<cluster>.connection-properties` | 空 | 追加到 JDBC URL 的连接属性。 |
| `clusters.<cluster>.datasources.<name>.database` | 无 | 固定 database，必填。 |
| `clusters.<cluster>.datasources.<name>.username` | 无 | 此固定 target 的连接账号，必填。 |
| `clusters.<cluster>.datasources.<name>.password` | 无 | 此固定 target 的连接密码，必填。 |
| `rules[].pattern` | 无 | 匹配表达式，必填。 |
| `rules[].match-type` | `exact` | `exact`、`prefix`、`suffix`、`wildcard` 或 `regex`。 |
| `rules[].datasource-key` | 无 | 规则命中的生成 target key，必填。 |
| `rules[].priority` | `1000` | 数值越大越优先；相同时按 YAML 声明顺序匹配。 |
| `rules[].enable` | `true` | 是否参与路由解析。 |

`wildcard` 仅将 `*` 和 `?` 视为通配符，其他正则特殊字符按字面匹配；`regex` 使用 Java 正则表达式。

## 1.0.0 升级到 1.0.1

1. 删除 cluster 上的 `credential-ref`，同时删除顶层 `datasources`。
2. 将每个原有固定 database 移动到所属 `clusters.<cluster>.datasources.<name>` 下。
3. 在每个 datasource 中配置该库自己的 `database`、`username` 与 `password`。
4. 将 rule、`executeOn()`、`jdbcTemplate()`、`dataSource()` 使用的 key 改为 `cluster.datasource`，例如 `mysql57.ops`。
5. 删除 `MySqlRouteCredential`、`MySqlRouteCredentialResolver` 及所有相关 Bean 配置。

这是配置模型的破坏性修复：公开 Template API 与事务边界不变，但不兼容 1.0.0 的平铺 target 与凭据 SPI 配置。

## 扩展与审计

| 扩展点 | 是否必须 | 作用 |
|---|---|---|
| `MySqlRouteAuditPublisher` | 否 | 接收 Route 执行审计事件；默认 `NoopMySqlRouteAuditPublisher` 不保存事件。 |
| `MySqlRouteResolver` | 否 | 替换业务 routeKey 到 datasourceKey 的解析策略。 |
| `MySqlRouteDataSourceFactory` | 否 | 替换物理 DataSource 的创建、校验和关闭策略。 |
| `MySqlRoutePropertiesValidator` | 否 | 替换或增强配置校验。 |
| `MySqlRouteTemplate` | 否 | 替换 Route 执行门面。 |

每次 Route 执行成功或失败都会尝试发布脱敏 `MySqlRouteAuditEvent`；默认实现是 Noop。审计发布失败不会改变业务调用结果。事件不包含 SQL、SQL 参数、结果、凭据、JDBC URL、原始 routeKey 或原始异常。调用方通过 `MySqlRouteAuditContext` 提供 `resourceDigest` 时，只能传入小写十六进制 SHA-256 摘要；格式无效时自动使用 routeKey 的 SHA-256 摘要。
