# Simple Redis Route Jakarta Starter

`simple-redis-route-jakarta-starter` 为 Spring Boot 3.x 应用提供 Redis 多数据源路由。启用后，`default-source` 同时是路由的默认数据源和标准 Spring Redis 的默认入口；命名数据源仍通过 `RedisRouteTemplate` 按 key 或显式数据源名访问。

它适用于缓存、会话和分布式锁等数据需要隔离，或同一应用同时使用 Redis Cluster 与 standalone Redis 的场景。

## 版本选择

| 应用运行时 | 使用模块 | 版本 | 是否可同时引入 |
| --- | --- | --- | --- |
| Spring Boot 2.x（javax） | `simple-redis-route-starter` | `1.2.2` | 否 |
| Spring Boot 3.x（Jakarta） | `simple-redis-route-jakarta-starter` | `1.0.0` | 否 |

两条线保留相同包名、配置键和公共类型。升级到 Spring Boot 3.x 时只替换依赖坐标；同一应用不能同时引入两条线。

## 最小接入

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'io.github.sure-zzzzzz:simple-redis-route-jakarta-starter:1.0.0'
}
```

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        redis:
          route:
            enable: true
            default-source: default
            sources:
              default:
                mode: standalone
                host: ${REDIS_HOST}
                port: ${REDIS_PORT:6379}
```

启用后，标准的 `RedisConnectionFactory`、`StringRedisTemplate` 和 `RedisTemplate` 都绑定 `default-source`。按 key 选择数据源或访问命名数据源时使用 `RedisRouteTemplate`：

```java
redisRouteTemplate.execute("cache:item:42", template -> {
    template.opsForValue().set("cache:item:42", "cached-value");
    return null;
});
```

未匹配任何规则的 key 使用 `default-source`。

## Redis 认证

需要 Redis ACL 认证时，在对应数据源下配置 `username` 和 `password`；只使用旧式密码认证时，省略 `username`。凭据应由部署环境提供，不要把明文写入仓库：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        redis:
          route:
            enable: true
            default-source: default
            sources:
              default:
                mode: standalone
                host: ${REDIS_HOST}
                port: ${REDIS_PORT:6379}
                username: ${REDIS_USERNAME}
                password: ${REDIS_PASSWORD}
```

显式认证配置无法应用时，连接工厂初始化会失败；用户名或密码被 Redis 拒绝时，实际连接会失败，不应将前者误当作凭据已通过服务端认证。

## 行为边界

- 支持 standalone 与 Redis Cluster，可在一个应用中混用；支持 exact、prefix、suffix、wildcard、regex 五种路由规则。
- 多 key 回调必须命中同一数据源。Route 不保证 Redis Cluster 的 key 位于同一个 slot；业务使用 multi-key 命令或 Lua 时仍应自行使用 hash tag。
- Route 是物理 `RedisConnectionFactory` 的唯一生命周期 owner。启用后，宿主自建独立连接工厂或未绑定 Route default-source 的标准模板会在启动期失败关闭。
- 命名数据源不注册为全局标准 Redis Bean，只能通过 `RedisRouteTemplate` 使用。
- Redis 密码、认证信息、业务 key/value 和 TLS 材料不得写入配置仓库、日志或异常消息。
- 显式配置的用户名、密码或客户端名称若为空白或无法应用，启动直接失败；跨数据源错误只返回数据源名称，不回显原始 Redis key。

## 运行时要求

- Java 17 或更高版本。
- Spring Boot 3.x 与 Spring Data Redis 3.x。
- Lettuce 连接池默认关闭。启用 `lettuce.pool.enabled=true` 时，宿主需提供 `org.apache.commons:commons-pool2` 运行时依赖；Spring Boot 应用可由自身 BOM 管理版本。

启用连接池的宿主在自己的 Gradle 依赖中增加：

```groovy
implementation 'org.apache.commons:commons-pool2'
```

## 已验证兼容性

以下组合均执行了完整 156 项测试，零失败、零跳过。真实 Redis 测试覆盖 Redis 3/5/7 的 standalone 和 Cluster；Redis 7 standalone 另以临时 ACL 用户验证正确密码读写及错误密码拒绝。Cluster 的用户名、密码配置与失败关闭已验证，但未验证独立启用认证的 Cluster 网络握手。

| Spring Boot | Java |
| --- | --- |
| 3.2.12 | 17、21 |
| 3.3.13 | 17、21 |
| 3.4.2 | 17、21 |
