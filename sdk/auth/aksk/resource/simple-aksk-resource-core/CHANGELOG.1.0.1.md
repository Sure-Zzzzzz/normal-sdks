# Changelog - v1.0.1

## 发布日期

2026-03-31

## 版本类型

**Patch Release** - 向后兼容的功能增强

## 变更概述

本版本引入事件发布机制，支持业务监听 AKSK 访问事件实现审计、监控等功能。

## 新增功能

### 1. AKSK 访问事件

新增 `AkskAccessEvent` 事件类，在 AKSK 认证成功后发布。

**事件内容：**
- 客户端信息：clientId、clientType
- 用户信息：userId、username、roles、scope
- 请求信息：requestUri、httpMethod、remoteAddr、userAgent
- 元数据：source（header/jwt）、traceId、context
- 时间戳：通过 `ApplicationEvent.getTimestamp()` 获取

**包路径：**
```java
io.github.surezzzzzz.sdk.auth.aksk.resource.core.event.AkskAccessEvent
```

## 依赖变更

### 新增依赖

```gradle
compileOnly "org.springframework:spring-context"
```

**原因：** 需要 `ApplicationEvent` 和 `ApplicationEventPublisher`。

## 向后兼容性

✅ **完全向后兼容**

- 删除的接口从未发布，不影响现有用户
- 事件是新增功能，不影响现有逻辑

## 升级指南

### 从 1.0.0 升级到 1.0.1

1. **更新依赖版本**

```gradle
api "io.github.surezzzzzz:simple-aksk-resource-core:1.0.1"
```

2. **无需修改代码**

现有代码无需任何修改，直接升级即可。

## 使用场景

### 1. 审计日志

记录谁在什么时间访问了哪些 API。

### 2. 安全监控

监控异常访问行为，如频繁失败、异常IP等。

### 3. 访问统计

统计 API 调用次数、用户活跃度等指标。

## 注意事项

1. **事件发布是同步的**：Spring 默认同步发布事件，如需异步处理，在监听器方法上添加 `@Async`
2. **事件监听器可以有多个**：多个 Bean 可以同时监听同一个事件
3. **事件发布失败不影响主流程**：starter 会捕获事件发布异常，仅记录警告日志

## 贡献者

- @surezzzzzz
