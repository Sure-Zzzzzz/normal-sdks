# simple-feedback-aksk-feign-client-starter 1.0.1

## 发布信息

- 版本：`1.0.1`
- 类型：Feature / AKSK Feign 传输实现
- 发布日期：2026-10-28

## 变更内容

- 新增 Spring Boot 2.7.9 / Spring Cloud 2021.0.8 / OpenFeign 3.1.8 的 Feedback AKSK Feign 传输实现。
- Feign wire 接口与 core 快照模型解耦，统一复用响应校验和异常分类。
- 覆盖提交、查询、处理、附件下载和应用注册的 13 个公开操作，保留 multipart 附件元数据、查询参数和下载响应头。

## 依赖升级

| 依赖 | 版本 |
|---|---:|
| `simple-feedback-client-core` | 1.0.1 |
| `simple-aksk-feign-redis-client-starter` | 3.0.2 |
| Spring Cloud OpenFeign | 3.1.8 |

## 兼容性

- Spring Boot 2.7.9 / Spring Cloud 2021.0.8 / Java 8。
- Jakarta 宿主使用 `simple-feedback-aksk-feign-client-jakarta-starter:1.0.1`。

## 新增测试

- `FeedbackFeignClientAdapterTest`：覆盖 wire 到 core 的适配、异常分类和下载头保留。
- `FeedbackFeignMultipartEncodingTest`：覆盖 JSON submission part 和同名附件的真实 multipart 编码。

## 向后兼容性

- 保持 `FeedbackClient` 1.0.1 方法面和异常分类；认证仍由 AKSK 底座注入，客户端不转发人员令牌。

## 升级指南

宿主引入本 starter、`simple-feedback-client-core:1.0.1` 和 OpenFeign 3.1.8 线依赖，启用 `@EnableFeignClients` 扫描 `FeedbackFeignClient`，配置 `base-url` 和 AKSK 凭据。提交失败重试时复用原 `Idempotency-Key`。
