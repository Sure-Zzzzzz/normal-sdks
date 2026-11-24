# 1.1.0

## 发布信息

- 类型：Feature / 向后兼容
- 基线版本：1.0.0

## 变更

- 升级 `simple-aksk-feign-redis-client-jakarta-starter:1.1.0` 与 `simple-aksk-client-core-jakarta:1.1.0`。
- 新增 `AkskProfileFeignConfiguration`、`AkskProfileFeignRequestInterceptor` 和拦截器工厂。
- 空 Profile 或 `default` 使用默认拦截器；命名 Profile 只注入一个认证拦截器。
- 工厂 `create` 对未配置的 Profile 抛出 `ConfigurationException`，不静默改用默认 AKP。

## 兼容性

- 默认 AKP 配置、默认 Feign Client 和公开 Bean 行为不变。
- 手工 Feign Builder 需要显式选择 Profile 配置；未选择时继续走默认链。
- Secret 不进入日志、Redis key 或异常消息。
