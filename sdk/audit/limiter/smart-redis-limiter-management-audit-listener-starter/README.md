# smart-redis-limiter-management-audit-listener-starter

限流**策略变更**审计监听器。与管理宿主（`smart-redis-limiter-management-starter`）同进程部署，消费两类管理事件并分发受控审计记录；限流**执行**事件的审计由 `smart-redis-limiter-audit-listener-starter`（执行审计件）在运行端进程消费，两件各随其宿主、互不混装。Spring Boot 2 / Java 8。

## 事件与记录口径

| 事件链 | 审计记录 |
| --- | --- |
| 三元组管理事件（`SmartRedisLimiterManagementEvent`） | 动作事实：操作/服务/资源/对象/前后启停/revision/可读操作人/发生时间；策略窗口数值不进记录 |
| 类型化管理事件（`SmartRedisLimiterTypedManagementEvent`） | 受控摘要：计数对象只留命名空间摘要，操作人只留 83 字符短摘要 |

失败、回滚与 no-op 不发布事件；Handler 异常互相隔离，不中断分发、不反写事件发布方。

## 依赖

```gradle
implementation 'io.github.surezzzzzz:smart-redis-limiter-management-starter:2.0.0'
implementation 'io.github.surezzzzzz:smart-redis-limiter-management-audit-listener-starter:1.0.0'
```

事件是进程内 Spring Event，不跨进程：管理宿主同装本件即生效；不装则管理事件无消费者，宿主照常工作。

## 接入 Handler

两种事件各一个 Handler 接口：`SmartRedisLimiterManagementAuditHandler` 与 `SmartRedisLimiterTypedAuditHandler`，按需实现任意子集。默认日志 Handler 均已启用，分别由 `management-enabled`、`typed-enabled` 控制。

```java
@Component
public class BusinessLimiterManagementAuditHandler implements SmartRedisLimiterManagementAuditHandler {

    @Override
    public void handle(SmartRedisLimiterManagementAuditRecord record) {
        if ("DELETE".equals(record.getOperation())) {
            save(record.getServiceCode(), record.getResourceCode(), record.getOperator());
        }
    }
}
```

## 配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `io.github.surezzzzzz.sdk.audit.limiter.management.listener.handler.log.management-enabled` | `true` | 是否注册三元组管理事件默认日志 Handler |
| `...handler.log.typed-enabled` | `true` | 是否注册类型化管理事件默认日志 Handler |
