# CHANGELOG - simple-aksk-server-starter 3.2.2

## 发布信息

- 版本：`3.2.2`
- 类型：Patch / 历史 Token 关联缺失的管理 DATA 权限过滤修复
- 基线版本：`3.2.1`

## 版本定位

删除 Client 会先撤销其活跃 Token，再删除 Client 注册记录；`oauth2_authorization` 中的历史授权记录保留。历史记录在管理 Token 列表或统计时无法再回查 Client 类型、Client ID 与所属人。3.2.1 的 DATA 权限维度转换对缺失的 `clientType` 发生自动拆箱，导致合法的历史记录使管理接口返回 500。

## 主要变更

- 管理 Token 的 DATA 权限过滤将缺失的 Client 类型作为缺失维度处理，不再抛出异常。
- 无约束的授权计划仍可统计历史撤销 Token；带 Client 类型、Client ID 或所属人等约束的计划不会匹配该历史记录，保持失败关闭。
- `GET /api/token`、`GET /api/token/statistics` 及同一过滤辅助链路在该历史状态下保持正常响应。

## 依赖与数据库

- 无 Core 版本变更、无新增或升级依赖。
- 无 SQL、无表结构变化、无数据回填或清理要求。
- 无 HTTP 路径、请求参数、响应体、响应头或配置键变化。

## 回归验证

- 覆盖正式删除 Client 路径：先撤销 Token、再删除 Client，随后读取 Token 列表与统计。
- 覆盖无约束计划的历史统计，以及受 Client 类型约束计划对历史记录的失败关闭过滤。
- 发布前执行 Server Starter 全量测试并核对 JUnit XML；部署正式版本后，复跑使用 `/api/token/statistics` 的 Feign 与 RestTemplate 客户端真实链路。

## 向后兼容性

- 纯修复版本。既有 Client 删除、Token 撤销、Token 清理、管理权限和 IAM 协作行为不变。
- 已存在的历史授权记录无需迁移；升级后即可避免管理 Token 读取该类记录时返回 500。
