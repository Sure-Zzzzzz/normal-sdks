# 2.2.0

## 能力

- **快照 304 查询快路径**：三个快照端点（`/v1/policy/snapshot`、`/v2/policy/snapshot` 及 portal 同路径）
  改为先以 revision 单行拼装 ETag 与 `If-None-Match` 比对，命中直接返回 304，不再执行全量规则查询；
  未命中才读取全量快照。ETag 与全量快照同源同算法（`serviceCode + revision`），协议行为不变，
  客户端零改动；比对与全量读取之间的 revision 变更窗口属良性竞态（下次拉取即取到新数据）。
- 类型化管理服务接口新增 `currentEtag(serviceCode)`；快照服务接口新增
  `getCurrentEtag(serviceCode)` 与 `getCurrentEtag(serviceCode, scope)`（portal 形态拒绝与
  DATA 范围校验语义与对应快照方法一致）。

## 依赖

- **数据源切换 mysql-route 接管**：模块随附 `simple-mysql-route-starter`（1.1.1，平台部署契约，
  同 KMS/feedback 形态）；宿主数据源配置从 `spring.datasource` 标准段迁移至
  `io.github.surezzzzzz.sdk.mysql.route.datasources` 路由形态（enable + primary-datasource +
  datasources 清单），路由版 JdbcTemplate 注册为标准 Bean，模块内 Repository 无需代码适配。
- `simple-data-permission-core` 1.1.0 → 1.2.0（`@CurrentDataAccessPlan` 注解与
  `DataPermissionAccessDeniedException` 异常自 1.2.0 起归位 core 契约层）。
- 移除 `simple-data-permission-spring-mvc-starter` 的 compileOnly 依赖（注解与异常归位后
  主代码仅依赖 core）；测试类路径升级至 1.1.0（配套 core 1.2.0 的注解位置），
  测试装配真实 MVC 参数解析链。

## 文档

- 领域文档三篇（`docs/领域文档/`）：精确策略域、类型化规则域、目录与快照域——表关系、
  身份与约束、revision/ETag 语义、304 快路径与目录聚散形态。
- `docs/mysql-schema.sql` 更名 `docs/schema.sql` 并对齐族式格式：版本化头部块（依赖与
  初始化警告）、`SET NAMES/FOREIGN_KEY_CHECKS` 包裹、DROP 集中表头。
- `docs/openapi.yaml` 版本随模块（2.2.0），description 补鉴权与接入配置点表格及
  快照条件请求说明。

## 兼容

- 协议行为不变：304/200 判定、ETag 格式、`Cache-Control` 与 DATA/权限校验语义原样。
- 接口新增方法：自建 `SmartRedisLimiterTypedPolicyManagementService` 或
  `SmartRedisLimiterPolicySnapshotService` 实现的宿主升级需补实现新增方法
  （均为只查 revision 单行的轻量实现）。
- 初始化脚本文件名变更为 `docs/schema.sql`（引用旧名的脚本需同步）。
