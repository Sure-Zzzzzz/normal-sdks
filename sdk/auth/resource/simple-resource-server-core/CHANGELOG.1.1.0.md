# simple-resource-server-core 1.1.0 变更记录

## 新增公共资源访问事件

- 新增 `ResourceAccessEvent`（`io.github.surezzzzzz.sdk.auth.resource.core.event`），自 `simple-resource-server-starter` 的 `io.github.surezzzzzz.sdk.auth.resource.server.event` 包迁入。
- 事件仅包含统一资源认证链完成验证后的主体、应用和请求摘要，不承载认证凭据、声明原文或Servlet对象。
- 迁入时改为纯模型对象（原继承 `org.springframework.context.ApplicationEvent`）：
  - core 保持零 Spring 依赖（依赖边界测试禁用 `org.springframework.*` import）；
  - 移除构造参数 `Object source`（无消费方使用 `getSource()`），`timestamp` 改为事件自记创建时间（原继承自 `ApplicationEvent` 的语义不变）；
  - 发布方式不变：`ApplicationEventPublisher#publishEvent(Object)`，Spring 4.2+ 对任意对象事件原生支持，`@EventListener` 监听方式不受影响。
- 事件模型归位 core 后，审计、指标等事件消费挂件（aksk-resource-audit-listener-starter、aksk-resource-server-metrics-starter 及未来的 iam 系挂件）只依赖 core 即可消费事件，不引入资源服务自动装配。

## 兼容性

core 自身为向后兼容的增量版本（既有模型、SPI、凭据契约不变，零 Spring 依赖不变）。事件类的包名与构造签名相对 starter 旧版为变更，引用方随 starter 1.1.0 统一切换（见 starter 变更记录）。
