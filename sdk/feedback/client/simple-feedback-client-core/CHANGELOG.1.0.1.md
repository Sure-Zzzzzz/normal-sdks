# simple-feedback-client-core 1.0.1

## 发布信息

- 版本：`1.0.1`
- 类型：Feature / Feedback REST 客户端契约
- 发布日期：2026-10-25

## 类型

Feature

## 变更内容

- 新增 Feedback OpenAPI 1.0.1 的 Java 客户端契约接口。
- 新增提交、分页、附件下载、应用注册和错误分类模型。
- 增加提交、分页和提示响应的基础结构校验。
- 注册表响应以不可变快照返回，客户端异常消息与协议状态常量集中维护。

## 依赖升级

| 依赖 | 1.0.1 变化 |
|---|---|
| Spring、HTTP、认证传输 | 无；本模块继续零 Spring、零网络依赖 |

## 兼容性

- Java 8 字节码，零 Spring 运行时依赖。
- 对接 `simple-feedback-server-starter:1.0.1`。
- 本版本不包含 Feign 或 Spring Boot 3/Jakarta 传输实现。

## 新增测试

- `FeedbackClientModelValidationTest`：覆盖客户端模型边界和缺失字段拒绝。
- `FeedbackResponseValidatorContractTest`：覆盖 OpenAPI 顶层响应、分页、附件和注册表结构校验。

## 向后兼容性

- 保留 Java 8 字节码和原有异常基类；新增响应校验只拒绝不符合公开契约的响应。
- 不改变人员令牌与服务主体令牌的边界，传输实现仍由宿主选择。

## 升级指南

调用方先引入本契约包，再引入与宿主运行时匹配的认证传输 starter；提交失败重试时继续复用原 `Idempotency-Key`。
