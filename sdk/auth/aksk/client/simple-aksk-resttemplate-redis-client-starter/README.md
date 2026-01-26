# Simple AKSK RestTemplate Redis Client Starter

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
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

本模块依赖：
- simple-aksk-redis-token-manager - Redis Token 管理器
- simple-aksk-client-core - 客户端核心
- Spring Boot - 自动配置
- Spring Web - RestTemplate
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
              resttemplate:
                enable: true  # 启用 akskClientRestTemplate Bean（默认：false）
                max-total: 100  # 连接池最大连接数（默认：100）
                max-per-route: 20  # 每个路由的最大连接数（默认：20）
                connect-timeout: 5000  # 连接超时（毫秒，默认：5000）
                read-timeout: 30000  # 读取超时（毫秒，默认：30000）
```

## 使用方式

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-resttemplate-redis-client-starter:1.0.0'
}
```

**重要说明**：为了避免版本冲突，starter 使用 `compileOnly` 声明依赖，不会传递依赖到您的项目。请根据您的 Spring Boot 版本自行引入以下依赖：

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

### 2. 使用场景 1：直接使用 akskClientRestTemplate（推荐）

最简单的使用方式，开箱即用：

```java
@Service
public class MyService {

    @Autowired
    @Qualifier("akskClientRestTemplate")
    private RestTemplate akskClientRestTemplate;

    public String callApi() {
        // 自动添加 Authorization 头
        String url = "http://localhost:8080/api/resource";
        ResponseEntity<String> response = akskClientRestTemplate.getForEntity(url, String.class);
        return response.getBody();
    }
}
```

**特点**：
- ✅ 自动添加 `Authorization: Bearer {token}` 请求头
- ✅ Token 由 RedisTokenManager 自动管理和刷新
- ✅ 无需手动管理 Token

### 3. 使用场景 2：只使用 TokenManager

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

### 4. 使用场景 3：自定义 RestTemplate

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

### 5. 多用户场景

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

## 测试覆盖

✅ **拦截器测试**（AkskRestTemplateInterceptorTest）
- 拦截器应该添加 Authorization 请求头
- 当 token 为 null 时应该不添加 Authorization 头
- 当 token 为空字符串时应该不添加 Authorization 头

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

**总计：14 个测试，100% 通过**

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

### 1.0.0 (2026-01-26)

初始版本发布：
- ✅ 实现基于 RestTemplate 的 AKSK 客户端
- ✅ 集成 Redis Token Manager
- ✅ 支持自动添加 Authorization 请求头
- ✅ 提供灵活的组件选择（TokenManager、RestTemplate、Interceptor）
- ✅ 完整的测试覆盖（拦截器、集成、端到端）

## 许可证

Apache License 2.0
