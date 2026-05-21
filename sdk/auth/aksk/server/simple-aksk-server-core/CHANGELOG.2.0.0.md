# CHANGELOG - simple-aksk-server-core 2.0.0

## 发布日期

2026-05-21

## 版本类型

Breaking Change - 安全增强，1.x 封版

## 变更概述

- **JWE Token 增强支持**：新增 JWE 算法常量（A256GCMKW / A256GCM）和 `encryptionKey` 配置项，为 server-starter 提供 JWE 加密基础设施

## 变更详情

### 新增

| 类 | 说明 |
|----|------|
| `JwtConfig.encryptionKey` | AES-256 密钥配置项，通过流水线注入环境变量 `AKS_AES_256_KEY` |
| `SimpleAkskServerConstant.JWE_*` | JWE 算法常量：Key Encryption、Content Encryption、AES 密钥长度等 |

### 依赖升级

| 模块 | 旧版本 | 新版本 |
|------|-------|-------|
| simple-aksk-core | 1.0.1 | 1.0.2 |

## 升级指南

无破坏性变更，server-starter 升级到 2.0.0 时自动引入。

---

## 贡献者

- @surezzzzzz