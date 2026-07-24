# smart-redis-limiter-audit-listener-starter

> 使用历史 `1.x` 的应用请查看 [README.1.x.md](README.1.x.md)。1.x 已封版，不再维护。

为 `smart-redis-limiter-starter:2.0.0` 提供限流**执行结果**审计。监听器在事件发布线程生成脱敏审计快照，再异步交给业务 Handler；不审计 Management 策略 CRUD。

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-starter:2.0.0'
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-audit-listener-starter:2.0.0'
```

该组合使用 core `2.1.0` 和 `simple-redis-route-starter:1.1.0`。Redis Route 是 limiter 2.x 的必需能力；Management `1.0.0` 是可选的独立策略管理服务，不是 audit 的运行时依赖。

## 接入 Handler

默认日志 Handler 已启用。需要写入数据库、消息系统或检索平台时，实现 `SmartRedisLimiterAuditHandler`：

```java
@Component
public class BusinessLimiterAuditHandler implements SmartRedisLimiterAuditHandler {

    @Override
    public void handle(SmartRedisLimiterRecord record) {
        if (!record.isPassed()) {
            save(record.getResourceCode(), record.getPolicySource(), record.getFallbackReason());
        }
    }
}
```

可注册多个 Handler；单个 Handler 失败不会阻断其他 Handler 或影响限流请求。`SmartRedisLimiterAuditHandler` 接口在 2.0.0 保持不变。

## 事件选择

Audit 不单独过滤事件，是否发布由 limiter starter 的 `log-on-pass` 决定：

| 执行结果 | `log-on-pass=false` | `log-on-pass=true` |
|----------|---------------------|--------------------|
| 正常通过 | 不发布 | 发布 |
| 触发限流 | 发布 | 发布 |
| fallback allow | 发布 | 发布 |
| fallback deny | 发布 | 发布 |

`log-on-pass` 位于 limiter starter 配置，不属于本模块。

## 审计记录

Handler 接收 core 的 `SmartRedisLimiterRecord`。2.0.0 完整映射执行快照：

| 分组 | 字段 |
|------|------|
| 结果 | `passed`、`limit`、`remaining`、`resetAt`、`durationNanos` |
| 限流规则 | `limitKey`、`keyStrategy`、`algorithm`、`limitRules`、`source` |
| Route | `routeKey`、`datasourceKey`、`redisMode`、`routeRequired`、`routeResolved` |
| fallback | `fallbackReason` |
| 动态策略 | `resourceCode`、`policySource`、`policyRevision` |
| 请求/方法 | `requestUri`、`httpMethod`、`clientIp`、`matchedPathPattern`、`methodName`、`methodQualifiedName` |
| 身份与关联 | `clientId`、`clientType`、`userId`、`username`、`traceId` |
| 审计时间 | `timestamp`（审计监听器生成快照的时间） |

`limitKey` 与 `routeKey` 都会交给自定义 Handler。当前生产执行中它们可能相同，但不应依赖该等值关系。远程策略场景中 key 只含 subject 的 SHA-256 摘要，SDK 不传递或恢复原始 subject。

## 隐私与默认日志边界

2.0.0 固定将 `record.extra` 设为 `null`，不会将 `SmartRedisLimiterEvent.attributes` 写入审计记录。因此原始 subject、内部执行属性、请求头、cookie、密码、凭据、policy token、会话标识和请求/响应体等任意 attributes 都不会通过本模块透传。

`requestUri`、`clientIp`、身份字段和 TraceId 是 `SmartRedisLimiterRecord` 的显式字段，仍会交给自定义 Handler；其存储、脱敏和留存由业务方负责。默认日志只输出结果、算法、策略来源、Route/fallback 诊断及限额结果；不会输出限流 Key、路由 Key、用户标识、IP、TraceId、原始 URI 或 `extra`。

## 用户与 Trace 扩展

可选实现 core 提供的 `SmartRedisLimiterUserProvider` 与 `SmartRedisLimiterTraceIdProvider`。2.0.0 在事件发布线程读取这些 Provider，避免 SecurityContext 或 MDC 在异步 Handler 线程丢失。Provider 失败只会使相应字段为空，不会阻断审计分发。

## 配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `io.github.surezzzzzz.sdk.audit.limiter.listener.handler.log.enabled` | `true` | 是否注册默认日志 Handler |

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        audit:
          limiter:
            listener:
              handler:
                log:
                  enabled: false
```

## 升级至 2.0.0

1. 将 limiter 升级为 `2.0.0`，完成 Redis Route 配置。
2. 将 audit listener 升级为 `2.0.0`。
3. 现有 `SmartRedisLimiterAuditHandler` 实现无需修改；可读取新增的 Route、fallback 与动态策略字段。
4. 如旧 Handler 读取 `record.extra`，应移除该依赖：2.0.0 故意不再透传任意 attributes。
5. Management 策略 CRUD 不在本模块审计范围内。
