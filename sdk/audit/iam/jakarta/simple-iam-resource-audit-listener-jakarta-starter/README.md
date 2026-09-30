# simple-iam-resource-audit-listener-jakarta-starter

IAM 资源访问审计监听器（jakarta 线）。

本模块是 `simple-iam-resource-audit-listener-starter`（javax 线）的 SB 3.x 对等件：订阅公共资源层的
`ResourceAccessEvent`，把 IAM 来源的已认证访问转换为审计记录并异步分发给 `IamResourceAuditHandler`。
事件模型来自已发布的 `simple-resource-server-core`（纯模型，代际无关），配合
`simple-iam-resource-server-jakarta-starter` 使用。业务方从 javax 线迁移换依赖坐标，包名与类名不变；但与 javax 线存在三处装配语义差异，迁移时需按需调整配置：

| 差异点 | javax 线 | jakarta 线（本模块） |
|---|---|---|
| 总开关 | 无（装配即生效） | `io.github.surezzzzzz.sdk.audit.iam.resource.enable`，默认 `true`；置 `false` 整个模块不装配 |
| 默认日志 Handler | `...listener.handler.log.enabled` 控制，**默认关** | **默认注册**，无条件装配，可与业务 handler 并存 |
| 配置前缀 | 内联在注解字符串 | 收敛到常量类（`SimpleIamResourceAuditListenerConstant.CONFIG_PREFIX`） |

总开关 `io.github.surezzzzzz.sdk.audit.iam.resource.enable`（默认 `true`）：置 `false` 时模块全部组件（监听器、默认日志 Handler、异步执行器）不装配；业务自注册的 `IamResourceAuditHandler` Bean 不受影响。

## 组合方式

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-iam-resource-server-jakarta-starter:1.0.0'
    implementation 'io.github.sure-zzzzzz:simple-iam-resource-audit-listener-jakarta-starter:1.0.0'
}
```

引入即装配。监听器仅在容器中存在 `IamResourceAuditHandler` 时注册；默认提供 `LogIamResourceAuditHandler`
（结构化日志），业务方可自建 handler 落库或转发（与默认日志 handler 并存）。可选
`IamResourceAuditTraceIdProvider` 透传调用链追踪标识。

## 边界

- 只处理 IAM 来源的访问事件；其他来源由各自的审计挂件处理。
- 审计记录不含 Token 原文、凭据或授权声明。
- 消费端 Handler 失败只记告警，不影响业务请求。

## 验证

1.0.0 含 2 个测试类（3 项断言用例）：端到端类（2 项）与总开关反证类（`enable=false` 时模块组件零装配、业务 Bean 不受影响，1 项）：MockMvc 走公共资源安全链 + IAM Provider stub 验证，覆盖 IAM 来源访问的审计记录生成（含 TraceId 透传）与非 IAM 来源过滤。
