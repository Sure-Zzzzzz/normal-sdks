# CHANGELOG - simple-iam-server-core 1.3.5

## 发布信息

- 版本：`1.3.5`
- 类型：Patch / 常量补充（随 starter 1.3.7）
- 基线版本：`1.3.4`

## 变更明细

- 新增 JIT 用户名冲突策略常量：`USERNAME_CONFLICT_STRATEGY_AUTO_SUFFIX`（默认）与 `USERNAME_CONFLICT_STRATEGY_REJECT`（收紧项）。
- 新增内置权限编码 `BUILT_IN_PERMISSION_APPLICATION_API`（`iam:application:api`，可信应用目录查询，端点随 starter 1.3.7）。
- 新增 DATA 资源标识 `DATA_RESOURCE_IAM_APPLICATION`（`iam:application`；注册表无部门归属，授权恒为 all=true）。
- 仅常量新增，其余零变化。
