# CHANGELOG - simple-aksk-resource-core 2.0.0

## 发布日期

2026-05-21

## 版本类型

Breaking Change - 与 server-starter 2.0.0 同步升级

## 变更概述

- **版本对齐**：随 simple-aksk-server-starter 2.0.0 同步升级，版本号从 1.x 升级到 2.0.0
- **兼容 JWE Token**：确认与 server-starter 2.0.0 签发的 JWE Token 完全兼容

## 变更详情

### 依赖升级

| 模块 | 旧版本 | 新版本 |
|------|-------|-------|
| simple-aksk-core | - | 随 transitive 依赖引入 |

> 本模块为纯共享库，不涉及 token 格式解析，代码层面无任何改动。

## 兼容说明

JWE Token 只是 token 的载体格式变了（从 JWS base64 明文变成 JWE 密文），claims 的字段名和结构（`client_id`、`client_type`、`user_id`、`username`、`security_context`、`scope`）完全不变。

`SimpleAkskResourceConstant` 中的 FIELD_TO_JWT_CLAIM 映射仍然有效，`SimpleAkskSecurityContextHelper` 无需任何改动。

## 升级指南

无破坏性变更，server-starter 升级到 2.0.0 时自动引入。

---

## 贡献者

- @surezzzzzz