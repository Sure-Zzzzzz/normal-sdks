# smart-redis-limiter-management-policy-mutation-aksk-resttemplate-client-jakarta-starter

管理面类型化策略变更客户端（Jakarta / Spring Boot 3 线）：与 javax 传输件同构（jakarta 注解、
底座 `simple-aksk-resttemplate-redis-client-jakarta-starter`、hc5 连接池），契约与授权矩阵见
[core README](../../../client/smart-redis-limiter-management-policy-mutation-client-core/README.md)。

## 接入

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-management-policy-mutation-aksk-resttemplate-client-jakarta-starter:1.0.0'
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.apache.httpcomponents.client5:httpclient5'
```

```yaml
io.github.surezzzzzz.sdk.limiter.management.policy-mutation-client:
  enable: true
  base-url: https://limiter.example.test/api
```

- **凭据即控制点**：未授对应码的调用一律 403 失败关闭，客户端无任何权限开关；AKP 按消费方
  申请（运行端授 snapshot:read，License 授 policy 双码），授权矩阵详见
  [core README](../../client/smart-redis-limiter-management-policy-mutation-client-core/README.md)。
