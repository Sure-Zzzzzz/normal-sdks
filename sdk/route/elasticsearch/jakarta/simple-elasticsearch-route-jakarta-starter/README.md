# Simple Elasticsearch Route Jakarta Starter

在一个 Spring Boot 应用需要连接多个 Elasticsearch 集群或网关时，本组件按索引名称选择目标数据源。业务代码仍注入一个 `ElasticsearchOperations`（Spring Data 提供的读写接口），不用在每个调用处处理连接、认证或集群选择。

这里的“数据源”是一个 Elasticsearch 集群或网关连接；“索引”是 Elasticsearch 中存放同类文档的名称。一次操作只会发送到一个数据源，避免跨集群结果被错误拼接。

## 适用范围

- Spring Boot `3.4.2`、Java `17+` 应用，使用 Spring Data Elasticsearch 5 的 ELC（Elastic Java Client，Elasticsearch 官方 Java HTTP 客户端）访问 Elasticsearch。
- 需要按索引把在线数据、归档数据或不同业务域写入不同 Elasticsearch 服务的应用。
- 只连接一个 Elasticsearch 服务的应用通常直接使用 Spring Boot 默认配置即可，无需引入本组件。

本模块与 javax 线 `simple-elasticsearch-route-starter` 二选一，不能在同一应用同时引入。

## 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-elasticsearch-route-jakarta-starter:1.0.0'
    implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'
}
```

## 最小接入

以下配置登记两个数据源：普通索引写入 `primary`，以 `archive-` 开头的索引写入 `archive`。将示例地址和版本替换为实际服务的地址及精确版本。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          route:
            enable: true
            default-source: primary

            sources:
              primary:
                urls: https://es-primary.example.test:9200
                server-version: 8.17.0
              archive:
                urls: https://es-archive.example.test:9200
                server-version: 7.17.16

            rules:
              - pattern: "archive-*"
                datasource: archive
                type: wildcard
                priority: 10
```

`default-source` 必须是 `sources` 中已登记的键。没有命中规则的索引使用该默认数据源；上例中 `customer-profile` 会进入 `primary`，`archive-order` 会进入 `archive`。

`urls` 只接受协议、主机和端口，例如 `https://es-primary.example.test:9200`。网关子路径使用 `path-prefix` 配置，不能写入 `urls`；包含路径、账号、查询参数或片段的 URL 会在启动时被拒绝。多个同一集群节点可用逗号分隔。

需要 Basic（用户名和密码）认证时，把凭据交给环境变量或受管配置，不要提交到代码库：

```yaml
sources:
  archive:
    urls: ${ES_ARCHIVE_URLS}
    username: ${ES_ARCHIVE_USERNAME}
    password: ${ES_ARCHIVE_PASSWORD}
```

## 路由规则

规则按 `priority` 从小到大匹配，首个命中的规则决定数据源。应为可能重叠的规则设置不同优先级，不依赖配置文件中的先后顺序。

| `type` | `pattern` 示例 | 命中条件 |
| --- | --- | --- |
| `exact` | `customer-profile` | 索引名完全相同 |
| `prefix` | `customer-` | 索引名以该文本开头 |
| `suffix` | `-archive` | 索引名以该文本结尾 |
| `wildcard` | `archive-*` | 通配符；`*` 匹配任意文本，`**` 可跨路径分隔符 |
| `regex` | `archive-\\d+` | Java 正则表达式必须完整匹配索引名 |

规则未命中时不会报错，而是使用 `default-source`。同一次 Spring Data 调用若解析出多个索引且它们命中不同数据源，会在发送网络请求前失败；批量写入多个索引同样会被拒绝。

## 业务调用

自动配置会注册一个主 `ElasticsearchOperations`。索引名来自显式 `IndexCoordinates`（索引名对象）、查询对象或实体的 `@Document` 注解，其中显式 `IndexCoordinates` 优先级最高。

### 按实体索引路由

```java
@Document(indexName = "archive-order")
public class ArchiveOrder {
    @Id
    private String id;
}

@Service
public class ArchiveOrderService {

    private final ElasticsearchOperations operations;

    public ArchiveOrderService(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    public ArchiveOrder save(ArchiveOrder order) {
        return operations.save(order);
    }
}
```

上例的 `archive-order` 命中最小配置中的规则，因此 `save` 会使用 `archive` 数据源。业务代码不需要也不能在这次调用中覆盖目标主机。

### 显式指定索引

需要运行时决定索引名时，传入 `IndexCoordinates`。它决定路由所用的索引名，不是直接指定连接地址。

```java
ArchiveOrder saved = operations.save(
        order,
        IndexCoordinates.of("archive-order"));
```

### 按日期分片

日期分片把一个逻辑索引的写入按日期落到多个物理索引。下面的逻辑索引 `audit-event` 会写入形如 `audit-2026.10.04` 的索引，读操作则覆盖所有 `audit-*` 分片。`zone-id` 使用 Java 时区名称，建议显式设置，避免部署机器时区不同导致日期边界不一致。

```yaml
rules:
  - pattern: "audit-event"
    datasource: archive
    type: exact
    priority: 20
    write-index:
      template: "audit-{yyyy.MM.dd}"
      zone-id: UTC
    read-index:
      pattern: "audit-*"
```

日期分片下，`get(id, Entity.class)` 会在读索引模式中按 ID 搜索；若同一 ID 出现在多个分片，操作明确失败而不会任意返回其中一个。无法安全改写为目标索引的调用也会失败，不会悄悄回退到实体原始索引。`indexOps(Class)` 保留实体映射，但不能用于管理配置了日期分片的索引；此时传入实际 `IndexCoordinates`，并按 Spring Data API 创建映射。

### 使用受管 HTTP 客户端

Spring Data 未覆盖的 HTTP API 可以从注册表取得对应数据源的 `RestClient`。该 HTTP 客户端的连接和关闭生命周期由组件管理，调用方不得关闭它。

```java
RestClient archiveClient = routeRegistry.getLowLevelClient("archive");
Response response = archiveClient.performRequest(new Request("GET", "/_cluster/health"));
```

需要 `reindex`、SQL 或脚本 API 时，先在业务侧确定唯一数据源，再通过该数据源的 low-level client 调用；这些 API 不经过路由代理。

## 关键配置

| 配置项 | 作用 |
| --- | --- |
| `default-source` | 未命中规则时使用的数据源，必须已在 `sources` 中声明。 |
| `sources.<key>.urls` | 一个数据源的 Elasticsearch 地址；推荐使用完整 `http` 或 `https` URL。 |
| `sources.<key>.server-version` | 目标 Elasticsearch 的精确版本。配置且探测成功时，版本不一致会拒绝启动。 |
| `sources.<key>.username` / `password` | 可选 Basic 认证凭据，推荐由环境变量注入。 |
| `sources.<key>.connect-timeout` / `socket-timeout` | 连接超时和读取超时，单位为毫秒。 |
| `sources.<key>.path-prefix` | Elasticsearch 位于网关子路径后的前缀，例如 `/search`。 |
| `sources.<key>.enable-connection-reuse` | 是否复用 HTTP 连接，默认 `true`。 |
| `version-detect.enabled` | 是否在启动期请求服务端根路径探测版本，默认 `true`。 |
| `version-detect.fail-fast-on-detect-error` | 已配置 `server-version` 且探测连接失败时是否阻断启动，默认 `false`；未配置 `server-version` 时探测失败始终阻断启动。 |
| `rules[].write-index.template` | 写入日期分片的名称模板，例如 `audit-{yyyy.MM.dd}`。 |
| `rules[].write-index.zone-id` | 当前规则的日期时区；未配置时继承全局 `write-index.zone-id`，再回退运行进程的 Java 默认时区。 |
| `rules[].read-index.pattern` | 读操作使用的索引模式，例如 `audit-*`。 |
| `rules[].async-write` | 异步提交写操作并立即返回 `null`，默认 `false`。 |

HTTPS 默认使用标准 TLS 证书校验。`skip-ssl-validation` 仅能用于受控开发环境，生产环境不得关闭证书校验。

## 行为边界

- 服务端兼容范围是 Elasticsearch `7.17+` 和 `8.x`。Elasticsearch `6.x` 与 `9.x` 不属于 Jakarta 线的支持范围；需要 ES 6 时使用 javax 线。
- 本组件在应用中尚未存在 `ElasticsearchOperations` Bean（Spring 容器中的已注册对象）时才会装配。应用若自行注册该 Bean，Route 会整体让位，不创建路由连接或代理。
- `async-write` 适合日志、埋点等允许少量丢失的写入。队列满、提交失败或后台执行失败都会丢弃该任务，不能用于必须确认写入结果的业务。
- 路由代理不支持 `reindex`、`submitReindex`、Spring Data SQL 和脚本 API，因为这些调用无法安全解析唯一索引。按数据源拆分后使用受管 `RestClient`。
- PIT（point in time，查询快照）打开和关闭必须在同一应用实例完成；打开记录丢失时，关闭操作会失败，不会尝试默认数据源。
- 服务端版本探测只校验兼容性，不会把客户端 API 自动降级为旧版。
