# simple-xff-capture-audit-es-persistence-provider-starter

XFF Capture Audit 的可选 Elasticsearch Persistence Provider。它实现 Audit Core 的 Provider SPI，由 Listener 与默认日志 Provider 一起广播调用。

## 接入

业务应用需要引入 XFF Capture、Audit Listener 和本 Provider。Elasticsearch Persistence 与 Route 由本 Provider 的传递依赖带入：

```gradle
implementation 'io.github.sure-zzzzzz:simple-xff-capture-starter:1.0.0'
implementation 'io.github.sure-zzzzzz:simple-xff-capture-audit-listener-starter:1.0.0'
implementation 'io.github.sure-zzzzzz:simple-xff-capture-audit-es-persistence-provider-starter:1.0.0'
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

`persistence.elasticsearch.enable` 只控制本 Provider；它不会创建 Elasticsearch Client、Route 或 PersistenceEngine。启用后必须同时接入 Elasticsearch Persistence；若缺少 `PersistenceEngine`，应用会在启动时明确失败，避免审计写入被静默跳过。未启用本 Provider 时，Listener 默认日志 Provider 仍可独立工作。

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

生产模板的根级 mapping 保持 `dynamic: strict`，并显式声明 `extensions`：

```json
"extensions": {
  "type": "object",
  "dynamic": false,
  "properties": {
    "clientId": {
      "type": "keyword"
    }
  }
}
```

`extensions.clientId` 因显式 mapping 可查询。未声明的扩展子字段仍会写入 `_source`，但不会自动建立 mapping 或可查询字段。不要将业务扩展平铺到审计文档顶层，也不要使用 `flattened`，以保持 Elasticsearch 6/7 兼容。

## 模板与 E2E

Legacy Index Template、ES 6/7 的安装命令和真实 HTTP 到 Elasticsearch E2E 均属于本模块测试边界。测试前由被忽略的 `LOCAL_TEST_COMMANDS.md` 预先安装模板；E2E 不创建、删除或修复模板，也不预建物理日期索引。首次写入必须由 Persistence/Route 根据 exact rule 自动创建测试物理日索引。
