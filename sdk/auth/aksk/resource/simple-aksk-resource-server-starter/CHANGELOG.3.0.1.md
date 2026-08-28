# simple-aksk-resource-server-starter 3.0.1 变更记录

## 依赖升级与事件模型同步

- `simple-application-authorization-core` `1.0.0` → `1.0.1`（`api`）：授权时效判定引入默认 2 秒时钟容差（仅放宽 `issuedAt` 下界，`expiresAt` 上界零容差），消除上游 AKSK Server 签发时间亚秒取整导致的签发后立即访问间歇拒绝。
- `simple-resource-server-core` `1.0.1` → `1.1.1`（`api`）：`ResourceAccessEvent` 事件模型已迁至 core（新包名 `io.github.surezzzzzz.sdk.auth.resource.core.event`，纯模型对象不再继承 `ApplicationEvent`）。
- 测试组装依赖 `simple-resource-server-starter` `1.0.2` → `1.1.0`（`testImplementation`）：配合公共 Starter 的事件模型迁移，仅影响本模块测试，不进入使用方 classpath。
- 本模块生产源码不引用 `ResourceAccessEvent`（事件由公共 Starter 的认证过滤器发布），Provider 行为不变。

## 兼容性

- 对只消费 HTTP 行为的业务方完全兼容：自动装配、配置项、`ResourceAuthenticationAdapter` 注册、内省与缓存行为均不变。
- 监听 `ResourceAccessEvent` 的使用方需按公共 core 1.1.x 的事件迁移说明同步 import 与构造调用（见 [simple-resource-server-starter 1.1.0 变更记录](../../resource/simple-resource-server-starter/CHANGELOG.1.1.0.md)）；`@EventListener` 注解方法监听完全兼容。
