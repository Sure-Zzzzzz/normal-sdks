# Simple AKSK RestTemplate HttpSession Client Starter

[![Version](https://img.shields.io/badge/version-1.0.1-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

基于 HttpSession 的 AKSK RestTemplate 客户端 Starter，集成 HttpSession Token Manager，提供开箱即用的 HTTP 客户端和 AKSK 认证拦截。

## 核心能力

### 1. 自动配置的 RestTemplate

- **akskClientRestTemplate** - 预配置的 RestTemplate Bean
  - 自动注入 `AkskHttpSessionRestTemplateInterceptor` 拦截器
  - 自动添加 `Authorization: Bearer {token}` 请求头
  - 连接池可配置，开箱即用无需手动配置

### 2. AKSK 认证拦截器

- **AkskHttpSessionRestTemplateInterceptor** - RestTemplate 拦截器
  - 实现 `ClientHttpRequestInterceptor` 接口
  - 自动从 `TokenManager` 获取 Token 并添加 Authorization 请求头
  - 支持覆盖已有 Authorization 头，避免重复

## 依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-resttemplate-httpsession-client-starter:1.0.0'
}
```

运行时需自行引入：

```gradle
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.apache.httpcomponents:httpclient'
```

## 配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            client:
              enable: true
              server-url: http://localhost:8080
              token-endpoint: /oauth2/token
              client-id: YOUR_CLIENT_ID
              client-secret: YOUR_CLIENT_SECRET
              token:
                refresh-before-expire: 300
              resttemplate:
                enable: true
                max-total: 100
                max-per-route: 20
                connect-timeout: 5000
                read-timeout: 30000
```

## 使用方式

### 方式1：注入预配置 RestTemplate（推荐）

```java
@Autowired
private RestTemplate akskClientRestTemplate;

// 直接使用，无需手动配置拦截器和连接池
String result = akskClientRestTemplate.getForObject(url, String.class);
```

### 方式2：注入拦截器到自定义 RestTemplate

```java
@Autowired
private AkskHttpSessionRestTemplateInterceptor interceptor;

@Bean
public RestTemplate myRestTemplate() {
    RestTemplate restTemplate = new RestTemplate();
    restTemplate.getInterceptors().add(interceptor);
    return restTemplate;
}
```

## 测试覆盖

✅ **拦截器测试**（AkskHttpSessionRestTemplateInterceptorTest）
- 拦截器应该添加 Authorization 请求头
- 当 token 为 null 时不应该添加 Authorization 头
- 当 token 为空字符串时不应该添加 Authorization 头
- TokenManager 抛异常时应该向上传播
- execution 抛 IOException 时应该向上传播
- 已有 Authorization 头时应该覆盖（不重复追加）

✅ **集成测试**（RestTemplateHttpSessionIntegrationTest）
- TokenManager Bean 应该存在
- AkskHttpSessionRestTemplateInterceptor Bean 应该存在

## 与 Redis 版本的区别

| | HttpSession 版本 | Redis 版本 |
|--|-----------------|-----------|
| Token 存储 | HttpSession | Redis |
| 跨实例共享 | ❌ | ✅ |
| 额外依赖 | 无 | Redis |
| 适用场景 | 单实例、轻量 | 多实例、生产级 |

## 版本历史

### 1.0.1 (2026-05-06)

- 重构类名，去掉冗余前缀（SimpleAksk → RestTemplateHttpSession）

### 1.0.0 (2026-05-06)

- 初始版本，基于 `simple-aksk-httpsession-token-manager` 提供 RestTemplate 拦截器