# CHANGELOG - simple-aksk-resource-server-starter 1.0.5

## 发布日期

2026-05-02

## 版本类型

重构

## 变更概述

升级依赖 `simple-aksk-resource-core` 至 1.0.3，将重复定义的常量和工具方法迁移到 core，消除模块间重复代码。

### 常量迁移

以下常量从 `SimpleAkskResourceServerConstant` 移除，统一引用 `SimpleAkskResourceConstant`（core）：

| 常量 | 说明 |
|------|------|
| `ACCESS_SOURCE_JWT` | AkskAccessEvent source 标识 |
| `ACCESS_SOURCE_INTROSPECT` | AkskAccessEvent source 标识 |
| `HEADER_USER_AGENT` | HTTP User-Agent 请求头名 |
| `FIELD_TRACE_ID` | 链路追踪 ID 字段名 |
| `JWT_CLAIM_SUB` | JWT subject claim 名 |
| `AUTHORITY_SCOPE_PREFIX` | Spring Security 权限前缀 |
| `INTROSPECT_CLAIM_ACTIVE` | Introspect 响应 active 字段名 |

### 工具方法迁移

`ConverterHelper` 中的通用方法（`buildAccessEvent`、`getCurrentRequest`、`claimValueToString`）改为委托给 core 的 `AkskContextHelper`，`ConverterHelper` 保留 resource-server-starter 专属的 `extractAuthorities`。

## 向后兼容性

完全向后兼容，接入方无感知。

## 贡献者

- @surezzzzzz
