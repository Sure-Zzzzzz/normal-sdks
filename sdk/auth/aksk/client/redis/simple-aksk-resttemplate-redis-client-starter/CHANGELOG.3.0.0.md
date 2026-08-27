# Changelog - simple-aksk-resttemplate-redis-client-starter 3.0.0

## 变更概述

跟随 `simple-aksk-redis-token-manager` 3.0.0 升级（级联 `simple-aksk-client-core` 3.0.0 与 `smart-cache-starter` 2.1.0）。`AkskRestTemplateInterceptor` 的认证注入逻辑零变更，新增 `DEBUG` 日志埋点提升链路可观测性；测试配置补齐 smart-cache 2.x 要求的 Redis Route 数据源。

## 依赖变更

| 依赖 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-aksk-redis-token-manager` | 2.0.1 | 3.0.0 |

传递依赖随之变化：`simple-aksk-client-core` 2.0.0 → 3.0.0（token-manager `api` 自动传递）；`smart-cache-starter` 1.1.2 → 2.1.0（token-manager `implementation`，运行时仍自动传递、开箱即用不变，直接使用 smart-cache API 需自行引入）。

## 行为说明

- 拦截器对外认证行为不变：自动从 `TokenManager` 获取 Token 并设置 `Authorization: Bearer {token}` 请求头；Token 为空时仍记录警告并放行（不加头）；调用方已有 Authorization 头时照旧覆盖。
- 新增 `DEBUG` 埋点：请求入口（HTTP 方法 + URI）、加头结果（Token 长度、是否覆盖调用方已有头，不记录 Token 值）、请求完成（URI + 响应状态码）。
- Token 的获取、缓存、预刷新与多租户隔离行为由 token-manager 3.0.0 决定（严格 Token 响应校验、L2 TTL 按服务端 `expires_in` 计算等），详见 [simple-aksk-redis-token-manager CHANGELOG.3.0.0.md](../simple-aksk-redis-token-manager/CHANGELOG.3.0.0.md)。

## 配置变更（smart-cache 2.x 适配）

- 开启 L2 缓存时必须提供 `io.github.surezzzzzz.sdk.redis.route` 数据源（RedisRouteTemplate），仅配置 `spring.redis.*` 将导致上下文启动失败。
- Redis 连接统一由 Redis Route 数据源自闭环管理，`spring.redis.*` 不再需要（`spring-boot-starter-data-redis` 依赖仍需自行引入，见 README"必需依赖"）。
- 分布式锁需同步开启 `io.github.surezzzzzz.sdk.lock.redis.route.enable: true`。
- `me` 为必配项（应用组标识）：同一应用的多个实例必须配置相同值，共享缓存 / 锁互斥 / Pub/Sub。
- 完整配置示例见 [README.md](README.md)，已按 3.0.0 更新。

## 兼容性

- 自动配置、Bean（`akskClientRestTemplate`、`AkskRestTemplateInterceptor`）、配置前缀均不变，业务代码无需修改。
- 升级时按 README 补充 Redis Route 数据源与 `lock.redis.route` 配置；Spring Web、Spring Data Redis、HttpClient 等运行时依赖仍由使用方自行引入。
- 建议使用 Spring Boot 2.7.x；2.2.x ~ 2.7.x 均经真实 E2E 验证。

## 测试

- Spring Boot 2.7.9 / 2.4.5 / 2.3.12.RELEASE / 2.2.13.RELEASE 四版本矩阵，每版本 17 个测试全绿（真实 Redis + AKSK Server E2E，含 `/api/token/statistics` 管理接口真实调用），命令与记录见 [LOCAL_TEST_COMMANDS.md](LOCAL_TEST_COMMANDS.md)。
- 测试配置修复：补齐 Redis Route 数据源、`lock.redis.route` 与 `cache.route.scan-enabled`（`clearToken()` 测试隔离依赖扫删 L2），Server 端口示例修正为 8280。
