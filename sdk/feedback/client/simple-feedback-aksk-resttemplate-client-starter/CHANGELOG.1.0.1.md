# simple-feedback-aksk-resttemplate-client-starter 1.0.1

## 发布信息

- 版本：`1.0.1`
- 类型：Feature / AKSK RestTemplate 传输实现
- 发布日期：2026-10-25

## 类型

Feature

## 变更内容

- 新增基于 AKSK `RestTemplate` 的 Feedback OpenAPI 1.0.1 认证传输实现。
- 覆盖反馈提交、分页查询、处理、附件下载、提交提示和应用注册表操作。
- 统一 4xx、429、5xx、网络异常和响应结构错误的客户端异常分类。
- 将资源标识编码为单个 URI 路径段，并将非法附件媒体类型统一映射为客户端参数异常。

## 依赖升级

| 依赖 | 版本 |
|---|---:|
| `simple-feedback-client-core` | 1.0.1 |
| `simple-aksk-resttemplate-redis-client-starter` | 3.0.2 |

## 兼容性

- Spring Boot 2.7.9 / Java 8。
- 依赖 `simple-feedback-client-core:1.0.1` 和 AKSK RestTemplate 传输底座 3.0.2。
- Jakarta 宿主使用同版本契约包和 Jakarta RestTemplate starter；Feign 宿主使用对应 Feign starter。

## 新增测试

- `FeedbackRestTemplateClientValidationTest`：覆盖客户端配置、请求参数和错误分类边界。

## 向后兼容性

- 继续提供 `FeedbackClient` 接口，服务端 REST 路径以 OpenAPI 1.0.1 为准。
- 认证仍由宿主提供的 `akskClientRestTemplate` 负责，客户端不接收人员令牌。

## 升级指南

宿主先配置 `akskClientRestTemplate` 和 `base-url`，再注入 `FeedbackClient`。提交重试必须沿用原 `Idempotency-Key`；不得把人员令牌交给客户端。
