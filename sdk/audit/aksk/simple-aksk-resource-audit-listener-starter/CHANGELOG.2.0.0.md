# CHANGELOG - simple-aksk-resource-audit-listener-starter 2.0.0

## 发布日期

2026-05-26

## 版本类型

Breaking Change - 依赖升级，移除 security-context-starter，1.x 封版

## 变更概述

1. **依赖升级**：resource-core 升至 2.0.0，resource-server-starter 升至 2.0.0
2. **移除 security-context-starter**：Header 认证模式废弃，仅支持 INTROSPECT 模式
3. **审计事件 source 变更**：`source` 字段仅为 `"introspect"`，不再出现 `"header"` 或 `"jwt"`

## 变更详情

### 依赖变更

| 依赖 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-aksk-resource-core` | 1.0.1 | 2.0.0 |
| `simple-aksk-resource-server-starter`（测试） | 1.0.1 | 2.0.0 |
| `simple-aksk-security-context-starter`（测试） | 1.0.2 | **已移除** |

### 行为变更

- `AkskAuditRecord.source` 字段值固定为 `"introspect"`

## 升级指南

### 从 1.x 升级到 2.0.0

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-audit-listener-starter:2.0.0'
```

无需修改业务代码，`AkskAuditHandler` 接口不变。

## 贡献者

- @surezzzzzz
