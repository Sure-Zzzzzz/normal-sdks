# simple-xff-capture-audit-es-persistence-provider-starter

XFF Capture Audit 的可选 Elasticsearch Persistence Provider。它实现 Audit Core 的 Provider SPI，由 Listener 与默认日志 Provider 一起广播调用。

## 接入

业务应用需要引入 XFF Capture、Audit Listener 和本 Provider。Audit Core 由 Listener 的传递依赖带入；Elasticsearch Persistence 与 Route 由本 Provider 的传递依赖带入：

```gradle
implementation 'io.github.sure-zzzzzz:simple-xff-capture-starter:1.1.0'
implementation 'io.github.sure-zzzzzz:simple-xff-capture-audit-listener-starter:1.1.0'
implementation 'io.github.sure-zzzzzz:simple-xff-capture-audit-es-persistence-provider-starter:1.1.0'
```

### 最小可运行配置

下面的配置只保留启动真实写入链路所需的字段，适合本地验证或没有特殊线程池、连接池要求的应用。示例使用 Route 默认数据源键 `primary`；业务也可统一替换为自己的数据源键。`urls` 必须指向实际可访问的 Elasticsearch，`server-version` 必须与服务端版本一致：

```yaml
spring:
  application:
    name: xff-capture-audit-service

io:
  github:
    surezzzzzz:
      sdk:
        http:
          xff:
            capture:
              enable: true
        audit:
          http:
            xff:
              capture:
                listener:
                  enable: true
                persistence:
                  elasticsearch:
                    enable: true
        elasticsearch:
          route:
            enable: true
            sources:
              primary:
                urls: http://localhost:9200
                server-version: 7.17.16
            rules:
              - pattern: xff-capture-audit
                type: exact
                datasource: primary
                write-index:
                  template: xff-capture-audit-{yyyy.MM.dd}
          persistence:
            enable: true
```

### 完整配置

下面的配置展示所有本模块接入链路的常用配置项，包括 Listener 执行器、Route 连接参数、日索引时区和同步写入。生产环境应通过环境变量或启动参数覆盖连接地址、版本等部署值：

```yaml
spring:
  application:
    name: xff-capture-audit-service

io:
  github:
    surezzzzzz:
      sdk:
        http:
          xff:
            capture:
              enable: true
        audit:
          http:
            xff:
              capture:
                listener:
                  enable: true
                  executor:
                    core-size: 1
                    max-size: 2
                    queue-capacity: 100
                    keep-alive-seconds: 10
                    await-termination-seconds: 5
                persistence:
                  elasticsearch:
                    enable: true
        elasticsearch:
          route:
            enable: true
            default-source: primary
            proxy-type: AUTO
            version-detect:
              enabled: true
              fail-fast-on-detect-error: false
              timeout-ms: 1500
            write-index:
              zone-id: Asia/Shanghai
            sources:
              primary:
                urls: ${ELASTICSEARCH_URL:http://localhost:9200}
                server-version: ${ELASTICSEARCH_VERSION:7.17.16}
                username: ${ELASTICSEARCH_USERNAME:}
                password: ${ELASTICSEARCH_PASSWORD:}
                connect-timeout: 5000
                socket-timeout: 60000
                use-ssl: false
                skip-ssl-validation: false
                proxy-host: ${ELASTICSEARCH_PROXY_HOST:}
                proxy-port: ${ELASTICSEARCH_PROXY_PORT:}
                path-prefix: ${ELASTICSEARCH_PATH_PREFIX:}
                keep-alive-strategy: 300
                max-conn-total: 100
                max-conn-per-route: 10
                enable-connection-reuse: true
                async-write-thread-pool-size: 8
            rules:
              - pattern: xff-capture-audit
                type: exact
                priority: 10
                enable: true
                datasource: primary
                write-index:
                  template: xff-capture-audit-{yyyy.MM.dd}
                  zone-id: Asia/Shanghai
                async-write: false
          persistence:
            enable: true
            async:
              core-size: 4
              max-size: 16
              queue-capacity: 1000
```

`persistence.elasticsearch.enable` 只控制本 Provider；它不会创建 Elasticsearch Client、Route 或 PersistenceEngine。启用后必须同时启用 Elasticsearch Persistence 与 Route；若缺少 `PersistenceEngine`，应用会在启动时明确失败，避免审计写入被静默跳过。未启用本 Provider 时，Listener 默认日志 Provider 仍可独立工作。

Capture 的 Query、Form、Body 请求数据采集开关、URI 规则、Content-Type 约束和截断策略均配置在 `simple-xff-capture-starter` 中；本 Provider 不重新采集 HTTP 请求，也不定义第二套请求数据配置。Listener 直接把 Event 中已经完成的不可变 `requestData` 快照交给本 Provider，本 Provider 通过通用 `PersistenceEngine` 原样序列化到审计文档。

## 写入边界

Provider 同步调用 `PersistenceEngine.index(IndexRequest)`，固定提交：

```text
logical index = xff-capture-audit
_id           = document.eventId
```

真实链路为：

```text
Audit Document
  -> Elasticsearch Provider
  -> PersistenceEngine
  -> Route exact rule
  -> write-index.template
  -> Elasticsearch physical daily index
```

Provider 不创建执行器、Elasticsearch Client、模板或物理日期索引，不读取 Route 内部状态，不提供 Search、读索引或清理能力。`pattern` 必须保持对 `xff-capture-audit` 的 `exact` 匹配，不能改为 wildcard。

## 业务扩展字段

Audit Document 固定使用顶层 `extensions` 信封承载业务字段，类型为 `Map<String, String>`。业务应在同步上下文中提供已经脱敏的字符串扩展值；默认日志 Provider 不会输出它们。

生产模板的根级 mapping 保持 Elasticsearch 默认宽松 dynamic 行为，不使用 `dynamic: strict` 阻断未来字段；但 Audit Document 和 `requestData` 的已知字段必须显式定义类型。未知业务扩展、Query 参数名和 Form 参数名仍由 dynamic 自动建立字段，并通过 dynamic template 映射为 `keyword`，满足请求参数值的精确查询语义。

```json
"dynamic_templates": [
  {
    "request_parameter_values_as_keyword": {
      "path_match": "requestData.*Parameters.values.*",
      "match_mapping_type": "string",
      "mapping": {"type": "keyword", "ignore_above": 32766}
    }
  },
  {
    "extension_values_as_keyword": {
      "path_match": "extensions.*",
      "match_mapping_type": "string",
      "mapping": {"type": "keyword", "ignore_above": 32766}
    }
  }
],
"properties": {
  "eventId": {"type": "keyword"},
  "capturedTime": {"type": "date"},
  "applicationName": {"type": "keyword"},
  "requestId": {"type": "keyword"},
  "traceId": {"type": "keyword"},
  "requestMethod": {"type": "keyword"},
  "requestUri": {"type": "keyword"},
  "hostList": {"type": "keyword"},
  "xffPresent": {"type": "boolean"},
  "xffRawList": {"type": "keyword"},
  "xffIpList": {"type": "ip"},
  "publicIpList": {"type": "ip"},
  "applicationRawRemoteAddress": {"type": "keyword"},
  "applicationRemoteIp": {"type": "ip"},
  "classificationVersion": {"type": "keyword"},
  "extensions": {"type": "object", "properties": {}},
  "requestData": {
    "type": "object",
    "properties": {
      "queryParameters": {
        "type": "object",
        "properties": {
          "status": {"type": "keyword"},
          "values": {"type": "object", "properties": {}}
        }
      },
      "formParameters": {
        "type": "object",
        "properties": {
          "status": {"type": "keyword"},
          "values": {"type": "object", "properties": {}}
        }
      },
      "body": {
        "type": "object",
        "properties": {
          "status": {"type": "keyword"},
          "contentType": {"type": "keyword"},
          "declaredContentLength": {"type": "long"},
          "capturedByteCount": {"type": "long"},
          "text": {"type": "keyword", "ignore_above": 32766}
        }
      }
    }
  }
}
```

`requestData` 的固定 Query、Form、Body 结构使用上面的显式 mapping；其中 `values` 下的参数名和参数值仍按宽松 dynamic 写入，但字符串参数值由 dynamic template 映射为 `keyword`，可直接使用 `term` 精确查询。Body `text` 同样使用 `keyword` 并设置 `ignore_above: 32766`：长度在限制内时可精确查询，超出限制时仍保留在 `_source` 但不建立倒排索引。`extensions` 容器已显式定义为 `object`，其业务键也映射为可精确查询的 `keyword`。不要将业务扩展平铺到审计文档顶层，也不要使用 `flattened`，以保持 Elasticsearch 6/7 兼容。

## 模板与 E2E

Legacy Index Template、ES 6/7 的安装命令和真实 HTTP 到 Elasticsearch E2E 均属于本模块测试边界。测试前由被忽略的 `LOCAL_TEST_COMMANDS.md` 预先安装模板；模板显式定义 Audit Document 和 `requestData` 已知字段，根文档、参数动态键和业务扩展保持宽松 dynamic。E2E 不创建、删除或修复模板，也不预建物理日期索引。首次写入必须由 Persistence/Route 根据 exact rule 自动创建测试物理日索引。

真实 E2E 覆盖随机端口 HTTP POST、Query 多值、JSON Body 原样回显、Capture 请求数据快照投影、Event → Listener → Persistence → Route → Elasticsearch 完整写入链路、请求参数值/Body/扩展值的精确查询，以及 ES 6/7 兼容的固定 mapping 查询。
