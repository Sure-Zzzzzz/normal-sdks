# 1.0.1

## 配置模型修复

- 删除 1.0.0 的 cluster 级凭据模型、顶层 `datasources`、`credential-ref`、`MySqlRouteCredential` 与 `MySqlRouteCredentialResolver`。
- 每个 `clusters.<cluster>.datasources.<name>` 现在自闭环定义固定 `database`、`username` 与 `password`，支持同一 MySQL 实例内每个固定 database 使用独立账号和最小权限。
- `datasourceKey` 改为自动生成的 `cluster.datasource`，统一用于规则、Registry、`executeOn()`、`jdbcTemplate()` 与 `dataSource()`。
- 这是破坏性配置迁移；Template API、显式路由模型和单事务单 target 约束不变。

## 连接与安全

- 启动期创建并验证所有 target；任一 target 的连接、账号、密码、库名或权限异常都会使启动失败，不会发布半初始化 Registry。
- 密码仅由 Spring 常规属性机制提供，可通过环境变量或 JVM `-D` 覆盖 YAML 占位符。
- 不支持、不保留 KMS/Vault、凭据 SPI、旧平铺配置 fallback 或动态凭据刷新。
- 公开 target 元数据、starter 审计、starter 日志和 starter 主动构造的异常消息不输出连接账号、密码、JDBC URL 或连接属性；自定义物理工厂和 JDBC 驱动异常由宿主按自身日志规范处理。

## 端到端验证

- MySQL 5.7.44 与 8.4.2 两个独立实例中各配置 ops/audit 两个固定 target，合计四个独立账号。
- 每个 target 均验证固定 database、实例身份、版本、连接账号、路由执行、显式 target 执行、命名参数 JDBC 和完整成功 CRUD。
- CRUD 权限收敛到各自 database 的专属测试表；同实例跨 database 的访问仍验证拒绝。

## 兼容性与发布验证

- 已在 Spring Boot `2.2.13.RELEASE`、`2.3.12.RELEASE`、`2.4.5` 与 `2.7.9` 上完成完整模块测试。
- 每个版本均执行真实 MySQL `5.7.44` / `8.4.2` 双实例四账号 E2E，不通过系统属性跳过 E2E；覆盖固定 target 连接身份、路由、显式 target、命名参数 JDBC、完整 CRUD、跨 database 权限拒绝和单事务跨 target 拦截。

## MyBatis 接入

- `mysqlRouteRoutingDataSource` 是命名、非 `@Primary` 的 `DataSource` Bean。
- `mysqlRouteRoutingDataSource`、`mysqlRouteJdbcTemplate` 和 `mysqlRouteNamedParameterJdbcTemplate` 是启用 Route 时的保留基础设施名称；应用定义同名 Bean 会启动失败，不能通过替换资源绕过严格路由和单事务单 target 约束。
- 单一 DataSource 候选应用可由 MyBatis 自动配置采用；与宿主 DataSource 并存时，应用必须显式用该 Bean 配置自己的路由 `SqlSessionFactory` 与事务管理器。
- starter 不添加 MyBatis 生产依赖，也不接管宿主默认 DataSource、Mapper 扫描或 `SqlSessionFactory`。
