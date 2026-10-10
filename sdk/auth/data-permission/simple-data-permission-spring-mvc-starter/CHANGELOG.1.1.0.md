# CHANGELOG - simple-data-permission-spring-mvc-starter 1.1.0

## 发布信息

- 版本：`1.1.0`
- 类型：Feature / DATA 权限 SPI 契约归位兼容层（零破坏）
- 基线版本：`1.0.1`
- 关联制品：`simple-data-permission-core:1.2.0`（契约本体）

## 变更明细

- 参数解析器双认：`@CurrentDataAccessPlan` 的 core 契约形态（`core.annotation`）与 legacy 本包形态等价注入——新签名只用 core，存量签名零感知。
- `DataPermissionAccessDeniedException` 改为继承 core 契约异常的薄壳（保留 `@ResponseStatus(FORBIDDEN)`）；机器（拦截器/解析器/门面/校验器）继续抛本壳，存量 `catch(壳)` 与 `@ExceptionHandler(壳)` 照常命中；`@ExceptionHandler(core 版)` 同样命中壳。
- `DataAccessPlanRestrictionVerifier` 改为委托 core 实现的同 FQCN 薄壳（core 拒绝异常在壳边界回抛本线异常，FQCN 在飞语义不变）。
- 无人显式处理时的 403 兜底保持既有 `@ResponseStatus` 路径不变（一期不注册兜底 advice——advice 与宿主同类型 handler 平级排序存在注册顺序竞态，注册时机会改变未升级宿主的错误体形状；三期撤壳时再带显式排序策略引入）。
- 依赖：`simple-data-permission-core:1.1.0 → 1.2.0`。

## 兼容性与测试

- Spring Boot `2.2.13.RELEASE` / `2.3.12.RELEASE` / `2.4.5` / `2.7.9`（javax 线矩阵不变）；本版新增 3 用例：core/legacy 注解注入等价（双认）、薄壳 `instanceof` core 契约并透传 `BIZ_006`、无人处理时 @ResponseStatus 兜底 403 与 1.0.1 一致——模块测试 9 → 12 全绿。
- 消费方零变化验证：IAM / AKSK / KMS / License / Limiter 等既有消费方不升级本版不受任何影响（旧 FQCN 全保留、机器抛出的异常类型不变）。

## 升级指引

- 零破坏：不升级消费方不受影响。新代码注解/异常/校验器一律 import core 包（`core.annotation.CurrentDataAccessPlan` / `core.exception.DataPermissionAccessDeniedException` / `core.support.DataAccessPlanRestrictionVerifier`）；存量 `spring.mvc.*` import 无需改动（双认/薄壳等价）。三期（下个 major）撤 legacy 壳，届时发迁移映射。
