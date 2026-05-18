# simple-aksk-resource-server-metrics-starter

监听 `simple-aksk-resource-server-starter` 发布的认证事件，采集 Micrometer 指标，对接 Prometheus / OTLP 等监控系统。

## 指标 vs 审计的分工

| | 指标（Metrics） | 审计事件（Audit） |
|---|---|---|
| 回答什么 | 认证成功率是多少、耗时多少 | 哪个具体请求被谁认证 |
| 维度粒度 | 规则级（低基数） | 请求级（高基数） |
| 用途 | 告警、看板、趋势 | 事后追溯、日志分析 |

---

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:1.0.6'
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-metrics-starter:1.0.0'

// 需要自行引入 Micrometer Registry，例如 Prometheus
implementation 'io.micrometer:micrometer-registry-prometheus'
```

前提：项目中已引入 `simple-aksk-resource-server-starter` 和 Micrometer Registry。

---

## 快速接入

引入依赖后自动生效，无需额外配置。只要 classpath 上存在 `MeterRegistry`，指标监听器就会自动注册。

---

## 指标列表

### smart_aksk_access_total

AKSK 认证请求计数（Counter）

| 标签 | 值域 | 说明 |
|------|------|------|
| `result` | `success` / `fail` | 认证结果 |
| `clientType` | `platform` / `user` 等 | 客户端类型 |
| `source` | `header` / `jwt` 等 | 认证来源 |

### smart_aksk_authenticate_seconds

认证耗时分布（Timer）

| 标签 | 值域 | 说明 |
|------|------|------|
| `clientType` | `platform` / `user` 等 | 客户端类型 |
| `source` | `header` / `jwt` 等 | 认证来源 |

> Timer 仅在事件携带 `durationNanos` 时记录，未携带则不注册。

---

## 指标示例

```
smart_aksk_access_total{result="success",clientType="platform",source="header"} 1523
smart_aksk_access_total{result="fail",clientType="user",source="jwt"} 7
smart_aksk_authenticate_seconds_count{clientType="platform",source="header"} 1523
smart_aksk_authenticate_seconds_sum{clientType="platform",source="header"} 0.456
```

---

## Prometheus 看板关键查询

```promql
# 认证失败率（按来源）
sum by (source, clientType) (rate(smart_aksk_access_total{result="fail"}[5m]))
  / sum by (source, clientType) (rate(smart_aksk_access_total[5m]))

# 认证 P99 耗时
histogram_quantile(0.99, rate(smart_aksk_authenticate_seconds_bucket[5m]))

# 各 clientType 每分钟认证量
sum by (clientType) (rate(smart_aksk_access_total[1m]))
```

---

## 配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `io.github.surezzzzzz.sdk.metrics.aksk.resource.enable` | Boolean | true | 是否启用指标采集 |

关闭指标采集：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        metrics:
          aksk:
            resource:
              enable: false
```

---

## 工作原理

1. `simple-aksk-resource-server-starter` 在每次认证请求后发布 `AkskAccessEvent`
2. `AkskAccessMetricsListener` 监听事件，将计数和耗时写入指标
3. Micrometer Registry（Prometheus / OTLP 等）自动采集指标
4. 不引入 Micrometer 时，Listener 不注册，零侵入

---

## 版本历史

### 1.0.0
- 初始版本
- 两个核心指标：`smart_aksk_access_total` / `smart_aksk_authenticate_seconds`
- 低基数标签设计，避免 Prometheus 指标爆炸
- `micrometer-core` compileOnly 依赖，不引入则自动不注册
- 支持配置开关 `enable`
