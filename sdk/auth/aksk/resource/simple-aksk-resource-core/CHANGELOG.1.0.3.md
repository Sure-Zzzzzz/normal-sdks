# Changelog - v1.0.3

## 发布日期

2026-05-02

## 版本类型

功能增强

## 变更概述

将 resource-server-starter 和 security-context-starter 中重复定义的常量和工具类统一迁移到 core，由 core 作为唯一来源。

### 新增常量（SimpleAkskResourceConstant）

| 常量 | 值 | 说明 |
|------|------|------|
| `FIELD_TRACE_ID` | `"traceId"` | 链路追踪 ID 字段名 |
| `HEADER_USER_AGENT` | `"User-Agent"` | HTTP User-Agent 请求头名 |
| `JWT_CLAIM_SUB` | `"sub"` | JWT subject claim 名 |
| `INTROSPECT_CLAIM_ACTIVE` | `"active"` | Introspect 响应 active 字段名 |
| `AUTHORITY_SCOPE_PREFIX` | `"SCOPE_"` | Spring Security 权限前缀 |
| `ACCESS_SOURCE_JWT` | `"jwt"` | AkskAccessEvent source：JWT 验证 |
| `ACCESS_SOURCE_INTROSPECT` | `"introspect"` | AkskAccessEvent source：Introspect 验证 |
| `ACCESS_SOURCE_HEADER` | `"header"` | AkskAccessEvent source：Header 解析（网关场景） |

### 新增类

| 类 | 包 | 说明 |
|------|------|------|
| `HeaderNameConverter` | `support` | HTTP Header 名称转换工具（kebab-case → camelCase），从 security-context-starter 迁移 |
| `AkskContextHelper` | `support` | 上下文公共工具方法：`buildAccessEvent()`、`getCurrentRequest()`、`claimValueToString()` |

## 向后兼容性

完全向后兼容，仅新增内容。

## 贡献者

- @surezzzzzz
