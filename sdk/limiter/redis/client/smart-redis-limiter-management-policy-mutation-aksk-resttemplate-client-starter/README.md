# smart-redis-limiter-management-policy-mutation-aksk-resttemplate-client-starter

管理面类型化策略变更客户端（javax / Spring Boot 2.7 线）：基于 AKSK 底座 `akskClientRestTemplate`
的 RestTemplate 实现，契约见 [core README](../../smart-redis-limiter-management-policy-mutation-client-core/README.md)。

## 接入

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-management-policy-mutation-aksk-resttemplate-client-starter:1.0.0'
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.apache.httpcomponents:httpclient:4.5.13'
```

```yaml
io.github.surezzzzzz.sdk.limiter.management.policy-mutation-client:
  enable: true
  base-url: https://limiter.example.test/api   # 管理端根地址（含 context-path）
```

- 传输与认证复用底座 `akskClientRestTemplate`（AKP 令牌链），缺失时启动即失败；
- AKP 授权矩阵见 core README（变更族 write、回读族 read，DATA 按 serviceCode 限定）；
- **凭据即控制点**：未授对应码的调用一律 403 失败关闭，客户端无任何权限开关；AKP 按消费方申请（运行端授 snapshot:read，License 授 policy 双码），详见 core README 授权矩阵。
- 409 响应转为冲突异常供 upsert 编排重读重试；响应超字节上限、未知字段严格拒绝。
