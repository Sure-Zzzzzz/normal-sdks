# 1.1.0

## 版本性质

1.1.0 完成 Route-owned datasource 模型收敛：MySQL Route 只管理自己声明的 datasource，并将所有 JDBC、Named JDBC、默认事务与单 datasource MyBatis 统一接入唯一的路由 DataSource。

每个 datasource 都是固定的 JDBC URL、database、账号和密码组合。Route 只在这些已登记目标之间选择；不提供动态 `USE database`、SQL 改写、跨 datasource Join、自动跨 datasource 迁移、分库分表、高可用切换、XA、2PC、Seata 或跨 datasource 事务。

## 最终 Route-owned datasource 模型

- `io.github.surezzzzzz.sdk.mysql.route.datasources` 是唯一物理连接定义；每个 datasource 都必须完整提供 URL、账号、密码、驱动和可选 Hikari 参数。
- `primary-datasource` 为必填项，必须精确命中一个已定义 datasource。当前线程没有 Route scope 时，路由到它。
- 路由规则的 `rules[].datasource` 与 `MySqlRouteTemplate.executeOn(...)` 统一使用 datasource 名称。
- Starter 统一创建、验证、登记和关闭所有 Hikari 连接池；初始化失败时，已创建池按逆序关闭。
- `mysqlRouteRoutingDataSource` 是唯一 `@Primary` DataSource，并在 Spring Boot JDBC、事务和 MyBatis 自动配置前创建。

## 迁移步骤

1. 删除旧的宿主物理 datasource 拓扑：`spring.datasource.*`、`source: spring`、`clusters.*`、隐藏或固定的 `primary`、自动 datasource 命名，以及双来源自动配置。
2. 将每一个需要路由的物理连接迁入 `datasources.<name>`，在同一个条目中配置 `url`、`username`、`password`、`driver-class-name` 和可选 `hikari`。
3. 为每个 datasource 分配稳定、可读且中性的名称；名称同时用于 `primary-datasource`、`rules[].datasource` 和 `executeOn(...)`。
4. 设置 `primary-datasource`，并确认它精确命中 `datasources` 中的某一个名称。不要依赖 YAML 声明顺序或隐式默认值。
5. 将业务 routeKey 规则迁入 `rules`，明确 `pattern`、`match-type`、`datasource`、`priority` 和 `enable`；同优先级规则按声明顺序匹配。
6. 逐个核对宿主自行声明的 `JdbcTemplate`、事务管理器、`SqlSessionFactory`、多 datasource MyBatis 或 JPA 配置，确保它们显式使用 `mysqlRouteRoutingDataSource` 或由宿主自行管理边界。
7. 在生产前用各 datasource 的真实最小权限账号验证连接、表权限、连接数上限、事务边界和路由规则。真实密码不得进入代码、文档或提交内容。

## 配置检查清单

- [ ] `enable` 已开启，`datasources` 非空，`primary-datasource` 已配置且精确命中。
- [ ] 每个 datasource 都拥有独立、完整且固定的 JDBC 连接身份；不使用 `root` 或 `admin`。
- [ ] 每个 datasource 的 URL、database、账号和密码均在 datasource 外层定义，不在 `hikari` 内覆盖。
- [ ] 规则的 datasource 存在，`exact`、`prefix`、`suffix`、`wildcard`、`regex` 模式有效，优先级和声明顺序符合预期。
- [ ] Hikari 仅使用 Starter allowlist；`minimum-idle` 不大于 `maximum-pool-size`。
- [ ] 未配置 `jdbc-url`、`username`、`password`、`driver-class-name`、`data-source-class-name`、`data-source-jndi`、`data-source`、`keepalive-time` 或对象型 Hikari 扩展项。
- [ ] 所有 JDBC、Named JDBC、MyBatis 与事务调用的 datasource 选择可追溯；一个 Spring 事务只使用一个 datasource。
- [ ] Flyway 或 Liquibase 无 Route scope 时只处理 `primary-datasource`；其余 datasource 的迁移已由部署流程显式安排。

## Hikari 配置边界

1.1.0 固定使用 HikariCP，支持 camelCase、kebab-case、snake_case 配置名和 `data-source-properties.<driver-property>` JDBC 驱动属性。支持的标量项为：

- `connection-timeout`、`validation-timeout`、`connection-test-query`、`connection-init-sql`；
- `maximum-pool-size`、`minimum-idle`、`idle-timeout`、`max-lifetime`、`initialization-fail-timeout`；
- `auto-commit`、`read-only`、`transaction-isolation`、`catalog`、`schema`；
- `isolate-internal-queries`、`allow-pool-suspension`、`pool-name`、`leak-detection-threshold`、`register-mbeans`、`exception-override-class-name`；
- `data-source-properties.<driver-property>`。

未知、空白、不可转换、组合非法、连接身份覆盖、物理 DataSource 替换和对象/回调型 Hikari 配置都会失败。配置错误只表达 datasource 范围，不回显 JDBC URL、账号、密码或具体 Hikari 配置值。

`keepalive-time` 不属于本版本跨 Spring Boot 兼容契约；较新 Hikari 版本单独提供的能力不能直接写入 1.1.0 配置。

## 集成与验证范围

- Spring Boot 标准 `JdbcTemplate`、`NamedParameterJdbcTemplate`、默认 `DataSourceTransactionManager` 和单 datasource MyBatis 自动配置均使用唯一的 `mysqlRouteRoutingDataSource`。
- `MySqlRouteTemplate.execute(...)`、`executeOn(...)` 和 `executeOnSameDatasource(...)` 提供显式 scope；未知 datasource、未命中 routeKey、跨 datasource 事务切换和事务内直接目标访问都严格失败。
- 真实 MySQL 验证覆盖 MySQL 5.7.44 和 8.4.2：同实例不同 database 只证明逻辑多 datasource 的 database/账号隔离；跨两个真实实例才证明物理实例路由正确。
- 上述覆盖不代表复制、读写分离、故障转移、高可用、多主、分库分表或分布式事务能力。
