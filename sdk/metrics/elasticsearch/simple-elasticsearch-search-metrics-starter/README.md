# simple-elasticsearch-search-metrics-starter

监听 ES 查询和聚合事件，采集 Micrometer 指标，支持 Prometheus 等监控系统。

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-elasticsearch-search-metrics-starter:1.0.0'
```

前提：项目中已引入 `simple-elasticsearch-search-starter`（其依赖的 `simple-elasticsearch-search-core` 版本 ≥ 1.0.10），它负责发布 `EsQueryEvent`、`EsAggEvent`、`EsQueryErrorEvent` 和 `EsAggErrorEvent`。

---

## 快速接入

### 启用指标采集

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        metrics:
          elasticsearch:
            search:
              enable: true
```

默认已启用，无需额外配置即可开始采集。

### 标签值说明

| 标签 | 说明 |
|------|------|
| eventType | 操作类型：`query` / `agg` |
| result | 操作结果：`success` / `failure` |
| sourceType | 来源端点：`QUERY_API` / `EXPRESSION_API` / `NL_API` / `unknown` |
| downgradeLevel | 降级级别，0=未降级 |
| me | 应用标识，默认取 `spring.application.name`，未配置则为 `unknown` |

---

## 指标列表

### 请求计数

`simple_elasticsearch_search_request_total`

| 维度 | 说明 |
|------|------|
| eventType | `query` / `agg` |
| result | `success` / `failure` |
| sourceType | `QUERY_API` / `EXPRESSION_API` / `NL_API` / `unknown` |
| downgradeLevel | 降级级别 |
| me | 应用标识 |

### 请求耗时

`simple_elasticsearch_search_request_seconds`

| 维度 | 说明 |
|------|------|
| eventType | `query` / `agg` |
| sourceType | `QUERY_API` / `EXPRESSION_API` / `NL_API` / `unknown` |
| downgradeLevel | 降级级别 |
| me | 应用标识 |

耗时取 ES 服务端返回的 `took` 值（毫秒），仅在 `success` 事件中记录。

---

## 配置项

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        metrics:
          elasticsearch:
            search:
              enable: true          # 是否启用指标采集，默认 true
              me: my-app            # 应用标识，未配置时取 spring.application.name
```

---

## 依赖说明

本模块 `compileOnly` Micrometer，因此项目中需要自行引入 Micrometer 实现。Spring Boot 应用通常已自带：

```gradle
// Prometheus 监控
implementation 'io.micrometer:micrometer-registry-prometheus'

// 或仅使用内存指标
implementation 'io.micrometer:micrometer-registry-statsd'
```

如使用 Spring Boot Actuator，只需引入 Actuator starter：

```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

---

## 注意事项

- 指标采集依赖 `EsQueryEvent`、`EsAggEvent` 等事件，**必须**先引入 `simple-elasticsearch-search-starter`
- 失败事件（`EsQueryErrorEvent`、`EsAggErrorEvent`）仅记录 Counter，不记录 Timer
- `sourceType` 由 `ExecutionContext.getSourceType()` 决定，未设置时为 `unknown`
- `me` 标签优先级：`spring.application.name` > 配置项 `me` > `unknown`