# CHANGELOG - simple-aksk-core 3.0.0

## 版本类型

Major Release - AKSK Server 3.0 共享契约收口

## 变更概述

升级 `simple-aksk-core` 至 3.0.0，为 AKSK Server 的应用授权自闭环和资源服务侧认证源路由提供共享模型与基础协议契约。

## 新增

- 新增 `AkskRouteKeyHelper`，统一创建和解析 `aksk/<key-id>` JOSE 路由键。
- 新增 `AkskConstant` 中的 AKSK 认证源、路由键前缀、允许字符和长度约束。
- 新增 `JwtClaimConstant.APPLICATION_AUTHORIZATION`，用于承载服务主体的应用授权快照。
- `TokenInfo` 新增 `DataSource` 枚举，区分 `MYSQL`、`REDIS` 与 `BOTH` 数据来源。

## 移除

- 移除 `HeaderConstant`：Header 安全上下文路线不再属于 Core 3.0 契约。
- 移除 `SecurityContextHelper`：security_context 的接收与限长校验由 Server 端负责，Core 不再提供 JSON 序列化工具。

## 兼容性说明

- `simple-aksk-server-core:3.0.0` 与 `simple-aksk-server-starter:3.0.0` 使用本版本作为共享依赖。
- 2.x 的最终发布版本保持为 `2.0.0`，历史接入请参考 [README.2.x.md](README.2.x.md)。
