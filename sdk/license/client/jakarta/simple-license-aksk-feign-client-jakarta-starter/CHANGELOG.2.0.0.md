# simple-license-aksk-feign-client-jakarta-starter 2.0.0

## 类型

Feature / AKSK Feign Jakarta 传输实现

## 变更内容

- 新增基于 AKSK Jakarta Feign 的 License Server HTTP 传输实现。
- 将 `LicenseServerClient` 的在线判定、候选授权发现、额度预占/续期/释放/扣减和信任资料同步接到 `/api/license/v1` 公开契约。
- 复用官方 `akskFeignRequestInterceptor` 注入认证信息；宿主可用自定义 `LicenseServerClient` 覆盖默认 Bean。
- 默认关闭 Feign 自动重试，避免额度命令在网络故障后被隐式重复提交。
- 对请求和服务端响应执行完整关联校验；认证、网络和协议错误保持异常语义，不改写为业务拒绝。

## 依赖

| 依赖 | 版本 |
| --- | ---: |
| `simple-license-client-core` | `2.0.1` |
| `simple-aksk-feign-redis-client-jakarta-starter` | `1.0.0` |

## 兼容性

- Spring Boot `3.4.2` / Java 17+。
- 传输件不自建认证、连接池或服务端领域实现；宿主需要提供 AKSK Jakarta `akskFeignRequestInterceptor` Bean 及 Feign 所需的 Spring 编解码器。
- javax 宿主使用同构 javax Feign 传输件。

## 新增测试

- `JakartaFeignClientTest`：覆盖默认装配、官方认证拦截器和异常传播。
- `FeignAssemblyTest`：覆盖 Feign 契约方法及请求装配。
- `ClientHttpContractTest`：覆盖真实 HTTP 回环、分页、续期、释放、严格整数和请求契约。

## 升级指南

宿主引入本模块 `2.0.0` 与 `simple-license-client-core:2.0.1`，配置 License Server `base-url` 并启用 Feign 客户端；认证凭据、拦截器和 Feign 基础设施由宿主现有 AKSK Jakarta 客户端生态提供。
