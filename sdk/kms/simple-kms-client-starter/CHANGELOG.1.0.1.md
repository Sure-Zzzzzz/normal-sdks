# Changelog - v1.0.1

## 发布日期

2026-07-29

## 版本类型

**Bug Fix** - 向后兼容的发布编译修复

## 变更概述

修复 `KmsPolicy` 响应解析路径中对 Lombok 生成 Builder 内部类型的显式源码引用，确保发布构建环境能够稳定编译 Client 源码。

## 问题与修复

### 策略响应解析的 Builder 类型引用

`1.0.0` 在可选 `keyVersion` 和 `expiresAt` 字段的解析辅助方法签名中，直接声明了 Lombok 在编译期生成的 `KmsPolicy.KmsPolicyBuilder` 类型。部分发布编译环境在处理该源码引用时无法解析生成类型，导致 Client 编译失败。

本版本将可选字段解析收敛为返回 `Integer` 或 `Instant` 的私有辅助方法，再由 `KmsPolicy.builder()` 链式赋值。策略响应的字段语义、JSON 契约和公开 API 均保持不变。

## 测试

- `RestTemplateKmsClientTest` 持续覆盖策略响应中的 `keyVersion` 和 `expiresAt` 解析。
- 四档 Client Spring Boot 矩阵均执行完整单元测试与对固定 Spring Boot 2.7.9 Server 的远程 HTTP E2E。

## 向后兼容性

- 不修改任何公开类、方法、配置项、HTTP 路径、请求或响应字段。
- `KmsPolicy` 的可选 `keyVersion`、`expiresAt` 语义保持不变：缺失或 `null` 时仍返回 `null`。
- 不引入 Server、Core、JDBC、MySQL 或密钥材料依赖到 Client 发布产物。

## 升级指南

将依赖版本从 `1.0.0` 升级至 `1.0.1`。无需修改配置或调用代码。
