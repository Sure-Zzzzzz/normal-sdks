# task-retry-starter 2.0.0

发布日期：2026-07-10

类型：Breaking Refactor

## 变更内容

- 规范化模块结构：新增 `TaskRetryPackage`、`TaskRetryComponent`、`TaskRetryAutoConfiguration`、`TaskRetryProperties`。
- 新增配置前缀 `io.github.surezzzzzz.sdk.retry.task`，默认零配置启用；显式配置 `enable=false` 时关闭默认 Bean。
- 统一公共延迟单位为毫秒，方法参数和配置项均使用 `Millis` 语义。
- 新增 `TaskRetryConstant`、`ErrorCode`、`ErrorMessage`、`RetryStrategyType`。
- 新增 `TaskRetryException`、`TaskRetryValidationException`，参数校验错误使用模块异常体系。
- 新增 `RetryRequest`，支持默认策略、固定延迟、指数退避和无延迟策略。
- 新增 `RetrySleeper`、`RetryPredicate`、`RetryListener` 扩展点，默认实现均支持调用方 Bean 覆盖。
- 重构测试结构到 `io.github.surezzzzzz.sdk.retry.task.test.cases`，通过 no-op sleeper 避免真实长时间等待。

## 破坏性变更

- `RetryPackage` 改为 `TaskRetryPackage`。
- `configuration.RetryComponent` 改为 `annotation.TaskRetryComponent`。
- `RetryConfiguration` 改为 `TaskRetryAutoConfiguration`。
- 默认 Bean 从 2.0.0 开始零配置启用，`io.github.surezzzzzz.sdk.retry.task.enable=false` 可显式关闭。
- `retryInterval` / `maxDelaySeconds` 语义统一迁移为毫秒级参数。

## 向后兼容性

2.0.0 保留 `executeWithRetry`、`executeWithFixedDelay`、`executeWithFastRetry`、`executeWithSlowRetry` 等常用方法名，但参数类型从装箱类型改为基本类型，延迟单位统一为毫秒。

## 升级指南

- 如果 1.x 调用方按秒传参，升级到 2.0.0 时需要将延迟参数换算为毫秒。
- 如果直接引用旧配置类或旧组件注解，需要切换到新的 `TaskRetryAutoConfiguration` 和 `TaskRetryComponent`。
- 如果希望覆盖默认等待、重试判断或监听逻辑，直接注册自定义 `RetrySleeper`、`RetryPredicate`、`RetryListener` Bean。
