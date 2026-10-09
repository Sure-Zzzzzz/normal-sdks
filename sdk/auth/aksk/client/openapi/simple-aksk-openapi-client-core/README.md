# Simple AKSK OpenAPI Client Core

AKSK Server 管理 OpenAPI 客户端家族的契约与编程式内核：路径/参数常量、wire DTO、
`AkskOpenApiClient` 编程式接口（20 端点全量）、按 HTTP 语义划分的异常族与
UriHelper / HttpErrorMapper / JsonCodec 支撑件。

本模块是调用侧契约，不校验入站请求、不管理令牌、不认知 IAM；零 Spring、零 HTTP 客户端依赖
（jackson compileOnly，POM 零依赖），javax（SB2/JDK8）与 jakarta（SB3/JDK17+）双宿主通用。
传输与认证由四形态 starter 装配：

| 应用运行时 | Feign 形态 | RestTemplate 形态 |
| --- | --- | --- |
| Spring Boot 2.x（javax） | `simple-aksk-openapi-feign-client-starter:1.0.0` | `simple-aksk-openapi-resttemplate-client-starter:1.0.0` |
| Spring Boot 3.x（jakarta） | `simple-aksk-openapi-feign-client-jakarta-starter:1.0.0` | `simple-aksk-openapi-resttemplate-client-jakarta-starter:1.0.0` |

对接 `simple-aksk-server-starter:3.2.2` 的管理面 `/api/**` 契约（3.1.0 起公共资源层鉴权，
本身就是 AKSK 保护的 OpenAPI——客户端用 AKP 调 AKSK Server 自己的管理 API）。

## 版本选择

| 应用运行时 | Feign 形态 | RestTemplate 形态 |
| --- | --- | --- |
| Spring Boot 3.x（jakarta） | `simple-aksk-openapi-feign-client-jakarta-starter:1.0.0` | `simple-aksk-openapi-resttemplate-client-jakarta-starter:1.0.0` |
| Spring Boot 2.x（javax） | `simple-aksk-openapi-feign-client-starter:1.0.0` | `simple-aksk-openapi-resttemplate-client-starter:1.0.0` |

core 模块由四形态 starter 传递引入，宿主不直接依赖；javax 与 jakarta 两条线同包名同类型，不可同时引入。

## 解决的问题

平台接入自动化（建 Client → 配应用授权 → 准入 → 换 Token 验证）此前只能走 Admin 管理台或手拼
curl；本家族把它变成可编程调用，适用于运维脚本化、多环境批量初始化与接入流水线。

## 20 端点契约映射

| 分组 | 方法（AkskOpenApiClient） | HTTP |
| --- | --- | --- |
| Client 管理 | createClient / listClients / getClient / updateClient / rotateSecret / deleteClient / syncUserScopes | POST·GET·GET·PATCH·PUT·DELETE·PATCH `/api/client**` |
| 应用授权 | createAuthorization / listAuthorizations / getAuthorization / replaceAuthorization / revokeAuthorization | POST·GET·GET·PUT·POST `/api/application-authorization**` |
| Token 管理 | listTokens / listRedisTokens / getToken / revokeToken / deleteToken / deleteExpiredTokens / getTokenStatistics / revokeTokensByClientId | GET·GET·GET·POST·DELETE·DELETE·GET·DELETE `/api/token**` |

`replaceAuthorization` 完整替换会事务性撤销该 Client 全部活跃 Token；`admitted=true` 即准入
（不勾不换得出 Token）。

## 异常族

根异常 `SimpleAkskOpenApiClientException` 只携带 status/method/endpoint/requestId/timestamp
等非敏感诊断元数据；子类按 HTTP 语义划分（400 BadRequest / 401 Unauthenticated / 403
Unauthorized / 404 NotFound / 409 Conflict / 413 PayloadTooLarge / 422 Unprocessable /
503 ServiceUnavailable / Transport / Protocol / ResponseTooLarge / ClientConfiguration）。
RestTemplate 形态经 `AkskOpenApiHttpErrorMapper` 映射；Feign 形态维持裸 FeignException。

## Secret 保密纪律

`createClient` / `rotateSecret` 响应中的 `clientSecret` 仅出现一次，调用方必须立即落受保护配置；
本模块及各形态 starter 的日志与异常一律不输出 Secret、Authorization、Token、完整 URL query。

## 版本
### 1.0.1（2026-10-09，待发布）

契约补齐：`ApplicationAuthorizationResponse` 补 `dataGrantDocument`；`listClients` 批量形态独立为
`listClientsByClientIds`（`BatchClientResponse` 信封），`ListClientsQuery` 收窄为分页字段。
接口 20 → 21 方法（HTTP 端点仍 20）。详见 [CHANGELOG.1.0.1.md](CHANGELOG.1.0.1.md)。

### 1.0.0（2026-10-08）

首发：20 端点契约内核（常量/DTO/接口/异常族/支撑件），javax 与 jakarta 双宿主通用。

