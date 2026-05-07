# Simple AKSK Feign HttpSession Client Starter

[![Version](https://img.shields.io/badge/version-1.0.1-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

基于 HttpSession 的 AKSK Feign 客户端 Starter，集成 HttpSession Token Manager，提供开箱即用的 Feign 客户端和 AKSK 认证拦截。

## 核心能力

### 1. AKSK Feign 请求拦截器

- **AkskHttpSessionFeignRequestInterceptor** - Feign 请求拦截器
  - 实现 `RequestInterceptor` 接口
  - 自动从 `TokenManager` 获取 Token 并添加 `Authorization: Bearer {token}` 头
  - 先 `removeHeader` 再 `header`，确保始终只有一个 Authorization 头

### 2. @AkskHttpSessionFeignClient 注解

- 自动应用 `AkskHttpSessionFeignConfiguration`
- 继承 `@FeignClient` 所有属性，使用方式与原生 FeignClient 完全一致

## 依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-feign-httpsession-client-starter:1.0.0'
}
```

运行时需自行引入：

```gradle
implementation 'org.springframework.cloud:spring-cloud-starter-openfeign:3.1.8'
implementation 'io.github.openfeign:feign-httpclient:11.10'
implementation 'org.springframework.boot:spring-boot-starter-web'
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
```

## 使用方式

### @AkskHttpSessionFeignClient（推荐）

```java
@AkskHttpSessionFeignClient(name = "my-service", url = "http://localhost:8080")
public interface MyServiceClient {
    @GetMapping("/api/resource")
    String getResource();
}
```

### 显式配置

```java
@FeignClient(
    name = "my-service",
    url = "http://localhost:8080",
    configuration = AkskHttpSessionFeignConfiguration.class
)
public interface MyServiceClient {
    // ...
}
```

## 测试覆盖

✅ **拦截器测试**（AkskHttpSessionFeignRequestInterceptorTest）
- 拦截器应该添加 Authorization 请求头
- 当 token 为 null 时应该不添加 Authorization 头
- 当 token 为空字符串时不应该添加 Authorization 头
- TokenManager 抛异常时应该向上传播
- 已有 Authorization 头时应该覆盖（不重复追加）

✅ **集成测试**（FeignHttpSessionIntegrationTest）
- TokenManager Bean 应该存在
- AkskHttpSessionFeignRequestInterceptor Bean 应该存在

## 与 Redis 版本的区别

| | HttpSession 版本 | Redis 版本 |
|--|-----------------|-----------|
| Token 存储 | HttpSession | Redis |
| 跨实例共享 | ❌ | ✅ |
| 额外依赖 | 无 | Redis |
| 适用场景 | 单实例、轻量 | 多实例、生产级 |

## 版本历史

### 1.0.1 (2026-05-06)

- 重构类名，去掉冗余前缀（SimpleAksk → Aksk/AkskHttpSession）

### 1.0.0 (2026-05-06)

- 初始版本，基于 `simple-aksk-httpsession-token-manager` 提供 Feign 拦截器