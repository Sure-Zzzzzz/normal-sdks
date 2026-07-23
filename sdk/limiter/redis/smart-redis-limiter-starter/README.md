# smart-redis-limiter-starter

智能 Redis 限流 Starter。2.0.0 强制通过 `simple-redis-route-starter` 访问 Redis，并支持本地规则与远程动态策略的请求级统一执行。

## 版本选型

| starter | core | redis-route | 说明 |
|---|---|---|---|
| 2.0.0 | 2.1.0 | 1.1.0 | Route 原生化、动态策略、fixed used counter、sliding remaining 修复 |
| 1.x | 1.x | 不强制 | 历史版本，不支持动态策略 |

## 主要能力

- 注解与拦截器双模式。
- fixed、sliding 双算法和多窗口原子限流。
- Redis Route 强依赖；缺少 Route class 或 `RedisRouteTemplate` Bean 时明确启动失败。
- 远程策略请求路径零网络调用，只读取本地 `AtomicReference` 快照。
- 精确匹配 `serviceCode + resourceCode + subject`，命中时整体替换 limits。
- ETag/304、canonical SHA-256、版本防回退、last-known-good。
- remote disabled 时不创建 HTTP Client、JSON Codec、快照存储、调度器或线程。
- 原始 subject 不进入 Redis Key、事件、日志和 context attributes。
- Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9 兼容。

## 兼容矩阵

| Spring Boot | Java | Redis Route 验证范围 |
|---|---:|---|
| 2.7.9 | 11 | Redis 3.2.12 / 5.0.14 / 7.2.6，standalone + cluster |
| 2.4.5 | 8 | Redis 3.2.12 / 5.0.14 / 7.2.6，standalone + cluster |
| 2.3.12 | 8 | Redis 3.2.12 / 5.0.14 / 7.2.6，standalone + cluster |
| 2.2.x | 8 | Redis 3.2.12 / 5.0.14 cluster，以及 3.2.12 / 5.0.14 / 7.2.6 standalone |

Spring Boot 2.2.x 自带 Lettuce 5.2.2.RELEASE 无法解析 Redis 7 cluster 的 `CLUSTER SLOTS` 新节点 metadata；因此该组合不支持 Redis 7.2.6 cluster。升级至 Spring Boot 2.3.12+ 后，同一 Redis 7 cluster 矩阵已验证通过。

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-starter:2.0.0'
```

starter 传递依赖：

- `smart-redis-limiter-core:2.1.0`
- `simple-redis-route-starter:1.1.0`
- Spring Data Redis、AOP、Web

运行时不依赖 management、JDBC、MySQL、Security 或 Actuator。Management 仅作为本模块测试依赖，不会传递给业务应用。

## 最小配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        redis:
          route:
            enable: true
        limiter:
          redis:
            smart:
              enable: true
              me: test-service
              mode: both
              redis:
                command-timeout: 3000
              fallback:
                on-redis-error: deny
```

Redis Route 的 datasource、standalone/cluster 配置以 `simple-redis-route-starter` 文档为准。限流器不会回退到应用主 Redis。

## 注解模式

```java
@SmartRedisLimiter(
        resourceCode = "query-data",
        rules = {
                @SmartRedisLimitRule(
                        count = 10L,
                        window = 1L,
                        unit = SmartRedisLimiterTimeUnit.SECONDS)
        },
        algorithm = "sliding",
        fallback = "allow"
)
public String queryData(String id) {
    return id;
}
```

`resourceCode` 为空时只使用本地策略并保持旧逻辑 Key；非空时允许精确命中远程动态策略。

## 拦截器模式

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        limiter:
          redis:
            smart:
              interceptor:
                default-key-strategy: path
                default-fallback: allow
                rules:
                  - path-pattern: /api/query/**
                    method: GET
                    resource-code: query-data
                    key-strategy: path
                    algorithm: sliding
                    limits:
                      - count: 100
                        window: 1
                        unit: SECONDS
```

远程策略只能整体替换 `limits`，不能修改 algorithm、fallback、keyStrategy、path/method、mode 或 datasource。

## 远程策略

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        limiter:
          redis:
            smart:
              remote-policy:
                enable: true
                snapshot-url: https://management.internal/api/v1/policy/snapshot
                policy-token: ${SMART_LIMITER_POLICY_TOKEN}
                refresh-interval-millis: 60000
                connect-timeout-millis: 2000
                read-timeout-millis: 3000
                initial-refresh: true
                max-policy-count: 10000
                max-limits-per-policy: 16
                max-response-bytes: 4194304
```

默认客户端自动以 `smart.me` 作为 URL 编码后的 `serviceCode`，无需重复配置服务编码：

```text
GET https://management.internal/api/v1/policy/snapshot?serviceCode=test-service
```

当配置可选的 `remote-policy.policy-token` 时，默认客户端还会发送：

```http
X-Smart-Redis-Limiter-Policy-Token: <shared-secret>
```

该 header 用于 management 明确关闭 resource-server 后的临时固定 token 模式；token 不会进入 URL、user-info、查询参数、事件或日志。resource-server/AKSK 模式下默认客户端不会获取 AKSK 凭据；调用方应接入自身的认证获取/网关能力，或提供自定义 `SmartRedisLimiterPolicyClient`。

Management `1.0.0` 是独立服务，官方运行时为 Spring Boot 2.7.x。真实 Management → MySQL → HTTP 快照 → limiter → Redis Route/Redis 算法的 E2E 仅在本模块 Spring Boot 2.7.9 基线执行；其余兼容矩阵验证 limiter 和 Redis Route 本身。

协议约束：

- `snapshot-url` 必须是完整绝对 HTTP/HTTPS URL，禁止 user-info、query 和 fragment。
- `policy-token` 可选且会从 Properties `toString()` 脱敏；应由环境变量或 secret manager 注入，不得写入配置仓库。
- 200 必须携带合法 ETag；SDK 同时限制 Content-Length 和实际输入流字节数。
- 已有快照时携带 `If-None-Match`，304 保留当前快照；首次 304 视为失败。
- revision 回退、同 revision 内容漂移、新 revision 复用旧 ETag 均拒绝。
- 合法空 `policies` 会清空动态覆盖；失败继续使用 last-known-good。
- JSON 使用 SDK 独立 ObjectMapper，未知字段严格失败，不注入或修改应用 ObjectMapper。

## Key 语义

本地策略（`resourceCode` 为空）：

```text
smart-limiter:test-service:<keyPart>
```

动态策略：

```text
smart-limiter:test-service:query-data:<subjectSha256>
```

原始 subject 只参与内存中的精确匹配和 SHA-256 计算，不写入 Redis。

固定窗口 2.0 使用 used counter 新 namespace：

```text
# 1.x remaining counter（2.0 不读取）
smart-limiter:test-service:<keyPart>:60s

# 2.0 used counter
smart-limiter:test-service:<keyPart>:fw2:60s
```

启用 Hash Tag 后，同一逻辑限流身份的多窗口 physical keys 处于同一 Redis Cluster slot；routeKey 不带窗口后缀。

## 算法语义

### fixed

- Redis String 保存已使用次数 used，而不是 remaining。
- 每次先检查全部窗口；任一窗口达到当前阈值时整体拒绝且不写任何窗口。
- 阈值动态升降继续解释同一 used 计数，TTL 不随请求刷新。
- remaining 为 `max(limit - usedAfterAcquire, 0)`。

### sliding

- 每次先清理所有窗口过期成员。
- 全部窗口允许后统一 ZADD，任一窗口拒绝时不向任何窗口写入。
- remaining 按写入后计数计算，首次请求返回 `limit - 1`。
- resetAt 为最老有效成员的真实释放时间。

## 扩展 Bean

以下默认实现均使用 `@ConditionalOnMissingBean`：

- `SmartRedisLimiterRedisExecutor`
- `SmartRedisLimiterPolicyJsonCodec`
- `SmartRedisLimiterPolicyClient`
- `SmartRedisLimiterPolicySnapshotValidator`
- `SmartRedisLimiterPolicySnapshotStore`
- `SmartRedisLimiterPolicyResolver`
- `SmartRedisLimiterPolicyRefreshManager`

## 1.x 升级到 2.0.0

1. 配置并启用 `simple-redis-route-starter:1.1.0`，确保存在 `RedisRouteTemplate` Bean。
2. 升级 core 到 2.1.0，调用方重新编译。
3. 将注解时间单位从 `java.util.concurrent.TimeUnit` 改为 `SmartRedisLimiterTimeUnit`。
4. 确认 `count/window` 使用 long 范围，但不得超过 Lua 安全整数边界。
5. 评估 fixed 新 namespace 带来的自然重新计数；1.x Key 不会被迁移或读取。
6. 需要动态策略时为规则设置稳定 `resourceCode` 并开启 `remote-policy`。
7. 观察事件中的 `resourceCode/policySource/policyRevision`，确认最终执行策略来源。

## 事件

`SmartRedisLimiterEventPayload` 包含最终 limits 对应的 limit、remaining、resetAt，以及：

- `resourceCode`
- `policySource`：`local` / `remote`
- `policyRevision`
- Route 执行快照

事件 attributes 只保留调用方扩展字段；SDK 内部 `precomputedKeyPart`、route 状态和原始 subject 不会进入事件。
