# CHANGELOG - simple-aksk-server-core 3.0.0

## 版本类型

Major Release - AKSK Server 3.0 共享服务端契约收口

## 变更概述

升级 `simple-aksk-server-core` 至 3.0.0，为 AKSK Server 的应用授权自闭环、管理 API 权限校验和数据权限投影提供统一服务端契约。

## 新增

- 新增管理 API 的应用编码、Client、Token、应用授权资源与 CRUD/撤销动作常量。
- 新增管理 API 权限常量，覆盖 Client、Token、应用授权的访问范围。
- 新增 Client、Token、应用编码等数据权限维度常量。
- 新增 `JWT_CLAIM_APPLICATION_AUTHORIZATION`，并复用 `simple-aksk-core` 的 `JwtClaimConstant.APPLICATION_AUTHORIZATION` 作为唯一 Claim 来源。

## 移除

- 移除 `SimpleAkskServerProperties.IntrospectConfig` 及 `introspect.require-authentication` 配置。
- AKSK Server 3.0 的 introspect 端点不再提供匿名查询模式，认证策略由 Starter 的安全链统一执行。

## 构建与依赖

- `simple-aksk-core` 升级为正式 Maven 坐标 `io.github.sure-zzzzzz:simple-aksk-core:3.0.0`。
- 不新增第三方运行时依赖。

## 兼容性说明

- `introspect.require-authentication` 已移除；依赖匿名 introspect 的旧配置不可继续使用。
- `simple-aksk-server-starter:3.0.0` 使用本版本作为共享依赖。
- 2.x 最终发布版本保持为 2.0.3，历史接入请参考 [README.2.x.md](README.2.x.md)。
