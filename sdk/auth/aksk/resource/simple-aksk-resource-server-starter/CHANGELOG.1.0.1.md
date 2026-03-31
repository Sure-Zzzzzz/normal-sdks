# Changelog - v1.0.1

## 发布日期

2026-03-31

## 版本类型

**Patch Release** - 向后兼容的功能增强

## 变更概述

本版本引入事件发布机制，在 JWT 认证成功后自动发布 Spring 事件，支持业务监听事件实现审计、监控等功能。

## 新增功能

### 1. JWT 认证事件发布

在 JWT 认证成功后，自动发布 `AkskAccessEvent` 事件。

**事件内容：**

- 客户端信息：clientId、clientType
- 用户信息：userId、username、roles、scope
- 请求信息：requestUri、httpMethod、remoteAddr、userAgent
- 元数据：source（固定为 "jwt"）、traceId、context
- 时间戳：通过 `ApplicationEvent.getTimestamp()` 获取

## 依赖变更

### 更新依赖

```gradle
api "simple-aksk-resource-core:1.0.1"  // 从 1.0.0 升级
```

**原因：** core 1.0.1 新增了 `AkskAccessEvent` 事件定义。

## 向后兼容性

✅ **完全向后兼容**

- 所有 import 路径保持不变
- 现有业务代码无需修改
- 事件发布是新增功能，不影响现有逻辑

## 升级指南

### 从 1.0.0 升级到 1.0.1

1. **更新依赖版本**

```gradle
implementation "io.github.surezzzzzz:simple-aksk-resource-server-starter:1.0.1"
```

2. **无需修改代码**

现有代码无需任何修改，直接升级即可。

## 使用场景

### 1. 审计日志

记录谁在什么时间通过 JWT 访问了哪些 API。

### 2. 安全监控

监控 JWT 访问行为，如异常访问、权限滥用等。

### 3. 访问统计

统计 API 调用次数、用户活跃度等指标。

## 事件监听示例

```java

@Component
public class MyAkskAccessListener {

    @EventListener
    @Async  // 可选：异步处理
    public void onAkskAccessEvent(AkskAccessEvent event) {
        log.info("JWT access: user={}, uri={}, method={}, source={}",
                event.getUsername(),
                event.getRequestUri(),
                event.getHttpMethod(),
                event.getSource()  // "jwt"
        );
    }
}
```

## 注意事项

1. **事件发布是同步的**：Spring 默认同步发布事件，如需异步处理，在监听器方法上添加 `@Async`
2. **事件监听器可以有多个**：多个 Bean 可以同时监听同一个事件
3. **事件发布失败不影响主流程**：捕获了事件发布异常，仅记录警告日志
4. **性能影响**：事件发布本身开销很小，但监听器应避免耗时操作

## 贡献者

- @surezzzzzz
