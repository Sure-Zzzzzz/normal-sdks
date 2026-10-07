# smart-redis-limiter-management-aksk-resttemplate-client-jakarta-starter

限流策略客户端的 jakarta 认证传输件（javax 线使用 smart-redis-limiter-management-aksk-resttemplate-client-starter）：把 `smart-redis-limiter-management-client-core` 的契约接到真实 HTTP 上，传输与认证全部复用 AKSK 底座。Spring Boot 3 / Java 17。

## 解决什么、谁该接

- 你的 Spring Boot 2 应用是限流运行端宿主（引了 `smart-redis-limiter-starter`）且需要远程策略（聚形态）。
- 你持有 AKP（服务凭据），已按 AKSK 底座接入 `akskClientRestTemplate`（认证+连接池+超时）。
- 本 starter 装配带认证的策略客户端；不引入则运行端按本地限额自治（散形态，合法终态）。

## 依赖坐标

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-management-aksk-resttemplate-client-jakarta-starter:1.0.0'
implementation 'org.springframework.boot:spring-boot-starter-web'
```

AKSK 底座（提供 `akskClientRestTemplate`）经本件运行时传递带入；连接池实现 httpclient5 由宿主自备（Boot web 应用通常已具备）。

## 最小配置

```yaml
io.github.surezzzzzz.sdk.limiter.management.client:
  enable: true
  policy-snapshot-url: https://management.example.test/api/v1/policy/snapshot
  typed-policy-snapshot-url: https://management.example.test/api/v2/policy/snapshot
```

启用条件：`enable=true` 且类路径存在 RestTemplate 与 AKSK 底座的 `akskClientRestTemplate` Bean。缺底座 Bean 时启动即失败（响亮失败，不静默降级）。使用自定义 `SmartRedisLimiterManagementClient` 实现时，将本件 `enable=false`（不装配组件）并由宿主自行注册 Bean。

## 机器凭据与授权

向 Management 宿主的身份系统登记 AKP 并授予最小权限：

| 类型 | 码值 |
| --- | --- |
| API | `smartLimiterPolicySnapshot:read` |
| DATA | 资源 `limiter-policy`、动作 `read`、维度 `serviceCode`（IN 本服务） |

不给 PAGE、不给写。客户端不持有任何凭据或固定 token；令牌头由 `akskClientRestTemplate` 注入。

## 语义边界

- 200/304/ETag 缺失/超长响应/未知字段均严格处理，与限流运行端既有内嵌客户端语义一致。
- 单次响应字节上限默认 4 MiB（`max-response-bytes` 可调）；读取过程中强制限制。
- 本模块代码不 import AKSK 底座类型，升级底座不需升级本模块（除非底座 Bean 名变更）。

## 聚散形态

引本件=聚（远程精确/默认规则+本地完整兜底）；不引=散。运行端 `remote-policy.enable=true` 但类路径无 client 制品时启动失败——散形态请关闭 remote-policy。
