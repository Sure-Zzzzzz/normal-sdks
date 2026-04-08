# simple-aksk-client-demo

验证 `simple-aksk-feign-redis-client-starter` 和 `simple-aksk-resttemplate-redis-client-starter` 同时引用时互不冲突。

> 本模块只有测试代码，不发布到 Maven Central。

---

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
