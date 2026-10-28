# simple-feedback-aksk-resttemplate-client-jakarta-starter 1.0.1

## 发布信息

- 版本：`1.0.1`
- 类型：Feature / Jakarta AKSK RestTemplate 传输实现
- 发布日期：2026-10-28

## 变更内容

- 新增 Spring Boot 3.4.2 / Java 17 的 Feedback Jakarta RestTemplate 传输实现。
- 复用 Jakarta AKSK `akskClientRestTemplate`，覆盖提交、处理、应用登记、分页查询和附件下载。
- 与 `simple-feedback-client-core:1.0.1` 对齐路径编码、响应校验和异常分类。

## 依赖升级

| 依赖 | 版本 |
|---|---:|
| `simple-feedback-client-core` | 1.0.1 |
| `simple-aksk-resttemplate-redis-client-jakarta-starter` | 1.0.0 |

## 兼容性

- Spring Boot 3.4.2 / Java 17。
- javax 宿主使用 `simple-feedback-aksk-resttemplate-client-starter:1.0.1`。

## 新增测试

- `FeedbackRestTemplateClientValidationTest`：覆盖客户端配置、请求参数和错误分类边界。

## 向后兼容性

- 保持 `FeedbackClient` 1.0.1 方法面；认证仍由宿主提供的 `akskClientRestTemplate` 负责。

## 升级指南

宿主引入本 starter 与 `simple-feedback-client-core:1.0.1`，配置 `akskClientRestTemplate` 和 `base-url`，再注入 `FeedbackClient`。提交失败重试必须沿用原 `Idempotency-Key`。
