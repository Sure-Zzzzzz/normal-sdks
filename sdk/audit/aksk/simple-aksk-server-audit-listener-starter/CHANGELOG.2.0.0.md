# CHANGELOG - simple-aksk-server-audit-listener-starter 2.0.0

## 发布日期

2026-05-26

## 版本类型

依赖升级，1.x 封版

## 变更概述

1. **依赖升级**：server-core 升至 2.0.1，server-starter 升至 2.0.0

## 变更详情

### 依赖变更

| 依赖 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-aksk-server-core` | 1.0.4 | 2.0.1 |
| `simple-aksk-server-starter`（测试） | 1.0.6 | 2.0.0 |

### 行为变更

无。`ServerTokenAuditHandler` 接口、`ServerTokenAuditRecord` 字段均不变。

## 升级指南

### 从 1.x 升级到 2.0.0

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-server-audit-listener-starter:2.0.0'
```

无需修改业务代码。

## 贡献者

- @surezzzzzz
