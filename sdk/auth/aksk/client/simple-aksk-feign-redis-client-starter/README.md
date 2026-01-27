# Simple AKSK Feign Redis Client Starter

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
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

本模块依赖：
- simple-aksk-redis-token-manager - Redis Token 管理器
- simple-aksk-client-core - 客户端核心
- Spring Boot - 自动配置
- Spring Cloud OpenFeign - 声明式 HTTP 客户端
- Spring Data Redis - Redis 操作
- Lombok - 简化代码

## 配置示例

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    database: 0

io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            client:
              enable: true
              client-id: AKP1234567890abcdefgh
              client-secret: SK1234567890abcdefghijklmnopqrstuvwxyz1234
              server-url: http://localhost:8080
              token-endpoint: /oauth2/token
              token:
                refresh-before-expire: 300  # 提前5分钟刷新
              redis:
                token:
                  me: my-app  # 应用标识
```

## 使用方式

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-feign-redis-client-starter:1.0.0'
}
```

**重要说明**：为了避免版本冲突，starter 使用 `compileOnly` 声明依赖，不会传递依赖到您的项目。请根据您的 Spring Boot 版本自行引入以下依赖：

**必需依赖：**

```gradle
dependencies {
    // Spring Cloud OpenFeign（声明式 HTTP 客户端）
    // 根据您的 Spring Boot 版本选择对应的版本：
    // - Spring Boot 2.7.x: 使用 3.1.8
    // - Spring Boot 2.4.x - 2.6.x: 使用 3.0.3
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign:3.1.8'

    // Feign HttpClient（HTTP 连接池，推荐）
    // 根据您的 Spring Boot 版本选择对应的版本：
    // - Spring Boot 2.7.x: 使用 11.10
    // - Spring Boot 2.4.x - 2.6.x: 使用 10.12
    implementation 'io.github.openfeign:feign-httpclient:11.10'

    // Spring Data Redis（Redis 操作）
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
}
```

**版本对应关系：**

| Spring Boot 版本 | Spring Cloud OpenFeign | Feign HttpClient | 说明 |
|-----------------|----------------------|------------------|------|
| 2.7.x | 3.1.8 | 11.10 | 推荐，测试通过 |
| 2.4.x - 2.6.x | 3.0.3 | 10.12 | 兼容 |

**版本说明**：
- 本 starter 使用 `compileOnly` 声明依赖，不会强制版本
- 运行时使用您项目中引入的版本
- Redis 驱动会通过 spring-boot-starter-data-redis 自动引入

### 2. 启用 Feign 客户端

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

### 3. 使用场景 1：使用 @AkskClientFeignClient 注解（推荐）

最简单的使用方式，开箱即用：

```java
@AkskClientFeignClient(name = "my-service", url = "http://localhost:8080")
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

### 4. 使用场景 2：显式配置 Feign 客户端

如果你需要更多控制，可以使用原始 `@FeignClient` 注解并显式配置：

```java
@FeignClient(
    name = "my-service",
    url = "http://localhost:8080",
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

### 5. 使用场景 3：只使用 TokenManager

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

### 6. 多用户场景

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

不同用户会获取不同的 Token（基于 hashCode 隔离）。

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

## 测试覆盖

✅ **拦截器测试**（AkskFeignRequestInterceptorTest）
- 拦截器应该添加 Authorization 请求头
- 当 token 为 null 时应该不添加 Authorization 头
- 当 token 为空字符串时应该不添加 Authorization 头

✅ **集成测试**（FeignIntegrationTest）
- TokenManager Bean 是否存在
- AkskFeignRequestInterceptor Bean 是否存在
- AkskFeignConfiguration Bean 是否存在
- 拦截器是否可以访问 TokenManager

✅ **端到端测试**（FeignEndToEndTest）
- TokenManager 应该存在
- TestFeignClient 应该存在
- 获取 Token 应该成功
- 使用 Token 调用 FeignClient 应该成功
- ExplicitConfigFeignClient 应该存在
- 使用显式配置的 FeignClient 调用应该成功

**总计：12 个测试，100% 通过**

## 常见问题

### Q1: @AkskClientFeignClient 和 @FeignClient 有什么区别？

**A**: `@AkskClientFeignClient` 是对 `@FeignClient` 的封装，自动配置了 `AkskFeignConfiguration`。

```java
// 使用 @AkskClientFeignClient（推荐）
@AkskClientFeignClient(name = "my-service", url = "http://localhost:8080")
public interface MyServiceClient {
    // ...
}

// 等价于
@FeignClient(
    name = "my-service",
    url = "http://localhost:8080",
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
