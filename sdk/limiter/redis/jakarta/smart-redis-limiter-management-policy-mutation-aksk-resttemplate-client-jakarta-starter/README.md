# smart-redis-limiter-management-policy-mutation-aksk-resttemplate-client-jakarta-starter

管理面类型化策略变更客户端（Jakarta / Spring Boot 3 线）：与 javax 传输件同构（jakarta 注解、
底座 `simple-aksk-resttemplate-redis-client-jakarta-starter`、hc5 连接池），契约与授权矩阵见
[core README](../../../client/smart-redis-limiter-management-policy-mutation-client-core/README.md)。

## 接入

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-management-policy-mutation-aksk-resttemplate-client-jakarta-starter:1smart-redis-limiter-management-policy-mutation-aksk-resttemplate-client-jakarta-starter:1.1.0'
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

## AKP Profile

本模块支持 AKSK Client 多 AKP Profile。调用方在 AKSK Client 底座的 `auth.aksk.client.profiles.<name>`
中声明 Profile，再通过本模块配置键 `io.github.surezzzzzz.sdk.limiter.management.policy-mutation-client.aksk-profile` 选择 Profile。

- 配置留空或 `default` 时使用顶层默认 AKP，原有接入不变。
- 配置命名 Profile 时，RestTemplate 走底座 Profile 模板；Feign 只挂一个 Profile 认证拦截器。
- Profile 不存在或凭据不完整时启动失败，不静默改用默认 AKP。
- Secret 由 AKSK 底座管理；本模块不输出 Secret、clientId、Token 或 Authorization。
