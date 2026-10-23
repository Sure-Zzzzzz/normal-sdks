# CHANGELOG - simple-iam-client-core 1.0.4

## 发布信息

- 版本：`1.0.4`
- 类型：Feature / 契约对齐（server 1.3.8 Portal 可信应用路由投影）
- 基线版本：`1.0.3`

## 变更明细

- 新增 `IamPortalApplicationClient`，提供 `GET /iam/api/portal/applications` 的分页契约。
- 新增 `IamPortalApplicationPage` 与 `IamPortalApplicationRoute`，承载 `catalogRevision`、路由来源和受控转发策略；模型不包含凭据、Token 或 Cookie 值。
- 新增 Portal 资源段、字段和 `catalogRevision` 查询参数常量；旧 `IamApplicationClient` 三字段目录契约保持不变。
- 契约方法总数由 48 增至 49；Java 8 字节码、零运行时依赖和异常边界保持不变。

## 新增测试

- `IamClientCoreContractTest`：断言 Portal 路径、查询参数、路由字段、固定转发策略和方法总数，并验证既有分页与模型契约未改变。

## 兼容性

- `IamPortalApplicationClient` 要求 Server `1.3.8+`、SERVICE 类型 AKP，以及 `iam:portal-application:read:api` API 码和 `iam:portal-application` DATA read 范围；Portal 目录不接受浏览器 Cookie 或 HUMAN 主体。
- 既有 48 方法继续按 1.0.3 的历史兼容基线使用；旧 Server 只是不提供新增 Portal 方法，不影响既有方法。

## 升级指引

- 需要读取 Portal 机器路由目录时，将 core 与对应的 SB2/Jakarta 传输 starter 一并升级到匹配版本，并将 Server 升级到 1.3.8 或更高版本。
- 仅使用既有 48 方法的调用方可继续使用 1.0.3；升级到 1.0.4 不会改变这些方法的请求和响应形态。
