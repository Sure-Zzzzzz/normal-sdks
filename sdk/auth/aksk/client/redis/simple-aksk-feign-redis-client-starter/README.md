# Simple AKSK Feign Redis Client Starter

> **2.x 已封版**：2.x 文档冻结快照见 [README.2.x.md](README.2.x.md)；本文档对应 **3.0.1**。

[![Version](https://img.shields.io/badge/version-3.0.1-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

基于 Spring Cloud OpenFeign 的 AKSK 客户端 Starter,集成 Redis Token Manager，提供开箱即用的声明式 HTTP 客户端和灵活的组件选择。

## 核心能力

### 1. 声明式 Feign 客户端

- **@AkskClientFeignClient** - 自定义 Feign 注解（推荐）
  - 自动配置 `AkskFeignConfiguration`
  - 自动添加 `Authorization: Bearer {token}` 请求头
  - 开箱即用，无需手动配置

### 2. AKSK 认证拦截器

- **AkskFeignRequestInterceptor** - Feign 请求拦截器
  - 实现 `RequestInterceptor` 接口
  - 自动从 `TokenManager` 获取 Token
  - 自动添加 `Authorization` 请求头
  - 可独立使用，添加到自定义 Feign 配置

### 3. Redis Token 管理

- **RedisTokenManager** - 基于 Redis 的 Token 管理器
  - 继承自 `simple-aksk-redis-token-manager`
  - 支持分布式 Token 缓存
  - 支持并发控制和自动刷新
  - 可独立使用，不依赖 Feign

### 4. 灵活的组件选择

用户可以根据需要选择使用：

| 组件 | 使用场景 | 使用方式 |
|------|---------|---------|
| **TokenManager** | 只需要 Token 管理，自己处理 HTTP 请求 | `@Autowired private TokenManager tokenManager;` |
| **@AkskClientFeignClient** | 需要自动添加认证头的 Feign 客户端（推荐） | `@AkskClientFeignClient(name = "my-service", url = "...")` |
| **AkskFeignConfiguration** | 需要显式配置 Feign 客户端 | `@FeignClient(configuration = AkskFeignConfiguration.class)` |

### 5. 自动配置

- **SimpleAkskFeignRedisClientAutoConfiguration** - 自动配置类
  - 条件装配：
    - `@ConditionalOnClass` - Feign 类存在
    - `@ConditionalOnBean` - TokenManager Bean 存在
    - `@ConditionalOnProperty` - `enable=true`
  - 自动注册 Bean：
    - AkskFeignRequestInterceptor

## 依赖说明

| 依赖 | 传递方式 | 说明 |
|------|---------|------|
| `simple-aksk-redis-token-manager:3.0.1` | `implementation` 运行时传递 | Token 管理（`TokenManager` Bean），级联 `simple-aksk-client-core:3.0.0`；直接使用 `TokenManager` / client-core API 需自行引入 |
| `smart-cache-starter:2.2.0` | 经 token-manager 运行时传递 | L1+L2 缓存、分布式锁、Pub/Sub，开箱即用；直接使用 smart-cache API 需自行引入 |
| Spring Cloud OpenFeign | `compileOnly`，**使用方必须自行引入** | 声明式 HTTP 客户端 |
| Spring Boot / Spring Data Redis | `compileOnly`，**使用方必须自行引入** | 自动配置与 Redis 操作 |
| feign-httpclient | 推荐自行引入 | HTTP 连接池 |

## 快速开始

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-feign-redis-client-starter:3.0.1'
}
```

**重要说明**：核心依赖 `simple-aksk-redis-token-manager:3.0.1`（连带 smart-cache 运行时组件）运行时自动传递、开箱即用；自 3.0.1 起其声明方式由 `api` 收为 `implementation`，不再向使用方编译期传递——直接使用 `TokenManager` / client-core 类型的代码请自行引入对应坐标。Spring Cloud OpenFeign 等使用 `compileOnly` 声明、不会传递，请根据您的 Spring Boot 版本自行引入以下依赖：

**必需依赖：**

```gradle
dependencies {
    // Spring Cloud OpenFeign（声明式 HTTP 客户端）
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign:3.1.8'

    // Spring Data Redis（Redis 操作）
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'

    // Feign HttpClient（HTTP 连接池，推荐）
    implementation 'io.github.openfeign:feign-httpclient:11.10'
}
```

**版本对应关系：**

| Spring Boot 版本 | Spring Cloud OpenFeign | Feign HttpClient | 说明 |
|-----------------|----------------------|------------------|------|
| 2.7.x | 3.1.8 | 11.10 | 推荐，测试通过 |
| 2.4.x - 2.6.x | 3.0.3 | 10.12 | 兼容 |
| 2.2.x - 2.3.x | 2.2.9.RELEASE | 10.12 | 兼容 |

**版本说明**：
- 本 starter 使用 `compileOnly` 声明依赖，不会强制版本
- 运行时使用您项目中引入的版本
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

### 3. 启用 Feign 客户端

在启动类上添加 `@EnableFeignClients` 注解：

```java
@SpringBootApplication
@EnableFeignClients
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

## 使用场景

### 场景 1：使用 @AkskClientFeignClient 注解（推荐）

最简单的使用方式，开箱即用：

```java
@AkskClientFeignClient(name = "my-service", url = "http://localhost:8280")
public interface MyServiceClient {

    @GetMapping("/api/resource")
    String getResource();

    @PostMapping("/api/data")
    String postData(@RequestBody DataRequest request);
}
```

在业务代码中使用：

```java
@Service
public class MyService {

    @Autowired
    private MyServiceClient myServiceClient;

    public String callApi() {
        // 自动添加 Authorization 头
        String response = myServiceClient.getResource();
        return response;
    }
}
```

**特点**：
- ✅ 自动添加 `Authorization: Bearer {token}` 请求头
- ✅ Token 由 RedisTokenManager 自动管理和刷新
- ✅ 无需手动管理 Token
- ✅ 声明式编程，代码简洁

### 场景 2：显式配置 Feign 客户端

如果你需要更多控制，可以使用原始 `@FeignClient` 注解并显式配置：

```java
@FeignClient(
    name = "my-service",
    url = "http://localhost:8280",
    configuration = AkskFeignConfiguration.class
)
public interface MyServiceClient {

    @GetMapping("/api/resource")
    String getResource();
}
```

**特点**：
- ✅ 完全控制 Feign 配置
- ✅ 可以添加其他配置类
- ✅ 复用 AKSK 认证逻辑

### 场景 3：只使用 TokenManager

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
用户调用 FeignClient 方法
    ↓
AkskFeignRequestInterceptor 拦截请求
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
- **调试日志**：`DEBUG` 级别输出两类埋点——请求入口（HTTP 方法 + 目标地址）、加头结果（Token 长度、是否覆盖调用方已有 Authorization 头，不记录 Token 值）。Feign 的 `RequestInterceptor` 只参与请求构建、拿不到响应，因此没有"请求完成"段埋点

## 测试覆盖

✅ **拦截器测试**（AkskFeignRequestInterceptorTest）
- 拦截器应该添加 Authorization 请求头
- 当 token 为 null 时应该不添加 Authorization 头
- 当 token 为空字符串时应该不添加 Authorization 头
- TokenManager 抛异常时应该向上传播
- 已有 Authorization 头时应该覆盖（不重复追加）

✅ **集成测试**（FeignIntegrationTest）
- TokenManager Bean 是否存在
- AkskFeignRequestInterceptor Bean 是否存在
- 拦截器是否可以访问 TokenManager

✅ **端到端测试**（FeignEndToEndTest）
- TokenManager 应该存在
- TestFeignClient 应该存在
- 获取 Token 应该成功
- 使用 Token 调用 FeignClient 应该成功
- ExplicitConfigFeignClient 应该存在
- 使用显式配置的 FeignClient 调用应该成功

**总计：14 个测试，100% 通过**

## 常见问题

### Q1: @AkskClientFeignClient 和 @FeignClient 有什么区别？

**A**: `@AkskClientFeignClient` 是对 `@FeignClient` 的封装，自动配置了 `AkskFeignConfiguration`。

```java
// 使用 @AkskClientFeignClient（推荐）
@AkskClientFeignClient(name = "my-service", url = "http://localhost:8280")
public interface MyServiceClient {
    // ...
}

// 等价于
@FeignClient(
    name = "my-service",
    url = "http://localhost:8280",
    configuration = AkskFeignConfiguration.class
)
public interface MyServiceClient {
    // ...
}
```

### Q2: 可以同时使用多个 Feign 客户端吗？

**A**: 可以。你可以同时使用多个 Feign 客户端，每个客户端都会自动添加 AKSK 认证。

```java
@AkskClientFeignClient(name = "service-a", url = "http://service-a:8080")
public interface ServiceAClient {
    @GetMapping("/api/resource")
    String getResource();
}

@AkskClientFeignClient(name = "service-b", url = "http://service-b:8080")
public interface ServiceBClient {
    @GetMapping("/api/data")
    String getData();
}
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

### Q6: 如何配置 Feign 的超时时间？

**A**: 可以在配置文件中配置 Feign 的超时时间：

```yaml
feign:
  client:
    config:
      default:
        connectTimeout: 5000  # 连接超时（毫秒）
        readTimeout: 10000    # 读取超时（毫秒）
```

### Q7: 如何配置 HTTP 连接池？

**A**: 引入 `feign-httpclient` 依赖后，可以配置 Apache HttpClient 连接池：

```yaml
feign:
  httpclient:
    enabled: true
    max-connections: 200  # 最大连接数
    max-connections-per-route: 50  # 每个路由的最大连接数
```

## 与 RestTemplate 版本的对比

| 特性 | Feign 版本 | RestTemplate 版本 |
|------|-----------|------------------|
| 编程风格 | 声明式（接口） | 命令式（方法调用） |
| 代码简洁度 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| 学习曲线 | 平缓 | 平缓 |
| 灵活性 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 适用场景 | 微服务间调用 | 通用 HTTP 调用 |
| 推荐度 | 推荐 | 推荐 |

**选择建议**：
- 如果你的项目使用 Spring Cloud 微服务架构，推荐使用 **Feign 版本**
- 如果你需要更灵活的 HTTP 客户端配置，推荐使用 **RestTemplate 版本**
- 两个版本可以同时使用，互不冲突

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
- 拦截器新增 `DEBUG` 埋点（请求入口 / 加头结果，不记录 Token 值），提升链路可观测性。
- Spring Cloud OpenFeign 兼容矩阵扩充：2.2.x / 2.3.x（2.2.9.RELEASE + 10.12）与原有 2.4.x - 2.6.x、2.7.x 同列，均经真实 E2E 验证。
- 经 Spring Boot 2.7.9 / 2.4.5 / 2.3.12.RELEASE / 2.2.13.RELEASE 四版本真实 E2E 矩阵验证（每版本 14 个测试全绿）。

详见 [CHANGELOG.3.0.0.md](CHANGELOG.3.0.0.md)。

### 2.0.1 (2026-06-14)

- 升级 simple-aksk-redis-token-manager 2.0.0 → 2.0.1（Security Hardening：cacheKey 由 `hashCode()` 32-bit 升级为 SHA-256 截断 128-bit hex，消除多租户 hashCode 碰撞串号风险）
- API / 配置零变更，业务代码无需修改

### 2.0.0 (2026-05-27)

- 升级 simple-aksk-redis-token-manager 1.1.0 → 2.0.0（JWE 支持、Token 有效性改由 Redis TTL 保证）

### 1.1.0 (2026-05-06)

- 升级 simple-aksk-redis-token-manager 1.0.1 → 1.1.0（L1+L2 两级缓存、Token 预刷新、多实例 L1 一致性）
- 修复 `AkskFeignRequestInterceptor` 已有 Authorization 头时重复追加的 bug

### 1.0.1 (2026-04-08)

- 🐛 修复 `AkskFeignConfiguration` 加了 `@Configuration` 导致所有 Feign 客户端都携带 AKSK token 的问题

### 1.0.0 (2026-01-27)

初始版本发布：
- ✅ 实现基于 Spring Cloud OpenFeign 的 AKSK 客户端
- ✅ 集成 Redis Token Manager
- ✅ 支持自动添加 Authorization 请求头
- ✅ 提供 @AkskClientFeignClient 注解（推荐）
- ✅ 支持显式配置 AkskFeignConfiguration
- ✅ 完整的测试覆盖（拦截器、集成、端到端）

## 许可证

Apache License 2.0
