# simple-data-permission-spring-mvc-starter 1.0.1 变更记录

## 依赖升级

- `simple-resource-server-core` `1.0.0` → `1.1.1`（`api`）：`ResourceAccessEvent` 事件模型迁入 core（纯模型对象，新包名 `io.github.surezzzzzz.sdk.auth.resource.core.event`）；core 1.1.1 同时传递 `simple-application-authorization-core` `1.0.1` 的时钟容差修复（授权时效判定引入默认 2 秒容差，仅放宽 `issuedAt` 下界）。
- 测试组装依赖 `simple-resource-server-starter` `1.0.1` → `1.1.0`（`testImplementation`）：配合公共 Starter 的事件模型迁移，仅影响本模块测试，不进入使用方 classpath。

## 兼容性

- 本模块源码不引用 `ResourceAccessEvent`（DATA 评估只消费 `VerifiedResourceContext`），对使用方完全兼容：自动装配、`DataPermissionFacade`、`@CurrentDataAccessPlan` 与 MVC 集成行为均不变。

## 测试

- Spring Boot 2.7.9 / 2.4.5 / 2.3.12.RELEASE / 2.2.13.RELEASE 四版本矩阵（完整模块测试，每组 9 项，`skipped=0`、`failures=0`、`errors=0`）。
- 既有 9 项场景全部保留：MVC 自动配置、Resource Server API 权限优先于 DATA 评估、IAM 人员主体与 AKSK 服务主体、缺失授权文档失败关闭、越权目标拒绝、完整 DNF grant 校验。
