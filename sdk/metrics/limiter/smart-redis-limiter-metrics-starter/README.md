# smart-redis-limiter-metrics-starter

监听 `smart-redis-limiter-starter` 发布的限流事件，采集 Micrometer 指标，对接 Prometheus / OTLP 等监控系统。

## 指标 vs 审计的分工

| | 指标（Metrics） | 审计事件（Audit） |
|---|---|---|
| 回答什么 | 哪个规则拒绝了多少 | 哪个具体 key/IP 被拒绝 |
| 维度粒度 | 规则级（低基数） | 请求级（高基数） |
| 用途 | 告警、看板、趋势 | 事后追溯、日志分析 |

---

## 依赖

```gradle
implementation 'io.github.surezzzzzz:smart-redis-limiter-starter:1.1.3'
implementation 'io.github.surezzzzzz:smart-redis-limiter-metrics-starter:1.0.0'

// 需要自行引入 Micrometer Registry，例如 Prometheus
implementation 'io.micrometer:micrometer-registry-prometheus'
```

前提：项目中已引入 `smart-redis-limiter-starter` 和 Micrometer Registry。

---

## 快速接入

引入依赖后自动生效，无需额外配置。只要 classpath 上存在 `MeterRegistry`，指标监听器就会自动注册。

---

## 指标列表

### smart_rate_limit_total

限流检查计数（Counter）

| 标签 | 值域 | 说明 |
|------|------|------|
| `result` | `passed` / `rejected` | 限流结果 |
| `algorithm` | `fixed` / `sliding` | 限流算法 |
| `source` | `annotation` / `interceptor` | 来源模式 |
| `rule` | 路径模式 / 方法名 / `default` | 规则标识 |

### smart_rate_limit_fallback_total

降级触发计数（Counter）

| 标签 | 值域 | 说明 |
|------|------|------|
| `algorithm` | `fixed` / `sliding` | 限流算法 |
| `source` | `annotation` / `interceptor` | 来源模式 |
| `strategy` | `allow` / `deny` | 降级策略 |

### smart_rate_limit_command_seconds

Redis 命令耗时分布（Timer）

| 标签 | 值域 | 说明 |
|------|------|------|
| `algorithm` | `fixed` / `sliding` | 限流算法 |
| `source` | `annotation` / `interceptor` | 来源模式 |

---

## `rule` 标签说明

| 场景 | rule 值 | 示例 |
|------|---------|------|
| 拦截器模式 + 有匹配规则 | `matchedPathPattern` | `/api/user/**` |
| 拦截器模式 + 无匹配规则 | `default` | `default` |
| 注解模式 | `methodQualifiedName` | `com.example.UserService.getUser` |

**为什么不用 Redis key 作为 rule？** — `path` 策略含动态参数（`/api/user/123`）基数不可控，`ip` 策略基数完全不可控。规则维度是配置级的，基数可控。具体哪个 key/IP 被拒绝，查审计事件。

---

## 指标示例

```
smart_rate_limit_total{result="passed",algorithm="sliding",source="interceptor",rule="/api/user/**"} 1498
smart_rate_limit_total{result="rejected",algorithm="sliding",source="interceptor",rule="/api/user/**"} 25
smart_rate_limit_fallback_total{algorithm="fixed",source="annotation",strategy="allow"} 3
smart_rate_limit_command_seconds_count{algorithm="sliding",source="interceptor"} 1523
smart_rate_limit_command_seconds_sum{algorithm="sliding",source="interceptor"} 1.234
```

---

## Prometheus 看板关键查询

```promql
# 限流拒绝率（按规则）
sum by (rule) (rate(smart_rate_limit_total{result="rejected"}[5m]))
  / sum by (rule) (rate(smart_rate_limit_total[5m]))

# 各规则拒绝量排名
topk(10, sum by (rule) (increase(smart_rate_limit_total{result="rejected"}[1h])))

# Redis 命令 P99
histogram_quantile(0.99, rate(smart_rate_limit_command_seconds_bucket[5m]))

# 降级触发次数
increase(smart_rate_limit_fallback_total[1h])
```

---

## 配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `io.github.surezzzzzz.sdk.metrics.limiter.enable` | Boolean | true | 是否启用指标采集 |

关闭指标采集：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        metrics:
          limiter:
            enable: false
```

---

## 工作原理

1. `smart-redis-limiter-starter` 在限流检查后发布 `SmartRedisLimiterEvent`
2. `SmartRedisLimiterMetricsListener` 监听事件，将数据写入 Micrometer 指标
3. Micrometer Registry（Prometheus / OTLP 等）自动采集指标
4. 不引入 Micrometer 时，Listener 不注册，零侵入

---

## 版本历史

### 1.0.0
- 初始版本
- 三个核心指标：`smart_rate_limit_total` / `smart_rate_limit_fallback_total` / `smart_rate_limit_command_seconds`
- 低基数标签设计，避免 Prometheus 指标爆炸
- `micrometer-core` compileOnly 依赖，不引入则自动不注册
- 支持配置开关 `enable`
