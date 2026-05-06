# simple-aksk-client-demo

[![Version](https://img.shields.io/badge/version-1.1.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)

验证 `simple-aksk-feign-redis-client-starter` 和 `simple-aksk-resttemplate-redis-client-starter` 同时引用时互不冲突，共享同一个 `TokenManager`。

> 本模块只有测试代码，不发布到 Maven Central。

## 依赖引入

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-feign-redis-client-starter:1.1.0'
    implementation 'io.github.sure-zzzzzz:simple-aksk-resttemplate-redis-client-starter:1.1.0'

    // 运行时依赖（compileOnly 不传递，需自行引入）
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign:3.1.8'
    implementation 'io.github.openfeign:feign-httpclient:11.10'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'com.github.ben-manes.caffeine:caffeine:2.9.3'
}
```

## 前置条件

1. 启动 `simple-aksk-server-starter`（端口 8080）
2. 启动 Redis（端口 6379）
3. 在 Server 中创建测试客户端，并授予 `/api/token` scope

## 配置

复制 `application-local.yml.example` 为 `application-local.yml`，填入真实凭证：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            client:
              client-id: YOUR_CLIENT_ID
              client-secret: YOUR_CLIENT_SECRET
```

完整配置示例（`application.yml`）：

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
              server-url: http://localhost:8080
              token-endpoint: /oauth2/token
              token:
                refresh-before-expire: 300
              redis:
                token:
                  cache-name: aksk-client-token
              resttemplate:
                enable: true
        cache:
          enabled: true
          key-prefix: sure-auth-aksk-client
          me: my-app
          l1:
            enabled: true
            expire-seconds: 2
            max-size: 1000
          l2:
            enabled: true
            expire-seconds: 3600
            preload:
              enabled: true
              before-expire-seconds: 300
          consistency:
            mode: strong
```

## 测试场景

### BothClientsCoexistTest — 核心：共存验证

| 测试 | 说明 |
|------|------|
| testBothBeansExistWithoutConflict | Feign 和 RestTemplate 的 Bean 同时注入，无冲突 |
| testBothClientsShareSameTokenManager | 两个客户端共享同一个 TokenManager，token 只取一次 |
| testFeignAndRestTemplateBothCallSucceed | Feign 和 RestTemplate 同时发请求，互不干扰 |
| testBothClientsReuseToken | 两个客户端复用同一个缓存 token，无重复获取 |
| testPlainFeignClientNotAffectedByAksk | 普通 `@FeignClient` 不受 AKSK 拦截器影响（验证 feign-starter 1.0.1 修复） |

### FeignOnlyCallTest — Feign 独立调用

| 测试 | 说明 |
|------|------|
| testFeignClientGetToken | 获取 token 成功 |
| testFeignClientCallWithToken | 携带 token 调用接口成功 |
| testFeignClientReuseToken | 多次调用复用同一个 token |

### RestTemplateOnlyCallTest — RestTemplate 独立调用

| 测试 | 说明 |
|------|------|
| testRestTemplateGetToken | 获取 token 成功 |
| testRestTemplateCallWithToken | 携带 token 调用接口成功 |
| testRestTemplateAutoRefreshToken | token 过期后自动刷新 |
| testRestTemplateReuseToken | 多次调用复用同一个 token |

## 结论

两个 starter 可以同时引用，共享同一个 `TokenManager`（token 缓存不重复），各自的拦截器独立工作，互不干扰。通常情况下按需选一个即可，有同时使用两种 HTTP 客户端的场景时可以放心共存。

## 版本历史

### 1.1.0 (2026-05-06)

- 升级 feign-redis-client-starter、resttemplate-redis-client-starter 至 1.1.0
- 更新配置：`redis.token.me` → `redis.token.cache-name`，新增 smart-cache 配置块

### 1.0.0 (2026-01-27)

- 初始版本，验证 Feign 和 RestTemplate 两个 starter 共存场景
