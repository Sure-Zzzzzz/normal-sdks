# CHANGELOG - simple-data-permission-core 1.2.0

## 发布信息

- 版本：`1.2.0`
- 类型：Feature / DATA 权限 SPI 契约归位（向后兼容的纯新增）
- 基线版本：`1.1.0`

## 变更明细

- 新增 `core/annotation/CurrentDataAccessPlan`：DATA 计划注入的契约注解从 Spring MVC 装配件归位 core（零 Spring 依赖；`@DataPermissionOperation` 的同包先例对齐）。控制器签名从此只依赖 core，javax / jakarta 双线装配件的参数解析器均识别注入。
- 新增 `core/exception/DataPermissionAccessDeniedException`：纯 RuntimeException + `ErrorCode`（新常量 `DATA_ACCESS_DENIED = BIZ_006`），构造 `(String message)` 与 `(errorCode, message)`。MVC 线旧 FQCN 异常改为继承本类的薄壳（@ResponseStatus 语义保留在装配件侧），调用方 `catch` / `@ExceptionHandler` 面向本类型即可同时命中壳。
- 新增 `core/support/DataAccessPlanRestrictionVerifier`：授权范围校验器的实现源下沉 core（纯逻辑零 MVC）；`spring.mvc.support` 同 FQCN 保留为委托薄壳。
- 既有类零变化；模块依赖保持为空（零 Spring、零第三方）。

## 升级指引

- 纯新增、无破坏：消费方不升级也不受影响；新代码一律使用 core 三件（装配件旧 FQCN 为兼容保留，三期撤壳见 DESIGN.1.2.0-core-spi-relocation）。
