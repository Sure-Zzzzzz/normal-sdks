# CHANGELOG - simple-aksk-server-starter 3.1.0

## 发布日期

2026-09-01

## 版本类型

Minor Release - Management API 授权重构（/api 鉴权链移交公共资源层）+ 中间件依赖对齐 route 1.2.2 版本线（cache 2.2.0 / limiter 2.1.0）+ 依赖口径收紧 + mysql-route 接管

## 变更概述

本版本包含三组变更：

1. **Management API 授权重构**：`/api/**` 管理端点鉴权从 server 内嵌授权链移交 `simple-resource-server-starter` 1.1.1 公共资源层，删除内嵌的 `ManagementApiAuthorizationInterceptor` / `ManagementApiAuthorizationHelper` / `ManagementApiAuthorizationConfiguration` / `ManagementApiAuthorizationConstant` 四件套与 `@Order(2)` 的 `/api/**` SecurityFilterChain；新增 `JweResourceAuthenticationAdapter` 供资源层验证 server 签发的 JWE Token，`AdminSecurityConfiguration` 不再自建 JwtDecoder；新增 `CrossResourceDataPlanHelper` 保留端点内跨资源连带操作的双层鉴权语义（对齐 3.0.x `requiredPlan`）。README 新增 iam-resource 外插接入教程（部署方可选让同一 `/api/**` 接受 IAM 人员 Token）。
2. **启动期 fail-fast 校验**：新增 `SimpleAkskServerStartupValidator`——JWT keyId 不得携带 `aksk/` 等路由前缀；`io.github.surezzzzzz.sdk.auth.resource.server.enabled` 不得为 false（否则 `/api` 裸奔）；protected-paths 必须包含 `/api/**`。配套 `AkskApiProtectedPathsEnvironmentPostProcessor` 自动补全 protected-paths 缺省值。
3. **中间件依赖对齐与口径**：`smart-cache-starter` 升级 2.2.0、`smart-redis-limiter-starter` 升级 2.1.0（均对齐 route 1.2.2，配套适配见详情）；依赖口径按自闭环原则收紧（见详情）；测试配置 lock 开关移除改验 1.2.2 让位语义；MySQL 连接切换为 `simple-mysql-route-starter` 1.1.1 接管（提供方自用自身组件）。

## 变更详情

### 重构：Management API 授权移交公共资源层

- `/api/**` 鉴权由资源层链（HIGHEST_PRECEDENCE）接管：kid 路由、端点注解声明的 API permission 评估与 DATA grant 计划由 `simple-resource-server-starter` 1.1.1（`implementation` 传递，使用方无需自加依赖）与 `simple-data-permission-spring-mvc-starter` 1.0.1（DATA 权限 MVC 集成）承载。
- 端点内跨资源连带操作（删 Client 时撤销其名下 Token、替换/撤销应用授权时撤销 Token）由 server 本地 `CrossResourceDataPlanHelper` 补双层评估：先评估跨资源动作的 API 权限（如 `token:update`），非 ALLOW 即 403；再经 facade 取数据计划。语义对齐 3.0.x `ManagementApiAuthorizationHelper.requiredPlan`。
- 删除 server 内嵌授权四件套与 `/api/**` SecurityFilterChain；`OAuth2SecurityConfiguration` 仅保留授权服务器链与 default 链。
- `JweResourceAuthenticationAdapter`（resourceserver 包）将 JWE 解码适配为资源层认证，原 `OAuth2SecurityConfiguration` 的 JwtDecoder bean 移除。
- `/api` 鉴权新增主体一致性校验（3.0.x 内嵌链无此校验）：token 的 `client_id` claim 必须与应用授权 claim 的 `subjectId` 一致且 `subjectType` 为 SERVICE，不一致即 401。防跨主体伪造，属行为收紧；server 自身签发的 token 主体天然一致，正常接入不受影响。
- 依赖 `simple-resource-server-starter` 1.1.1：公共 `/api` 链固定 `SessionCreationPolicy.STATELESS`，管理 API 响应零 `Set-Cookie` 下发（消除 cookie 与 Bearer 并存的载体歧义）。
- DATA 权限过滤进入管理端点：`TokenManagementService.queryRedisTokens` 等签名增加 plan 重载，REST 查询按调用方授权计划过滤。

### 新增：启动期 fail-fast 校验

- `SimpleAkskServerStartupValidator` 在启动期校验三项配置，失败即拒绝启动：keyId 含路由前缀或非法字符（签发侧自动包装 `aksk/` 前缀，配置值不得重复携带）；资源层被显式关闭；protected-paths 摘掉 `/api/**`。
- `AkskApiProtectedPathsEnvironmentPostProcessor` 保证 protected-paths 缺省含 `/api/**`，显式配置缺失时自动补全。

### 升级：smart-cache-starter 2.2.0 与 smart-redis-limiter-starter 2.1.0

- 两者对齐 `simple-redis-route-starter` 1.2.2 版本线（server 自身 route 坐标已是 1.2.2）。
- cache 2.x 适配：`RedisTokenRepository` 由 `@Qualifier("smartCacheRedisTemplate") RedisTemplate<String, Object>` 切换为 route 接管的标准 `StringRedisTemplate`；token 数据读取兼容三种历史格式（1.x Java 序列化 / 1.x DefaultTyping JSON / 2.x Jackson 信封），Redis 既有数据无需迁移。
- limiter 2.x 适配：`AkskServerOAuth2LimiterFilter` 规则转换适配 Long 化 count/window 与自有时间粒度枚举（毫秒以下粒度 fail-fast）；限流事件统一走 `buildEventPayload` 构造器，limitKey 由算法执行结果的 routeKey 携带，不再手工拼装。
- 限流行为、配置契约（`io.github.surezzzzzz.sdk.limiter.redis.smart.*`）零变化。

### 调整：lock 开关移除，改验让位语义

- 测试 yaml 删除 `lock.redis.route.enable: true` 及 2.1.0 时代的双候选注释；lock 1.2.2 起容器存在 route 接管的标准 `stringRedisTemplate` 时不再自建同名模板，无需显式开关。

### 调整：依赖口径收紧（自闭环原则）

- `smart-cache-starter` / `smart-redis-limiter-starter`：`api` → `implementation`。`SmartCacheManager` 与 limiter 算法 / KeyProvider 仅由 server 自带组件内部消费，公开 API 零暴露；运行时随本模块传递，自动配置与 `cache.*` / `limiter.*` 配置键照常生效。
- `spring-boot-starter-aop`：`api` → `runtimeOnly`。aop 在 cache 2.2.0 / limiter 2.1.0 中均为 `compileOnly`（使用方自备件），server 以 runtimeOnly 供注解模式切面运行时织入；server 源码零 AOP 类型引用。
- `caffeine`：`api` → `runtimeOnly`。供 smart-cache L1 运行时生效，server 源码零 caffeine 类型引用。
- `jackson-datatype-jsr310`：新增 `implementation`。`OAuth2SettingsHelper` 直接 import `JavaTimeModule` 注册 JSON 时间模块，按依赖口径自闭环原则显式声明直接依赖（cache 2.2.0 虽有 jsr310 传递链，不依赖其传递）。
- `task-retry-starter`：删除显式声明。server 源码零直接调用；cache 2.2.0 以 `implementation` 声明 task-retry 2.0.0，运行时传递有效，版本随 cache 配套演进。

### 调整：MySQL 连接切换为 mysql-route 接管

- 测试 yaml 移除 `spring.datasource.*` 直连配置，新增 `io.github.surezzzzzz.sdk.mysql.route` 段（`enable: true`、`primary-datasource: default`、`datasources.default` 连接与 Hikari 池参数），与 iam-audit-listener / iam-adapter 系列配置形态一致。
- 接管机制：mysql-route 1.1.1 以 `@AutoConfigureBefore(DataSourceAutoConfiguration)` 注册 `@Primary` routing DataSource，JPA / 事务管理器注入点不变，零代码改动。
- `application-local.yml.example` 与本地凭据文件的数据库密码键同步迁移到 `mysql.route.datasources.default.password`。

## 兼容性

- `/api/**` 鉴权架构变化：资源层必须启用（`io.github.surezzzzzz.sdk.auth.resource.server.enabled` 不得为 false，启动校验拦截）；protected-paths 缺省自动补全 `/api/**`，显式配置不得摘除。REST 管理端点对外契约（路径、请求响应体）零变化。
- 内嵌授权四件套（`ManagementApiAuthorization*`）删除：此类为内部装配件，正常接入不直接引用；如使用方有直接引用需迁移到资源层链路。
- route 1.2.x 接管语义传递：`redis.route.enable=true` 时 route 接管标准 `stringRedisTemplate`、Boot Redis 自动配置让位——使用方的 `spring.redis.*` 连接配置不再生效，连接契约统一在 route 数据源配置。
- 测试配置 MySQL 连接同步切 mysql-route 接管：`spring.datasource.*` 不再配置，连接契约在 `mysql.route.datasources.*`（模块自带依赖为 `implementation`，使用方启用接管时无需自加依赖；保持 `spring.datasource.*` 直连亦不受影响——mysql-route `enable` 默认关）。
- 使用方编译期影响：直接注入 `SmartCacheManager`、使用 limiter 注解与算法 API、或直接使用 AOP / caffeine / task-retry 类型的使用方，需自行声明对应依赖；仅使用 server 自动配置与 yml 配置键的接入方式零变化，运行时行为与自动装配零变化。
- server 对外 API、配置键、introspection / token / admin 行为零变化（除 /api 鉴权架构项与主体一致性收紧，见上）。
- 本模块运行时仅支持 Spring Boot 2.7.x（既定硬性要求，无变化）。

## 测试

- 2.7.9 单档全量（server 硬性 SB 2.7 基线）：201 个用例全部通过（0 skipped / 0 failures / 0 errors，33 个测试类），测试配置固定 mysql-route / redis-route 接管形态。
- 覆盖面：资源层接管 `/api` 后的管理端点鉴权、跨资源连带操作双层鉴权与主体一致性收紧（缺 `token:update` API 权限删 Client 拒绝、伪造 `client_id` 主体的 token 401）、IAM Provider 共存（`IamProviderCoexistenceTest`）、启动 fail-fast（`StartupValidationFailFastTest`）、mysql-route 接管（`@Primary` routing DataSource + JPA 全链路）、redis-route 接管（cache L1/L2/PubSub、limiter 算法通道）、lock 1.2.2 让位语义（无显式开关）、OAuth2 签发 / introspect / 撤销全链路、Admin 鉴权链。
- 测试环境：MySQL 127.0.0.1:3306（`sure_auth_aksk`）+ Redis 127.0.0.1:6379；Gradle 8.5 + Zulu 11。
