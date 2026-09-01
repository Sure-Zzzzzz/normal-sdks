# Simple AKSK RestTemplate Redis Client Starter

> **2.x 已封版**：2.x 文档冻结快照见 [README.2.x.md](README.2.x.md)；本文档对应 **3.0.1**。

[![Version](https://img.shields.io/badge/version-3.0.1-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

基于 RestTemplate 的 AKSK 客户端 Starter，集成 Redis Token Manager，提供开箱即用的 HTTP 客户端和灵活的组件选择。

## 核心能力

### 1. 自动配置的 RestTemplate

- **akskClientRestTemplate** - 预配置的 RestTemplate Bean
  - 自动注入 `AkskRestTemplateInterceptor` 拦截器
  - 自动添加 `Authorization: Bearer {token}` 请求头
  - 开箱即用，无需手动配置

### 2. AKSK 认证拦截器

- **AkskRestTemplateInterceptor** - RestTemplate 拦截器
  - 实现 `ClientHttpRequestInterceptor` 接口
  - 自动从 `TokenManager` 获取 Token
  - 自动添加 `Authorization` 请求头
  - 可独立使用，添加到自定义 RestTemplate

### 3. Redis Token 管理

- **RedisTokenManager** - 基于 Redis 的 Token 管理器
  - 继承自 `simple-aksk-redis-token-manager`
  - 支持分布式 Token 缓存
  - 支持并发控制和自动刷新
  - 可独立使用，不依赖 RestTemplate

### 4. 灵活的组件选择

用户可以根据需要选择使用：

| 组件 | 使用场景 | 注入方式 |
|------|---------|---------|
| **TokenManager** | 只需要 Token 管理，自己处理 HTTP 请求 | `@Autowired private TokenManager tokenManager;` |
| **akskClientRestTemplate** | 需要自动添加认证头的 RestTemplate | `@Autowired @Qualifier("akskClientRestTemplate") private RestTemplate restTemplate;` |
| **AkskRestTemplateInterceptor** | 需要自定义 RestTemplate 配置 | `@Autowired private AkskRestTemplateInterceptor interceptor;` |

### 5. 自动配置

- **SimpleAkskRestTemplateRedisClientAutoConfiguration** - 自动配置类
  - 条件装配：
    - `@ConditionalOnClass` - RestTemplate 类存在
    - `@ConditionalOnBean` - TokenManager Bean 存在
    - `@ConditionalOnProperty` - `enable=true`
  - 自动注册 Bean：
    - AkskRestTemplateInterceptor（通过组件扫描）
    - akskClientRestTemplate（需额外配置 `resttemplate.enable=true`）

## 依赖说明

| 依赖 | 传递方式 | 说明 |
|------|---------|------|
| `simple-aksk-redis-token-manager:3.0.1` | `implementation` 运行时传递 | Token 管理（`TokenManager` Bean），级联 `simple-aksk-client-core:3.0.0`；直接使用 `TokenManager` / client-core API 需自行引入 |
| `smart-cache-starter:2.2.0` | 经 token-manager 运行时传递 | L1+L2 缓存、分布式锁、Pub/Sub，开箱即用；直接使用 smart-cache API 需自行引入 |
| Spring Boot / Spring Web | `compileOnly`，**使用方必须自行引入** | RestTemplate 与自动配置 |
| Spring Data Redis | `compileOnly`，**使用方必须自行引入** | Redis 操作 |
| Apache HttpClient | `compileOnly`，推荐自行引入 | HTTP 连接池 |

## 快速开始

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-resttemplate-redis-client-starter:3.0.1'
}
```

**重要说明**：核心依赖 `simple-aksk-redis-token-manager:3.0.1`（连带 smart-cache 运行时组件）运行时自动传递、开箱即用；自 3.0.1 起其声明方式由 `api` 收为 `implementation`，不再向使用方编译期传递——直接使用 `TokenManager` / client-core 类型的代码请自行引入对应坐标。Spring 相关依赖使用 `compileOnly` 声明、不会传递，请根据您的 Spring Boot 版本自行引入以下依赖：

**必需依赖：**

```gradle
dependencies {
    // Spring Web（RestTemplate）
    implementation 'org.springframework.boot:spring-boot-starter-web'

    // Spring Data Redis（Redis 操作）
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'

    // Apache HttpClient（HTTP 连接池，推荐）
    implementation 'org.apache.httpcomponents:httpclient:4.5.14'
}
```

**版本说明**：
- 建议使用 Spring Boot 2.7.x 版本
- Redis 驱动会通过 spring-boot-starter-data-redis 自动引入

### 2. 配置应用

完整配置（各键作用见行内注释；未注释即默认值形态的键均可按需调整）：

```yaml
# Redis 连接由 Redis Route 数据源自闭环管理（下方 sdk.redis.route），
# 无需配置 spring.redis.*（spring-boot-starter-data-redis 依赖仍需引入，见"必需依赖"）
io:
  github:
    surezzzzzz:
      sdk:
        redis:
          route:
            enable: true                  # Redis Route 接管总开关；smart-cache 2.x 开启 L2 时必须启用（须提供 RedisRouteTemplate）
            default-source: default       # 默认数据源名，指向 sources 下同名条目
            sources:
              default:
                mode: standalone          # 部署模式：standalone 单机 / sentinel 哨兵 / cluster 集群
                host: localhost
                port: 6379
                database: 0
                timeout-ms: 3000          # 命令超时（毫秒）
                connect-timeout-ms: 3000   # 建连超时（毫秒）

        auth:
          aksk:
            client:
              enable: true                # 本 starter 自动配置开关；false 时不注册认证拦截器
              client-id: AKP1234567890abcdefgh           # AKSK Client ID（AKSK Server Admin 页面创建）
              client-secret: SK1234567890abcdefghijklmnopqrstuvwxyz1234   # Client Secret（创建时一次性展示，妥善保存）
              server-url: http://localhost:8280           # AKSK Server 地址
              token-endpoint: /oauth2/token              # token 端点路径（默认值，一般不改）
              redis:
                token:
                  cache-name: aksk-client-token           # token 在 smart-cache 中的缓存名
              resttemplate:
                enable: true              # 启用 akskClientRestTemplate Bean（默认 false；false 时仅注册拦截器，自行组装）
                max-total: 100            # 连接池最大连接数
                max-per-route: 20         # 每个路由的最大连接数
                connect-timeout: 5000     # 连接超时（毫秒）
                read-timeout: 30000       # 读取超时（毫秒）

        cache:                             # smart-cache 配置（token 缓存载体，经 token-manager 生效）
          enabled: true                    # 缓存开关
          key-prefix: sure-auth-aksk-client  # Redis key 前缀（区分业务）
          me: my-app                       # 应用组标识（必配）：同一应用多实例必须一致，共享缓存 / 锁互斥 / Pub/Sub
          l1:
            enabled: true                  # L1 本地缓存（进程内，削 Redis 读压力）
            expire-seconds: 2              # L1 TTL（秒），建议 2~5s（过长会放大服务端撤销延迟）
            max-size: 1000                 # L1 最大条目数
          l2:
            enabled: true                  # L2 Redis 缓存（多实例共享 token）
            expire-seconds: 3600           # L2 默认 TTL；token 实际写回按服务端 expires_in 计算
            preload:
              enabled: true                # 预刷新（提前异步续期，避免请求打到过期 token）
              before-expire-seconds: 60    # 预刷新窗口：Redis TTL <= 此值时触发
          consistency:
            mode: strong                   # 多实例 L1 一致性（Pub/Sub 广播失效），strong / eventual
```

## 使用场景

### 场景 1：直接使用 akskClientRestTemplate（推荐）

最简单的使用方式，开箱即用：

```java
@Service
public class MyService {

    @Autowired
    @Qualifier("akskClientRestTemplate")
    private RestTemplate akskClientRestTemplate;

    public String callApi() {
        // 自动添加 Authorization 头
        String url = "http://localhost:8280/api/resource";
        ResponseEntity<String> response = akskClientRestTemplate.getForEntity(url, String.class);
        return response.getBody();
    }
}
```

**特点**：
- ✅ 自动添加 `Authorization: Bearer {token}` 请求头
- ✅ Token 由 RedisTokenManager 自动管理和刷新
- ✅ 无需手动管理 Token

### 场景 2：只使用 TokenManager

如果你想自己处理 HTTP 请求，只需要 Token 管理：

```java
@Service
public class MyService {

    @Autowired
    private TokenManager tokenManager;

    public String callApi() {
        // 手动获取 Token
        String token = tokenManager.getToken();

        // 使用自己的 HTTP 客户端（OkHttp、HttpClient 等）
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        // 发起请求...
        return "response";
    }
}
```

**特点**：
- ✅ 灵活控制 HTTP 请求
- ✅ 可以使用任何 HTTP 客户端
- ✅ Token 自动缓存和刷新

### 场景 3：自定义 RestTemplate

如果你需要自定义 RestTemplate 配置（超时、连接池等），可以只使用拦截器：

```java
@Configuration
public class RestTemplateConfig {

    @Autowired
    private AkskRestTemplateInterceptor akskInterceptor;

    @Bean
    public RestTemplate myCustomRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();

        // 自定义配置
        restTemplate.setRequestFactory(customRequestFactory());

        // 添加 AKSK 拦截器
        restTemplate.getInterceptors().add(akskInterceptor);

        return restTemplate;
    }

    private ClientHttpRequestFactory customRequestFactory() {
        HttpComponentsClientHttpRequestFactory factory =
            new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return factory;
    }
}
```

**特点**：
- ✅ 完全控制 RestTemplate 配置
- ✅ 复用 AKSK 认证逻辑
- ✅ 可以添加其他拦截器

### 多用户场景

需要实现自定义 `SecurityContextProvider`：

```java
@Component
public class UserSecurityContextProvider implements SecurityContextProvider {

    @Override
    public String getSecurityContext() {
        // 从当前请求获取用户信息
        String userId = getCurrentUserId();
        return String.format("{\"user_id\":\"%s\"}", userId);
    }
}
```

不同用户会获取不同的 Token（基于 SHA-256 截断 hex 隔离，自 token-manager 2.0.1 起，杜绝 hashCode 碰撞串号风险）。

## 拦截器工作原理

### 请求流程

```
用户调用 akskClientRestTemplate
    ↓
AkskRestTemplateInterceptor 拦截请求
    ↓
从 TokenManager 获取 Token
    ↓
添加 Authorization: Bearer {token} 请求头
    ↓
发送请求到服务器
    ↓
返回响应（200、401、500 等）
```

### Token 管理

- **Token 获取**：拦截器从 `TokenManager` 获取 Token
- **Token 缓存**：Token 由 `RedisTokenManager` 缓存在 Redis 中
- **Token 刷新**：`RedisTokenManager` 会在 Token 过期前自动刷新
- **无 Token 处理**：如果 Token 为空，拦截器会记录警告并继续请求（不添加 Authorization 头）
- **调试日志**：`DEBUG` 级别输出三类埋点——请求入口（方法 + URI）、加头结果（Token 长度、是否覆盖调用方已有 Authorization 头，不记录 Token 值）、请求完成（URI + 响应状态码），便于排查请求是否被拦截器覆盖、定位慢请求与失败请求

## 测试覆盖

✅ **拦截器测试**（AkskRestTemplateInterceptorTest）
- 拦截器应该添加 Authorization 请求头
- 当 token 为 null 时应该不添加 Authorization 头
- 当 token 为空字符串时应该不添加 Authorization 头
- 已有 Authorization 头时应该覆盖
- TokenManager 抛异常时应该向上传播
- execution 抛 IOException 时应该向上传播

✅ **集成测试**（RestTemplateIntegrationTest）
- TokenManager Bean 是否存在
- AkskRestTemplateInterceptor Bean 是否存在
- RestTemplate Bean 是否存在
- RestTemplate 是否包含 AkskRestTemplateInterceptor
- 拦截器是否可以访问 TokenManager

✅ **端到端测试**（RestTemplateEndToEndTest）
- 获取 Token 应该成功
- 使用 Token 调用 akskClientRestTemplate 应该成功
- 没有 Token 时应该自动添加 Token
- Token 过期时应该自动刷新
- 无效凭证应该返回 401
- 多次请求应该复用 Token

**总计：17 个测试，100% 通过**

## 常见问题

### Q1: 为什么要使用 @Qualifier("akskClientRestTemplate")？

**A**: 如果你的项目中有多个 RestTemplate Bean，Spring 无法确定注入哪一个。使用 `@Qualifier` 可以明确指定注入 `akskClientRestTemplate`。

```java
// 推荐写法
@Autowired
@Qualifier("akskClientRestTemplate")
private RestTemplate akskClientRestTemplate;

// 如果项目中只有一个 RestTemplate，也可以省略 @Qualifier
@Autowired
private RestTemplate akskClientRestTemplate;
```

### Q2: 可以同时使用多个 RestTemplate 吗？

**A**: 可以。你可以同时使用 `akskClientRestTemplate`（带 AKSK 认证）和其他 RestTemplate（不带认证）。

```java
@Autowired
@Qualifier("akskClientRestTemplate")
private RestTemplate akskRestTemplate;  // 带 AKSK 认证

@Autowired
@Qualifier("myRestTemplate")
private RestTemplate myRestTemplate;  // 不带认证
```

### Q3: 如何禁用自动配置？

**A**: 在配置文件中设置 `enable=false`：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            client:
              enable: false
```

### Q4: 拦截器会影响性能吗？

**A**: 不会。拦截器只在首次请求时从服务器获取 Token，后续请求直接使用缓存的 Token。Redis 缓存的读取性能非常高（微秒级）。

### Q5: 支持 HTTPS 吗？

**A**: 支持。只需要在配置中使用 HTTPS URL：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            client:
              server-url: https://api.example.com
```

## 版本历史

### 3.0.1

- 升级 `simple-aksk-redis-token-manager` 3.0.0 → 3.0.1，级联 `smart-cache-starter` 2.1.0 → 2.2.0（对齐 `simple-redis-route-starter` 1.2.2 版本线）；拦截器认证注入逻辑零变更。
- 依赖声明收紧：token-manager 由 `api` 改为 `implementation`——运行时仍自动传递、开箱即用不变；本模块公开 API 不含 token-manager 特有类型（`TokenManager` 接口来自 client-core），直接使用 `TokenManager` / client-core API 的使用方需自行引入。
- lock 接管开关（`lock.redis.route.enable`）不再需要：lock 1.2.2 起容器存在 route 接管的标准 `stringRedisTemplate` 时自动让位，配置示例已删除该段。
- 公开 API、配置键零变化，业务代码无需修改。

详见 [CHANGELOG.3.0.1.md](CHANGELOG.3.0.1.md)。

### 3.0.0

- 升级 `simple-aksk-redis-token-manager` 2.0.1 → 3.0.0，级联 `simple-aksk-client-core` 3.0.0、`smart-cache-starter` 2.1.0；拦截器认证注入逻辑零变更，业务代码无需修改。
- smart-cache 2.x 适配：Redis 连接改由 `io.github.surezzzzzz.sdk.redis.route` 数据源自闭环管理（开启 L2 时必须提供，RedisRouteTemplate），`spring.redis.*` 不再需要，配置示例已按 3.0.0 更新。
- 拦截器新增 `DEBUG` 埋点（请求入口 / 加头结果 / 请求完成，不记录 Token 值），提升链路可观测性。
- 经 Spring Boot 2.7.9 / 2.4.5 / 2.3.12.RELEASE / 2.2.13.RELEASE 四版本真实 E2E 矩阵验证（每版本 17 个测试全绿）。

详见 [CHANGELOG.3.0.0.md](CHANGELOG.3.0.0.md)。

### 2.0.1 (2026-06-14)

- 升级 simple-aksk-redis-token-manager 2.0.0 → 2.0.1（Security Hardening：cacheKey 由 `hashCode()` 32-bit 升级为 SHA-256 截断 128-bit hex，消除多租户 hashCode 碰撞串号风险）
- API / 配置零变更，业务代码无需修改

### 2.0.0 (2026-05-27)

- 升级 simple-aksk-redis-token-manager 1.1.0 → 2.0.0（JWE 支持、Token 有效性改由 Redis TTL 保证）

### 1.1.0 (2026-05-06)

- 升级 simple-aksk-redis-token-manager 1.0.1 → 1.1.0（L1+L2 两级缓存、Token 预刷新、多实例 L1 一致性）

### 1.0.0 (2026-01-26)

初始版本发布：
- ✅ 实现基于 RestTemplate 的 AKSK 客户端
- ✅ 集成 Redis Token Manager
- ✅ 支持自动添加 Authorization 请求头
- ✅ 提供灵活的组件选择（TokenManager、RestTemplate、Interceptor）
- ✅ 完整的测试覆盖（拦截器、集成、端到端）

## 许可证

Apache License 2.0
