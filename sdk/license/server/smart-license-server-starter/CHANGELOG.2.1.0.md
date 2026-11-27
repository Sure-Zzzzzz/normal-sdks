# smart-license-server-starter 2.1.0

## 发布信息

- 类型：Feature / 兼容增强
- 基线版本：`2.0.0`

## 变更

- `license-grant` DATA 支持 `customerId IN`，也支持与 `grantId IN` 同授权项内 AND 求交；不同授权项仍按 OR 独立合并。
- `customerId` 由 License Server 按当前授权事实解析为有效 grantId；未知维度、空结果、空值或解析器缺失一律失败关闭，不放宽为全量。
- 角色投影创建/AUTO 前校验 `dataGrantTemplate` 的资源、动作和约束维度必须落在目标应用 manifest 申报内；非法模板返回 422，不再依赖 IAM 后置 400。
- manifest 申报自身缺动作或维度集合时同样按非法模板失败关闭。
- `LicenseGrantAccessScopeResolver` 注入为可选增强；旧隔离测试未注入时继续按 `grantId` 投影。
- IAM 确定性 400/403/404 在宿主适配映射为 `REJECTED` 后，角色投影计划保留 `PENDING_EXECUTION`，不误标 `UNCERTAIN`。

## 兼容性

- 生产 Spring 装配自动注入 resolver 并启用 customerId；旧三参控制器构造器和旧 `grantId IN` 契约保持可用。
- 依赖 License Core `2.1.0` 获取 `Manifest.dataResources` 契约。

## 验证

- Java 8 全量测试 418 项；0 失败、0 错误、0 跳过。覆盖 Grant 维度投影、DATA 模板前置校验、确定性拒绝和持久化围栏。
