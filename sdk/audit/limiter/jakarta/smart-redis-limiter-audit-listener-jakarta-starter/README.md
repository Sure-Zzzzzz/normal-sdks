# smart-redis-limiter-audit-listener-jakarta-starter

限流审计监听（jakarta 线）：消费限流运行端发布的事件，转换为安全审计快照后分发给可插拔 Handler。Spring Boot 3 / Java 17。与 javax 线 `smart-redis-limiter-audit-listener-starter` 同构镜像。

## 解决什么、谁该接

- 你的 Spring Boot 3 应用引了 `smart-redis-limiter-jakarta-starter`（限流运行端），需要把限流执行事件与策略管理事件落审计（日志或自建持久化）。
- 不引本件则事件仅由 Spring 默认机制丢弃，无审计记录。

## 依赖坐标

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-audit-listener-jakarta-starter:1.0.0'
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-core:2.2.0'
```

## 能力

- 消费 v1 执行事件（`SmartRedisLimiterEvent`）：转换为 `SmartRedisLimiterRecord` 安全快照（不含限流 Key、用户标识、原始 URI、扩展属性），异步分发给全部 `SmartRedisLimiterAuditHandler`。
- 消费 v2 类型化管理事件（`SmartRedisLimiterTypedManagementEvent`）：转换为 `SmartRedisLimiterTypedAuditRecord` 受控摘要（操作/服务/资源/维度/选择器/规则标识/revision/结果/受控原因/操作人短摘要）；计数对象只保留命名空间受控摘要，不输出原始用户、客户、IP 或自定义 key。
- 默认日志 Handler：执行审计（`enabled` 控制，默认开）与类型化管理审计（`typed-enabled` 控制，默认开），SUCCESS/FAILURE 分级输出；业务方可注册自建 Handler 持久化到数据库或 ES。
- Handler 异常隔离：单个 Handler 失败不中断后续分发、不反写事件发布方；审计侧不反查任何身份系统。

## 配置

```yaml
io.github.surezzzzzz.sdk.audit.limiter:
  log-handler:
    enabled: true        # 执行事件日志审计（默认开）
    typed-enabled: true  # 类型化管理事件日志审计（默认开）
```

## 语义边界

- 审计是 best-effort 快照，不是可靠审计队列；需要可靠投递由自建 Handler 的持久化保证。
- 类型化管理审计的操作人只记录短摘要（`resource:v1:sha256:` 前缀）；完整身份事实由事件 attributes 携带，不进入审计记录。
