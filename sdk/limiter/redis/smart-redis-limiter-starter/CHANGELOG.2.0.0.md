# CHANGELOG 2.0.0

发布日期：2026-07-23
类型：Major Feature / Compatibility Break

## 依赖升级

| 依赖 | 版本 |
|---|---|
| smart-redis-limiter-core | 2.1.0 |
| simple-redis-route-starter | 1.1.0 |

## 变更内容

### Redis Route 原生化

- Redis Route 成为强依赖。
- 缺少 Route class 或 `RedisRouteTemplate` Bean 时明确启动失败。
- 不再回退到应用主 Redis。
- route、datasource 和 redis mode 快照统一进入执行结果与事件。

### 远程动态策略

- 增加稳定 `resourceCode`，支持按 `serviceCode + resourceCode + subject` 精确匹配策略。
- 命中时整体替换 limits；远程策略不能修改算法、降级、Key 策略和路由。
- 请求链只读取一次本地原子快照，不访问 management。
- 支持 ETag、If-None-Match、304、fixed-delay 和 last-known-good。
- revision 回退、同 revision 内容漂移、新 revision 复用旧 ETag 均拒绝。
- canonical SHA-256 基于已校验对象模型，不依赖 JSON 字段顺序或时间单位等价表达。
- JSON Codec 自建独立 ObjectMapper，未知字段严格失败。
- remote disabled 时不创建 HTTP、快照、调度 Bean 或线程。

### subject 隔离

- 动态策略 Redis Key 使用 subject 的 SHA-256 小写十六进制摘要。
- 原始 subject 不进入 Redis Key、事件、日志、指标和 context attributes。
- 事件 attributes 过滤全部 SDK 内部执行属性。

### fixed 算法

- Redis String 从 remaining counter 改为 used counter。
- 使用 `:fw2:<windowSeconds>s` 新 physical namespace，完全隔离 1.x Key。
- 多窗口先全量检查再写入，任一窗口拒绝时不修改任何窗口。
- 支持动态提高或降低阈值，同时保持同一 used 计数和原 TTL。

### sliding 算法

- 所有窗口先清理过期成员。
- 全部允许后统一 ZADD，拒绝时不写任何窗口。
- remaining 改为按写入后的计数计算。
- resetAt 改为最老有效成员的真实释放时间。

### 配置与公共 API

- `SmartRedisLimitRule.count/window` 使用 long。
- 注解时间单位改为 `SmartRedisLimiterTimeUnit`，避免暴露 core 不支持的纳秒、微秒和毫秒单位。
- `SmartLimitRule.count/window` 使用 `Long`，可识别缺失配置。
- 所有本地和远程限额统一校验 Lua `2^53-1` 安全整数边界。
- 保留 `SmartRedisLimiterAlgorithm` 原有签名，并通过 default/重载桥接执行计划。

## 新增测试

- `SmartRedisLimiterPolicyTest`：Resolver、Atomic Store、revision、canonical digest、subject hash。
- `SmartRedisLimiterPolicyJsonCodecTest`：合法快照与未知字段严格失败。
- 自动配置测试：remote disabled 零 Bean、remote enabled 单例 Bean 集合。
- 真实端到端测试：Management 1.0.0、MySQL、随机端口 HTTP 快照、ETag/304、Redis Route 与真实 Redis fixed 多窗口执行，验证远程 `2/秒 + 2/分钟` 策略覆盖本地宽松规则后为两次通过、第三次拒绝。
- 既有 fixed/interceptor Key 结构测试升级到 `fw2` namespace。
- 完整模块矩阵：Spring Boot 2.7.9、2.4.5、2.3.12、2.2.x 均已验证；Management E2E 仅在其官方支持的 2.7.9 基线运行。

## 向后兼容性

2.0.0 是破坏性升级：

- 必须启用 Redis Route。
- 注解中的 `java.util.concurrent.TimeUnit` 必须替换为 `SmartRedisLimiterTimeUnit`。
- fixed 计数从 1.x remaining Key 切换到 2.0 used Key，升级后自然重新计数。
- 建议所有调用方重新编译。

## 升级指南

1. 升级 core 2.1.0 与 route 1.1.0。
2. 完成 Redis Route datasource 配置并确认 `RedisRouteTemplate` Bean。
3. 替换注解时间单位类型并重新编译。
4. 评估 fixed 新 namespace 的重新计数窗口。
5. 如需动态策略，为每个可管理资源设置稳定 `resourceCode`。
6. 配置完整 `snapshot-url`，验证 ETag/304 和 last-known-good。
7. 通过事件中的 `policySource/policyRevision` 验证实际策略来源。
