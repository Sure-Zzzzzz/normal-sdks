# simple-license-aksk-resttemplate-client-jakarta-starter 2.0.0

## 类型

Feature / AKSK RestTemplate Jakarta 传输实现

## 变更内容

- 新增基于 AKSK Jakarta `RestTemplate` 的 License Server HTTP 传输实现。
- 将 `LicenseServerClient` 的在线判定、候选授权发现、额度预占/续期/释放/扣减和信任资料同步接到 `/api/license/v1` 公开契约。
- 固定复用宿主提供的 `akskClientRestTemplate`，由 AKSK 通道负责认证注入、连接治理和超时；宿主可用自定义 `LicenseServerClient` 覆盖默认 Bean。
- 对请求和服务端响应执行完整关联校验；认证、网络和协议错误保持异常语义，不改写为业务拒绝。

## 依赖

| 依赖 | 版本 |
| --- | ---: |
| `simple-license-client-core` | `2.0.1` |
| `simple-aksk-resttemplate-redis-client-jakarta-starter` | `1.0.0` |

## 兼容性

- Spring Boot `3.4.2` / Java 17+。
- 传输件不自建认证、连接池或服务端领域实现；宿主需要提供 AKSK Jakarta `akskClientRestTemplate` Bean。
- javax 宿主使用同构 javax RestTemplate 传输件。

## 新增测试

- `JakartaRestTemplateClientTest`：覆盖在线判定、响应关联校验和异常传播。
- `ClientHttpContractTest`：覆盖真实 HTTP 回环、分页、续期、释放、严格整数和请求契约。

## 升级指南

宿主引入本模块 `2.0.0` 与 `simple-license-client-core:2.0.1`，配置 License Server `base-url` 并启用客户端；认证凭据和 `akskClientRestTemplate` 由宿主现有 AKSK Jakarta 客户端生态提供。
