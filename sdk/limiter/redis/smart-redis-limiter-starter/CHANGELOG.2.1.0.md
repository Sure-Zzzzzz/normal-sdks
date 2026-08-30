# CHANGELOG 2.1.0

发布日期：2026-08-30
类型：Dependency Alignment

## 依赖升级

| 依赖 | 原版本 | 新版本 | 口径变化 |
|---|---|---|---|
| simple-redis-route-starter | 1.1.0 | 1.2.2 | `api` → `implementation` |
| spring-boot-starter-aop | - | - | `api` → `compileOnly` |
| spring-boot-starter-web | - | - | `api` → `compileOnly` |
| spring-boot-starter-data-redis | - | - | 保持 `api` |

## 变更内容

- `simple-redis-route-starter` 从 `1.1.0` 升级到 `1.2.2`，对齐 route 1.2.x 版本线。本模块限流行为、配置契约、Redis Key 语义与事件结构零变化。
- **依赖口径收紧**（按模块自闭环口径，公开 API 面实锤裁定）：
  - route 收为 `implementation`——公开注解与核心接口零暴露 route 类型，`RedisRouteTemplate` 仅在自动配置装配与 executor 实现内部使用；运行时仍随本模块传递。
  - aop、web 收为 `compileOnly`——aspectj 仅内部切面使用，web 类型仅在拦截器注册、异常 advice 与远程策略客户端内部装配；均为按功能自备件。
  - data-redis 保持 `api`——executor 接口签名暴露 `StringRedisTemplate`（`execute(Function<StringRedisTemplate, T>)`），算法扩展点 `getScript()` 返回 `DefaultRedisScript`，属公开编程面；同时 route 对 data-redis 为 `compileOnly`，本模块承担 runtime 传递。
- 测试依赖按 route README 契约补充 `commons-pool2`（testRuntimeOnly）：route 1.2.x 起连接池相关类型由使用方提供，本模块测试启用 route 通路时自带，不影响传递依赖。

## 版本号说明

本次包含编译期依赖可见性变化（route `api` 收紧、aop / web 改 `compileOnly`）与传递行为变化（route 1.2.x 接管模式），超出 patch 的"可无脑升级"语义，按语义化版本递增 minor 至 `2.1.0`；限流公开 API 与配置契约零变化。

## 升级迁移

- 只使用注解 / 拦截器、写 YAML 配置、catch 本模块异常的使用方零动作。
- 编译期直接使用 route API 的使用方需自行声明 `simple-redis-route-starter`（此前经 `api` 传递进编译类路径，现仅运行时传递）。
- 注解模式使用方需自备 `spring-boot-starter-aop`；拦截器模式与远程策略（RestTemplate）使用方需自备 `spring-boot-starter-web`（Web 应用天然自带）。
- 应用若启用 `lettuce.pool.enabled=true`，仍按 `simple-redis-route-starter` README 自行提供 `commons-pool2` 运行时依赖（契约与 2.0.0 一致）。

## 兼容性

- route 1.1.0 → 1.2.2 对本模块使用的 API（`RedisRouteTemplate` 执行通道）完全兼容。
- route 1.2.x 接管语义会传递到本模块使用方：`redis.route.enable=true` 时 route 接管标准 `stringRedisTemplate`、Boot Redis 自动配置让位——存量应用的 `spring.redis.*` 连接配置不再生效，需迁移到 route 数据源配置（本模块自 2.0.0 起即强制 route 通路，限流执行不受影响）。
- 仓库内下游（limiter-audit-listener-starter、aksk-server-starter、iam-server-starter）已核对：均自带 web / data-redis / aop 声明或使用 1.x 版本线，零编译影响。
- Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9 兼容矩阵不变。

## 测试

- Spring Boot `2.7.9`：136 用例，0 失败、0 错误、0 跳过——含 Management 1.0.0 + MySQL + 随机端口 HTTP 快照 + ETag/304 + 真实 Redis Route 的远程策略全链路 E2E；恢复 `build.gradle.2.7.9` 后再次完整回归通过。
- Spring Boot `2.4.5` / `2.3.12.RELEASE` / `2.2.13.RELEASE`：各 136 用例，0 失败、0 错误、各 1 跳过——跳过项为 Management 远程策略 E2E 的设计内版本门控（`@EnabledIfSystemProperty` 仅 2.7.9 启用，Management 1.0.0 官方支持基线为 2.7.9）。
- 测试基于真实 Redis 拓扑：本机 standalone 与 Redis `3.2.12` / `5.0.14` / `7.2.6`（standalone + cluster，docker 矩阵）。
