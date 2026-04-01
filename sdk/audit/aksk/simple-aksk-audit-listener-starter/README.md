# Simple AKSK Audit Listener Starter

AKSK 审计监听器，监听 AKSK 访问事件并调用业务处理器。

## 功能特性

- 自动监听 `AkskAccessEvent` 事件（由 `simple-aksk-security-context-starter` 或 `simple-aksk-resource-server-starter` 发布）
- 转换为 `AkskAuditRecord` 审计记录
- 支持多个 `AkskAuditHandler` 同时工作
- 提供默认的日志Handler（可配置开关）
- 异步处理，不影响主流程性能
- 支持自定义链路追踪ID提供者

## 快速开始

### 1. 添加依赖

```gradle
implementation 'io.github.surezzzzzz:simple-aksk-audit-listener-starter:1.0.0'
```

### 2. 实现审计处理器

```java
@Component
public class MyAkskAuditHandler implements AkskAuditHandler {

    @Override
    public void handle(AkskAuditRecord record) {
        // 处理审计记录，例如：
        // 1. 存储到数据库
        // 2. 发送到消息队列
        // 3. 写入日志文件
        log.info("AKSK audit: user={}, uri={}, method={}, source={}",
            record.getUsername(),
            record.getRequestUri(),
            record.getHttpMethod(),
            record.getSource());
    }
}
```

**支持多个Handler**：可以实现多个 `AkskAuditHandler`，所有Handler都会被调用。例如：
- `DatabaseAuditHandler`：存储到数据库
- `KafkaAuditHandler`：发送到Kafka
- `LogAuditHandler`：写入日志文件

### 3. （可选）启用默认日志Handler

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        audit:
          aksk:
            listener:
              handler:
                log:
                  enabled: true  # 启用默认日志Handler
```

启用后会自动打印审计日志：
```
INFO  AKSK_AUDIT - AkskAuditRecord(clientId=xxx, userId=xxx, requestUri=/api/test, ...)
```

### 4. （可选）实现链路追踪ID提供者

如果需要记录链路追踪ID，需要实现 `AkskAuditTraceIdProvider` 接口：

```java
@Component
public class MyAkskAuditTraceIdProvider implements AkskAuditTraceIdProvider {

    @Override
    public String getTraceId() {
        // 从请求 Header 获取 traceId
        HttpServletRequest request =
            ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        return request.getHeader("X-Trace-Id");
    }
}
```

### 5. 自动生效

当业务实现了 `AkskAuditHandler` 接口后，监听器会自动注册并开始工作。

## 工作原理

1. **事件发布**：`simple-aksk-security-context-starter`（Header认证）或 `simple-aksk-resource-server-starter`（JWT认证）在认证成功后发布 `AkskAccessEvent` 事件
2. **事件监听**：`AkskAuditEventListener` 监听事件并转换为 `AkskAuditRecord`
3. **异步处理**：使用 `@Async` 异步调用所有 `AkskAuditHandler` 实现
4. **容错处理**：单个Handler异常不影响其他Handler和主流程

## 配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `io.github.surezzzzzz.sdk.audit.aksk.listener.handler.log.enabled` | Boolean | false | 是否启用默认日志Handler |

## 审计记录字段

| 字段 | 类型 | 说明 |
|------|------|------|
| clientId | String | 客户端ID（AccessKey） |
| clientType | String | 客户端类型（platform/user） |
| userId | String | 用户ID |
| username | String | 用户名 |
| roles | String | 角色 |
| scope | String | 权限范围 |
| requestUri | String | 请求URI |
| httpMethod | String | HTTP方法 |
| remoteAddr | String | 来源IP |
| userAgent | String | User-Agent |
| timestamp | Long | 时间戳 |
| source | String | 来源类型（header/jwt） |
| traceId | String | 链路追踪ID |
| context | Map | 完整上下文 |

## 注意事项

1. **异步处理**：监听器使用 `@Async` 异步处理，不会阻塞主流程
2. **异常处理**：单个Handler抛出异常不会影响其他Handler和主流程，仅记录错误日志
3. **链路追踪ID**：如果不实现 `AkskAuditTraceIdProvider`，traceId 字段将为 null
4. **性能考虑**：Handler应避免耗时操作，建议使用消息队列异步处理
5. **多Handler支持**：所有实现了 `AkskAuditHandler` 的Bean都会被调用，按Spring容器顺序执行

## 依赖要求

- Spring Boot 2.x
- 需要配合以下任一认证模块使用：
  - `simple-aksk-security-context-starter`（Header认证）
  - `simple-aksk-resource-server-starter`（JWT认证）

## 版本历史

### 1.0.0 (2026-04-01)
- 初始版本
- 支持监听 `AkskAccessEvent` 事件
- 支持多Handler机制
- 提供默认日志Handler
- 支持自定义链路追踪ID提供者
- 异步处理，容错机制
