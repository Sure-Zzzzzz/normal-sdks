# simple-redis-route-starter 1.1.0

## 新增能力

- 新增 Redis Server 信息探测：datasource 初始化后执行 `INFO server`，记录 datasource 对应的 Redis 版本与运行模式。
- 新增 `RedisRouteTemplate#serverInfo()`、`serverInfo(String)`、`serverInfoByKey(String)` 只读 API。
- 新增 `RedisServerVersionHelper`，统一处理 Redis 版本比较与 unknown 判断。
- 新增 `RedisCommandCapabilityHelper`，按 Redis 版本保守判断 ACL、UNLINK、GETEX、SET GET、KEEPTTL、ZPOP、LMOVE 能力。
- 新增 `RedisCommandCompatibilityHelper`，提供语义安全的 `deletePreferUnlink` 和显式能力断言。

## 增强与修正

- datasource 创建失败时保留 datasource key，便于定位配置问题。
- 补严 `default-source`、route rule datasource 等空白配置校验。
- Redis Server 探测失败不阻断启动，unknown 版本下命令能力判断统一保守返回 `false`。
- 兼容 Spring Data Redis 2.x 的 `INFO server` API 差异，支持 cluster 返回的节点前缀 `redis_version` / `redis_mode` 信息。
- 异常日志只记录异常类型，不输出原始异常对象和原始异常消息，避免日志系统采集到认证信息或连接细节。

## 测试架构调整

- 多版本矩阵 E2E 配置从 Java 内联 `@SpringBootTest(properties=...)` 迁移到测试 YAML，与 es-route 组织方式对齐。
- 公共 datasource / route rule 集中在 `application-redis-route-version-matrix.yaml`，不再混在测试注解里。
- 各 Spring Boot 版本专属 `application-redis-route-version-matrix-<version>.yaml` 声明该版本下哪些 datasource `known=true`、哪些是兼容边界 `known=false`。
- 新增 `RedisRouteMatrixProfilesResolver`，按 `spring.profiles.active` 加载公共 matrix profile + SB 版本 profile + 版本专属 matrix profile。
- 新增测试侧 `RedisRouteMatrixExpectationProperties` 绑定兼容预期，矩阵 E2E 按 YAML 预期断言 known / unknown datasource，不再在 Java 里硬编码 Spring Boot 版本分支。
- 重型 E2E 收敛为单一 `RedisRouteMultiVersionMatrixEndToEndTest`，删除旧 `RedisRouteClusterEndToEndTest` / `RedisRouteMixedEndToEndTest` 和 `docker-compose.redis-cluster.yml`。

## Redis 版本矩阵

| Redis Server | standalone | cluster | 说明 |
|--------------|------------|---------|------|
| 3.2.12 | 已验证 | 已验证 | 老版本能力基线，不假设 UNLINK / ZPOP / GETEX / ACL |
| 5.0.14 | 已验证 | 已验证 | 支持 UNLINK / ZPOP，不假设 GETEX / ACL / LMOVE / KEEPTTL |
| 7.2.6 | 已验证 | 已验证 | BSD-3-Clause Redis OSS 线上的现代能力验证版本 |

默认测试矩阵不使用 Redis 7.4+ 或 8.x；如需引入，必须先完成许可证评估。

## Spring Boot 兼容验证

| Spring Boot | Java | 验证结果 |
|-------------|------|----------|
| 2.7.9 | 11 | Redis 3.2.12 / 5.0.14 / 7.2.6 standalone + cluster 全通过 |
| 2.4.5 | 8 | Redis 3.2.12 / 5.0.14 / 7.2.6 standalone + cluster 全通过 |
| 2.3.12 | 8 | Redis 3.2.12 / 5.0.14 / 7.2.6 standalone + cluster 全通过 |
| 2.2.x | 8 | Redis 3.2.12 / 5.0.14 cluster 与 3.2.12 / 5.0.14 / 7.2.6 standalone 通过；Redis 7.2.6 cluster 为旧 Lettuce 5.2 兼容边界 |

Spring Boot 2.2.x 自带 Lettuce 5.2.2.RELEASE 无法解析 Redis 7 cluster `CLUSTER SLOTS` 节点 metadata，`redis7Cluster` 在该组合下按 unknown 处理。
