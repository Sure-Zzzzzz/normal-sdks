# Simple XFF Capture Audit ES Persistence Provider Jakarta Starter

面向需要把 X-Forwarded-For（XFF，代理转发地址链）审计事实写入 Elasticsearch 的 Spring Boot 3 Servlet 应用。本模块实现 Audit Core 的存储接口，接收 Listener 已生成的不可变审计文档，通过 Elasticsearch Persistence 写入；不重新读取 HTTP 请求，也不判断哪个地址是真实客户端 IP。

## 最小接入

应用同时引入采集、监听和本 Provider；Audit Core、Elasticsearch Persistence 与 Route 由模块依赖传递引入：

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-xff-capture-jakarta-starter:1.0.0'
    implementation 'io.github.sure-zzzzzz:simple-xff-capture-audit-listener-jakarta-starter:1.0.0'
    implementation 'io.github.sure-zzzzzz:simple-xff-capture-audit-es-persistence-provider-jakarta-starter:1.0.0'
}
```

下面以 Elasticsearch 7.17.16 为例。连接地址和服务端版本必须按部署环境填写；Route 的 `exact` 规则把固定逻辑索引映射为物理日索引：

```yaml
spring:
  application:
    name: sample-service

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
                async-write: false
          persistence:
            enable: true
```

三个 XFF 模块的开关默认关闭。Provider 启用后若缺少 `PersistenceEngine`（统一写入入口），应用启动会失败，不会静默退回仅记日志。Listener 仍同时调用默认日志 Provider；日志不是 ES 写入失败后的补偿。Capture 的 Query（URL 查询参数）、Form（表单参数）和 Body（请求体）快照仍由 Capture 自己的白名单、类型与大小配置控制，本模块不增加采集面。

## 写入结果

每个审计文档调用一次 `PersistenceEngine.index(IndexRequest)`，使用固定逻辑索引 `xff-capture-audit` 和 `eventId` 作为 Elasticsearch 文档 ID。Route 决定实际数据源与物理索引。Provider 使用自己的 JSON 序列化器保留 `xRealIpList` 等公开字段名，不使用宿主 Spring 容器的 `ObjectMapper`。

Listener 在线程池中异步调用 Provider；Provider 写入失败会交回 Listener 记录事件 ID、Provider 类型和异常类型，并继续调用其他 Provider，不传回原 HTTP 响应。本链路是尽力而为审计，没有自动重试或持久化队列。Provider 不创建 ES 客户端、执行器或索引模板，也不主动预建索引；首次写入由 ES 在 Route 选定的物理目标上创建索引。Provider 不提供查询、保留期和清理能力。DEBUG 日志只记录事件 ID、字段投影与写入阶段，不输出 XFF 原文、请求数据或认证信息。

## 索引模板

部署方须在首次写入前安装覆盖物理索引的 Composable Index Template（组合式索引模板），并负责认证、权限和现有索引迁移。下面是 ES 7.17+/8.x 的完整字段模板；提交至 `PUT /_index_template/xff-capture-audit`。如果物理索引命名不同，同步调整 `index_patterns`。

```json
{
  "index_patterns": ["xff-capture-audit-*"],
  "priority": 100,
  "template": {
    "mappings": {
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
        "xffRawHeaderList": {"type": "keyword"},
        "xffRawList": {"type": "keyword"},
        "xRealIpList": {"type": "keyword"},
        "xForwardedHostList": {"type": "keyword"},
        "xForwardedPortList": {"type": "keyword"},
        "xForwardedProtoList": {"type": "keyword"},
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
    }
  }
}
```

模板只影响后续新建的物理索引，不会改写已存在的日索引。原始 Header 是网络事实，固定映射为 `keyword`，不要把它们直接解释为可信 IP；规范化的 `xffIpList`、`publicIpList` 才映射为 `ip`。`extensions` 和请求参数的动态字段由模板映射为 `keyword`，便于精确查询。模板、存储访问权限和数据留存由宿主负责。

## 兼容边界

本模块面向 Spring Boot 3 / Jakarta Servlet，编译目标 Java 17，依赖 Elasticsearch Persistence Jakarta `1.0.0`；后者支持 Elasticsearch 7.17+ 与 8.x，不支持 6.x。Spring Boot 2 / javax Servlet 应使用 `simple-xff-capture-audit-es-persistence-provider-starter:1.1.1`，两条线不能在同一应用混用。WebFlux 不在范围内。

Spring Boot `3.4.2` 下使用 Java `17`、`21` 分别完成完整模块测试，每轮 8 个用例、零失败零跳过。真实链路分别连接 Elasticsearch `7.17.16` 与 `8.17.0` 两个独立单节点，验证 HTTP 请求、XFF/Query/Body 快照、Listener 分发、Persistence/Route 写入物理索引及精确查询；这不等同于多节点集群、网络故障或生产模板迁移验收。

## 兼容矩阵

| Spring Boot | Java | 验证范围 |
|---|---:|---|
| 3.4.2 | 17 / 21 | 事件模拟链全量测试（基线） |
| 3.3.13 | 17 | 事件模拟链全量测试 |
| 3.2.12 | 17 | 事件模拟链全量测试 |
