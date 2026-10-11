# CHANGELOG - simple-iam-server-core 1.3.6

## 发布信息

- 版本：`1.3.6`
- 类型：Patch / 常量补充（随 starter 1.3.7 全五件）
- 基线版本：`1.3.4`（`1.3.5` 为废号：误发 Central 后作废跳号，未形成有效列车，见台账）

## 变更明细

- **A 件（特权短路修复）**：无 core 变化（修复在 starter 侧）。
- **B 件（JIT 冲突策略）**：新增策略值常量 `USERNAME_CONFLICT_STRATEGY_AUTO_SUFFIX`（默认）/ `USERNAME_CONFLICT_STRATEGY_REJECT`（收紧项）；`ServerErrorMessage` 新增 `USERNAME_CONFLICT_STRATEGY_INVALID`（配置非法值启动拒绝文案）。
- **D 件（可信应用目录）**：新增内置权限编码 `BUILT_IN_PERMISSION_APPLICATION_API`（`iam:application:api`）与 DATA 资源标识 `DATA_RESOURCE_IAM_APPLICATION`（`iam:application`——注册表无部门归属，授权恒 all=true）。
- 仅常量与错误文案新增，其余零变化。

## 升级指引

- starter 1.3.7 依赖本版；六族 openapi 端点（messages/sessions/permissions/role-catalog/statistics/user-groups）复用既有码族，无新常量。
