# CHANGELOG - simple-data-permission-spring-mvc-jakarta-starter 1.1.0

## 发布信息

- 版本：`1.1.0`
- 类型：Feature / DATA 权限 SPI 契约归位兼容层（零破坏）
- 基线版本：`1.0.0`
- 关联制品：`simple-data-permission-core:1.2.0`（契约本体）

## 变更明细

- 参数解析器双认：`@CurrentDataAccessPlan` 的 core 契约形态（`core.annotation`）与 legacy 本包形态等价注入——新签名只用 core，存量签名零感知。
- `DataPermissionAccessDeniedException` 改为继承 core 契约异常的薄壳（保留 `@ResponseStatus(FORBIDDEN)`）；机器（拦截器/解析器/门面/校验器）继续抛本壳，存量 `catch(壳)` 与 `@ExceptionHandler(壳)` 照常命中；`@ExceptionHandler(core 版)` 同样命中壳。
- `DataAccessPlanRestrictionVerifier` 改为委托 core 实现的同 FQCN 薄壳（core 拒绝异常在壳边界回抛本线异常，FQCN 在飞语义不变）。
- 无人显式处理时的 403 兜底保持既有 `@ResponseStatus` 路径不变（一期不注册兜底 advice，同 SB2 线注记）。
- 依赖：`simple-data-permission-core:1.1.0 → 1.2.0`。

## 兼容性与测试

- Spring Boot 3.4.2 / JDK 17（jakarta 线基线不变）；与 SB2 线 1.1.0 同构的 3 个新用例（双认等价/壳继承/403 兜底）随既有测试全绿。
- 消费方零变化验证：jakarta 线既有消费方不升级不受影响（旧 FQCN 全保留、异常类型不变）。

## 升级指引

- 零破坏：不升级消费方不受影响。新代码注解/异常/校验器一律 import core 包（与 SB2 线同一组 core 三件类型）；存量 `spring.mvc.*` import 无需改动。三期（下个 major）撤 legacy 壳。
