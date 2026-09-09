# Simple IAM Resource Core

将 IAM Server 已受控验证的最小响应转换为公共 Resource Server 可消费的 IAM 人员认证结果。

本模块不发起 HTTP 调用、不解析原始 Bearer Token、不读取 Servlet Session，也不保存 Token、客户端密钥或用户展示信息。

## 依赖

本模块由 `simple-iam-resource-server-starter` 作为 IAM Provider 的协议依赖引入，业务服务不应直接引入。

它依赖以下稳定协议模块：

- `simple-iam-core:1.0.0`
- `simple-application-authorization-core:1.0.1`
- `simple-resource-server-core:1.1.1`

## 使用边界

仅将 IAM Server `POST /iam/resource/tokens/verify` 成功响应（恰好包含 `sub` 与 `iam_authorization`，由 Provider Starter 的 HTTP 客户端把关）整体交给 `IamVerifiedAuthenticationClaimMapper`。映射器会严格校验 `iam_authorization` 快照内的非法、缺失与额外字段、主体类型和主体一致性；任何校验失败都不能形成已认证结果。

资源服务的 Provider Starter 应通过 `IamResourceAuthenticationResultHelper` 生成公共 `ResourceAuthenticationResult`，由 `simple-resource-server-starter` 统一执行来源选择、API 权限与 DATA 授权边界。业务代码不得从角色、OAuth scope、URL 或未经验证的 Token 内容推导 API 权限。

## 兼容性

`1.0.x` 仅兼容：

- IAM Core `1.0.x`
- Application Authorization Core `1.0.x`
- Resource Server Core `1.1.x`

本模块是 Java 8 纯 Core。它不替代 IAM Server 对 Token、OAuth Client、会话、用户状态和应用授权时效的受控验证。

## 许可证

Apache License 2.0
