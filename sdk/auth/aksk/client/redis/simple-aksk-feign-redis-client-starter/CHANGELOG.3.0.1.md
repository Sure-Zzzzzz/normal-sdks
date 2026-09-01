# Changelog - simple-aksk-feign-redis-client-starter 3.0.1

## 变更概述

跟随 `simple-aksk-redis-token-manager` 3.0.1 升级（其内部对齐 `smart-cache-starter` 2.2.0 / `simple-redis-route-starter` 1.2.2 版本线），移除 lock 接管开关。本模块源码零改动，属纯坐标升级的 Patch Release。

## 依赖变更

| 依赖 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-aksk-redis-token-manager` | 3.0.0 | 3.0.1（声明方式 `api` → `implementation`） |

级联变化（经 token-manager 运行时传递）：`smart-cache-starter` 2.1.0 → 2.2.0；`simple-redis-lock-starter` 1.2.2、`task-retry-starter` 2.0.0 随 token-manager 显式声明并运行时传递。

## 行为说明

- Feign 拦截器认证注入逻辑、自动配置装配零变更。
- lock 接管开关（`lock.redis.route.enable`）不再需要：lock 1.2.2 起容器存在 route 接管的标准 `stringRedisTemplate` 时自动让位复用；配置残留不报错，仅失效。测试 yaml 已删除该段。

## 兼容性

- 公开 API、配置键（`auth.aksk.client.*` / `cache.*` / `feign` 相关）零变化，业务代码无需修改。
- 使用方必须启用 `io.github.surezzzzzz.sdk.redis.route` 接管：cache 2.2.0 启动校验要求 L2 / strong 配置下容器存在 `RedisRouteTemplate`，`spring.redis.*` 直连形态启动失败（继承 token-manager 3.0.1 约束，3.0.0 起 yaml 模板已是 route 形态）。
- 依赖声明收紧：token-manager 由 `api` 改为 `implementation`——运行时仍自动传递、开箱即用不变；本模块公开 API 不含 token-manager 特有类型（`TokenManager` 接口来自 client-core），直接使用 `TokenManager` / client-core API 的使用方需自行引入对应坐标。

## 测试

- 四版本矩阵（SDK starter 基线）：Spring Boot 2.7.9 / 2.4.5 / 2.3.12.RELEASE / 2.2.13.RELEASE，每版本 `cleanTest test --rerun-tasks` 全量重跑，3 个测试类 14 个用例全部通过（0 skipped / 0 failures / 0 errors），已核对 JUnit XML 计数；含 FeignClient 真实调用 AKSK Server `/api/token/statistics` 的 E2E（注解式 + 显式配置两种客户端）。
- E2E 针对 AKSK Server 3.1.0（`/api` 已移交公共资源层鉴权）验证通过：token 签发、拦截器自动加头、缓存复用与过期刷新全链路。
- openfeign / feign-httpclient 解析版本随 Spring Boot 版本联动（3.1.8/11.10、3.0.3/10.12、2.2.9.RELEASE/10.12 ×2）。
- 测试环境：真实 AKSK Server `127.0.0.1:8280` + Redis 6379 db 0；Gradle 8.5 + Zulu 11（2.7.9）/ Gradle 7.6 + JDK 8（低三档）。
