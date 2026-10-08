# 2.0.0

类型：功能升级（Portal 形态 + 多维度类型化规则）。

## 依赖与运行基线

| 项目 | 1.0.0 | 2.0.0 |
| --- | --- | --- |
| Core | 2.1.0 | 2.2.0 |
| 公共资源契约 | 无 | 1.1.1 |
| AKSK Provider | 强传递 2.0.1 | 宿主按模式显式提供 |
| 管理宿主 | Spring Boot 2.7.9 / Java 8 | Spring Boot 2.7.9 / Java 8 |

## 变更

### Portal 形态

- 增加 console/portal 互斥形态，默认 Console 保留历史入口；Portal 关闭内嵌页面、管理员会话与固定 token 兜底。
- Portal 使用来源中立的已验证资源上下文，区分管理查询、管理写入和快照 API 权限；机器调用不附加 PAGE 门槛。
- 将完整 DATA 服务范围带入查询、统计及主键写入 SQL；缺计划或无法表达的约束失败关闭。
- DATA 权限采用 `@DataPermissionOperation` 注解链与 `@CurrentDataAccessPlan` 参数注入（与身份底座各管理端同构）；注解拦截器在进入业务前拒绝数据权限不足的请求（403）。
- 增加不可缓存的人员页面能力接口，按目标服务判断可写性。
- 快照先执行 API 与 DATA 授权，再判断 ETag 和 304。
- 拒绝权限和参数错误保持 403/400，媒体类型错误保持 415；异常处理不影响宿主其他 Controller。
- 移除未直接使用的 AOP/OAuth2 依赖和 Thymeleaf 强传递，Console 宿主按需提供页面引擎；增加 Portal OpenAPI 契约与端点/权限一致性断言。

### 多维度类型化规则（/v2/policy/**）

- 新增类型化规则存储：七字段身份（服务/资源/计数维度/选择器/命名空间/自定义类型/对象）唯一约束，维度为 RESOURCE/IP/USER/SERVICE/CREDENTIAL/CUSTOMER/CUSTOM，选择器为 DEFAULT（各对象默认额度）与 EXACT（精确对象覆盖）；窗口表沿用 1–16 个滑动窗口与规范化秒数唯一约束。身份不适用的字段以空串入库，保证唯一索引可判重。
- 新增目录 SPI `SmartRedisLimiterDirectoryProvider`：服务协议模式（TYPED_V2/LEGACY_V1）、资源与维度声明、命名空间、自定义类型、静态对象目录与策略代次 policyEpoch。默认实现来自 `management.typed.services` 部署声明；宿主自带动态目录（如客户事实）时以自有 Bean 覆盖，无自动注册与心跳。
- 新增类型化目录与规则 API：服务列表、服务声明、对象目录检索（经 DATA 过滤返回最小 ID/名称）、规则分页查询（计数维度支持逗号分隔多值，对象筛选按字面前缀匹配并转义 LIKE 通配符）、规则 CRUD（创建返回 201 与 Location，更新/启停携带行版本 CAS，协议错配返回 409）。
- 创建规则时命名空间以目录声明为准对齐，不信任客户端传值；未声明维度、未声明自定义类型返回 400；LEGACY 服务创建类型化规则或请求类型化快照返回 409。
- 新增类型化快照 `GET /v2/policy/snapshot`：协议版本固定 "2"，携带策略代次 policyEpoch（以目录声明为准，协议切换时由宿主显式调整）与单调 revision；与三元组快照共用服务 revision 表，先完成 API 与 DATA 授权再比较 ETag/304。
- 类型化规则变更发布 Core 2.2.0 类型化管理事件：计数对象只携带命名空间受控摘要，操作人以 83 字符短摘要进入事件属性，不输出原始对象或凭据。

### 其他

- 增加默认关闭的参数化 DEBUG（API 判定、DATA 编译、变更结果、查询结果、快照生成与提交后事件），不记录 Token、Cookie、完整授权文档或原始计数对象。
- 修正大页码 offset 的整数溢出。
- 类型化服务在宿主未装配 JDBC 数据源时以 503 明确拒绝，不与持久化错误混淆。

## 数据库与升级

- 三元组策略表结构不变；新增类型化规则两表（`smart_redis_limiter_typed_rule`、`smart_redis_limiter_typed_rule_limit`），首次部署或从 1.0.0 升级均执行 `docs/mysql-schema.sql`（幂等建表）。无自动 DDL，无既有数据重写。
- Repository/Service 自定义实现需要实现 DATA 范围重载；目录 Provider 可整体替换。
- 1.0.0 宿主升级须显式提供旧 Provider；切 Portal 须完成双来源、PAGE/API/DATA 配置。无 Jakarta Management。

## 覆盖与兼容性

- Portal 随机端口/真实 MySQL 测试覆盖跨服务 CRUD、完整授权项、页面能力、HUMAN 机器调用、精确 API、304、无 Cookie 与 HTTP 错误；类型化专项测试覆盖命名空间目录对齐、身份冲突、协议错配、未声明维度拒绝、行版本 CAS、启停删 revision 递增、快照协议/代次/ETag 与对象筛选字面转义；OpenAPI 契约测试逐一断言全部端点的路径、方法与权限码。
- 测试适配器不替代真实身份提供方的签名和 PKCE 联调。
- 三元组 HTTP 契约、数据库表结构与 Core 事件契约保持不变；v1 快照与 CRUD 行为不变。
