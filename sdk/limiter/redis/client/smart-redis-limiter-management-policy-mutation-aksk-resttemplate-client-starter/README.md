# smart-redis-limiter-management-policy-mutation-aksk-resttemplate-client-starter

管理面类型化策略变更客户端（javax / Spring Boot 2.7 线）：基于 AKSK 底座 `akskClientRestTemplate`
的 RestTemplate 实现，契约见 [core README](../../smart-redis-limiter-management-policy-mutation-client-core/README.md)。

## 接入

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-management-policy-mutation-aksk-resttemplate-client-starter:1smart-redis-limiter-management-policy-mutation-aksk-resttemplate-client-starter:1.1.0'
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

## AKP Profile

本模块支持 AKSK Client 多 AKP Profile。调用方在 AKSK Client 底座的 `auth.aksk.client.profiles.<name>`
中声明 Profile，再通过本模块配置键 `io.github.surezzzzzz.sdk.limiter.management.policy-mutation-client.aksk-profile` 选择 Profile。

- 配置留空或 `default` 时使用顶层默认 AKP，原有接入不变。
- 配置命名 Profile 时，RestTemplate 走底座 Profile 模板；Feign 只挂一个 Profile 认证拦截器。
- Profile 不存在或凭据不完整时启动失败，不静默改用默认 AKP。
- Secret 由 AKSK 底座管理；本模块不输出 Secret、clientId、Token 或 Authorization。
