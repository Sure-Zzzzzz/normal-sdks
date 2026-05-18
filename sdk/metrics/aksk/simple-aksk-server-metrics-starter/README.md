# simple-aksk-server-metrics-starter

监听 `simple-aksk-server-starter` 发布的 Token 事件，采集 Micrometer 指标，对接 Prometheus / OTLP 等监控系统。

## 指标 vs 审计的分工

| | 指标（Metrics） | 审计事件（Audit） |
|---|---|---|
| 回答什么 | Token 签发/撤销量是多少 | 哪个具体 Token 被谁签发/撤销 |
| 维度粒度 | 规则级（低基数） | 请求级（高基数） |
| 用途 | 告警、看板、趋势 | 事后追溯、日志分析 |

---

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-server-starter:1.1.3'
implementation 'io.github.sure-zzzzzz:simple-aksk-server-metrics-starter:1.0.0'

// 需要自行引入 Micrometer Registry，例如 Prometheus
implementation 'io.micrometer:micrometer-registry-prometheus'
```

前提：项目中已引入 `simple-aksk-server-starter` 和 Micrometer Registry。

---

## 快速接入

引入依赖后自动生效，无需额外配置。只要 classpath 上存在 `MeterRegistry`，指标监听器就会自动注册。

---

## 指标列表

### smart_aksk_token_total

Token 操作计数（Counter）

| 标签 | 值域 | 说明 |
|------|------|------|
| `eventType` | `issued` / `revoked` / `introspected` / `removed` | Token 操作类型 |
| `clientType` | `platform` / `user` 等 | 客户端类型 |

---

## 指标示例

```
smart_aksk_token_total{eventType="issued",clientType="platform"} 312
smart_aksk_token_total{eventType="revoked",clientType="user"} 8
smart_aksk_token_total{eventType="introspected",clientType="platform"} 1523
smart_aksk_token_total{eventType="removed",clientType="platform"} 5
```

---

## Prometheus 看板关键查询

```promql
# 每分钟签发 Token 量
sum by (clientType) (rate(smart_aksk_token_total{eventType="issued"}[1m]))

# Token 撤销率（异常检测）
sum by (clientType) (rate(smart_aksk_token_total{eventType="revoked"}[5m]))
  / sum by (clientType) (rate(smart_aksk_token_total{eventType="issued"}[5m]))

# 各操作类型分布
sum by (eventType) (increase(smart_aksk_token_total[1h]))
```

---

## 配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `io.github.surezzzzzz.sdk.metrics.aksk.server.enable` | Boolean | true | 是否启用指标采集 |

关闭指标采集：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        metrics:
          aksk:
            server:
              enable: false
```

---

## 工作原理

1. `simple-aksk-server-starter` 在每次 Token 操作后发布 `AbstractTokenEvent`
2. `TokenMetricsListener` 监听事件，将操作类型和客户端类型写入指标
3. Micrometer Registry（Prometheus / OTLP 等）自动采集指标
4. 不引入 Micrometer 时，Listener 不注册，零侵入

---

## 版本历史

### 1.0.0
- 初始版本
- 核心指标：`smart_aksk_token_total`（标签：eventType / clientType）
- 低基数标签设计，避免 Prometheus 指标爆炸
- `micrometer-core` compileOnly 依赖，不引入则自动不注册
- 支持配置开关 `enable`
