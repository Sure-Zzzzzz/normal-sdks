# CHANGELOG - simple-aksk-security-context-starter 1.0.3

## 发布日期

2026-05-02

## 版本类型

重构

## 变更概述

升级依赖 `simple-aksk-resource-core` 至 1.0.3，将重复定义的常量和工具方法迁移到 core，消除模块间重复代码。

### 常量迁移

以下常量从 `SimpleAkskSecurityContextConstant` 移除，统一引用 `SimpleAkskResourceConstant`（core）：

| 常量 | 说明 |
|------|------|
| `ACCESS_SOURCE_HEADER` | AkskAccessEvent source 标识 |
| `HEADER_USER_AGENT` | HTTP User-Agent 请求头名 |
| `FIELD_TRACE_ID` | 链路追踪 ID 字段名 |

### 工具类迁移

`HeaderNameConverter`（kebab-case → camelCase）迁移到 core，`AkskSecurityContextFilter` 改为引用 core 的版本。`buildAccessEvent` 改为调用 core 的 `AkskContextHelper`。

## 向后兼容性

完全向后兼容，接入方无感知。

## 贡献者

- @surezzzzzz
