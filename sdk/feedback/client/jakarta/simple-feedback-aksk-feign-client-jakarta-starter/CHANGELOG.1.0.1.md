# simple-feedback-aksk-feign-client-jakarta-starter 1.0.1

## 发布信息

- 版本：`1.0.1`
- 类型：Feature / Jakarta AKSK Feign 传输实现
- 发布日期：2026-10-28

## 变更内容

- 新增 Spring Boot 3.4.2 / Spring Cloud 2024.0.0 / OpenFeign 4.2.0 的 Feedback Jakarta Feign 传输实现。
- 通过 AKSK Feign 拦截器注入机器认证，覆盖 Feedback 全部公开操作、multipart 附件和下载响应头。
- 与 `simple-feedback-client-core:1.0.1` 对齐 wire 映射、响应校验和异常分类。

## 依赖升级

| 依赖 | 版本 |
|---|---:|
| `simple-feedback-client-core` | 1.0.1 |
| `simple-aksk-feign-redis-client-jakarta-starter` | 1.0.0 |
| Spring Cloud OpenFeign | 4.2.0 |

## 兼容性

- Spring Boot 3.4.2 / Spring Cloud 2024.0.0 / Java 17。
- javax 宿主使用 `simple-feedback-aksk-feign-client-starter:1.0.1`。

## 新增测试

- `FeedbackFeignClientContractTest`：覆盖 Feign wire 方法面、multipart part 和响应适配边界。

## 向后兼容性

- 保持 `FeedbackClient` 1.0.1 方法面和异常分类；认证仍由 Jakarta AKSK 底座注入，客户端不转发人员令牌。

## 升级指南

宿主引入本 starter、`simple-feedback-client-core:1.0.1` 和 OpenFeign 4.2.0 线依赖，提供 `akskFeignRequestInterceptor`、配置 `base-url`，再按宿主启用 Feign 客户端。提交失败重试时复用原 `Idempotency-Key`。
