# simple-resource-server-starter 1.1.0 变更记录

## 事件模型迁出至 core

- `ResourceAccessEvent` 从本模块 `io.github.surezzzzzz.sdk.auth.resource.server.event` 包迁出，由 `simple-resource-server-core` 统一提供（新包名 `io.github.surezzzzzz.sdk.auth.resource.core.event`，1.1.0 引入）。
- 认证过滤器的行为不变：仍在本模块完成认证后发布 `ResourceAccessEvent`，仅事件类坐标归属变化。
- 依赖变更：`simple-resource-server-core` `1.0.1` → `1.1.1`（`api`；1.1.1 将传递的 `simple-application-authorization-core` 升级到 1.0.1，为授权时效判定引入默认 2 秒时钟容差，消除上游签发时间亚秒取整导致的签发后立即访问间歇拒绝）。
- 本模块不再直接声明 `simple-application-authorization-core`，统一经 `simple-resource-server-core` 的 `api` 传递获得。

## 兼容性（破坏性变更）

- **事件类包名与构造签名变更**：包名切换为 `io.github.surezzzzzz.sdk.auth.resource.core.event`；事件改为纯模型对象（不再继承 `ApplicationEvent`），构造参数移除 `Object source`，`getTimestamp()` 语义不变（事件创建时间）。引用方需同步 import 与构造调用。
- **监听方式不变**：`@EventListener` 注解方法监听 `ResourceAccessEvent` 完全兼容；实现 `ApplicationListener<ResourceAccessEvent>` 接口的旧写法需改为 `@EventListener` 方法（泛型约束要求 `ApplicationEvent` 子类）。
- 事件消费挂件（audit listener、metrics listener 等）建议改为直接依赖 `simple-resource-server-core` 消费事件，不再经本模块传递。
- 其余自动装配、配置项、过滤器行为均不变。
