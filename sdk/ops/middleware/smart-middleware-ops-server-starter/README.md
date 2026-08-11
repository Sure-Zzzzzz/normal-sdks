# smart-middleware-ops-server-starter

内部中间件只读运维 Server SDK。使用方配置既有的 Elasticsearch、Redis、Kafka、MySQL Route Starter 后，引入本 Starter 即可提供统一的受控观察页面和 HTTP 接口。

一期不提供任意协议透传、原始中间件请求、业务数据读取或任何写操作。

## 适用范围与兼容性

| 项目 | 说明 |
| --- | --- |
| Java | 源码与产物兼容 Java 8。 |
| Spring Boot | 当前仅以 `2.7.9` 作为开发、测试和运行验证基线；不支持 Spring Boot 3.x。 |
| 本地构建工具 | Gradle 8.5 使用 Java 11 工具链编译和测试；这不是部署 JDK 要求。 |
| 中间件能力 | Elasticsearch、Redis、Kafka、MySQL 的安全状态概览与受控只读查询。 |
| 审计存储 | Elasticsearch 6.2.2 兼容的 legacy index template 与按 UTC 分割的日索引。 |

## 接入顺序

按下面顺序部署，避免启动后才发现审计无法使用：

1. 引入 Ops Starter 和实际启用中间件的运行时实现。
2. 按各 Route Starter 的公开配置方式登记数据源；Ops 不读取或复制 Route 的物理连接配置。
3. 在 Elasticsearch 创建一次审计日索引模板，并配置审计逻辑索引的 Route 与 Search 读侧。
4. 配置 Ops 的路径、查询上限和并发预算。
5. 启动应用，登录 `/middleware-ops/login`，执行一次人工操作并确认审计页面可查到该记录。

## 1. 引入依赖

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:smart-middleware-ops-server-starter:1.0.0'

    // Ops 以 compileOnly 声明中间件实现；按实际启用能力提供运行时实现
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.kafka:spring-kafka'
    runtimeOnly 'mysql:mysql-connector-java'
}
```

四个 Route Starter、Elasticsearch Search/Persistence、Thymeleaf 与 Spring Security 都由 Ops Starter 通过 `api` 传递引入，使用方只直接引入 Ops Starter，不重复声明这些依赖。上例同时启用四类能力；未启用的中间件实现可以移除。MySQL 启用时保留 JDBC Starter 与实际数据库驱动。

`spring-boot-autoconfigure`、`spring-boot-starter-web`、`spring-boot-starter-validation` 与 `spring-boot-configuration-processor` 是模块构建期的 `compileOnly` 依赖，不需要由使用方额外声明。

## 2. 配置 Route 与 Ops

先按各 Route Starter 的公开配置方式配置要观察的数据源。Ops 仅从 Route 已登记的数据源目录读取安全快照；不会把 JDBC URL、地址、账号、密码或 Route 配置暴露为接口参数、目录数据或审计内容。

### Ops 基础配置

```yaml
io.github.surezzzzzz.sdk.ops.middleware:
  enable: true
  api-base-path: /api/v1/middleware-ops
  ui-base-path: /middleware-ops
  query:
    default-size: 50
    max-size: 200
    max-dsl-length: 8192
    max-sql-length: 8192
    max-columns: 40
    max-cell-length: 1024
    max-response-length: 4096
    deadline-millis: 5000
  concurrency:
    global: 16
    datasource: 4
```

`default-size` 是页面首次查询的默认窗口；`max-size` 是单次读取的硬上限。页面只在浏览器内存中对已经返回且受硬上限限制的数据分页，不提供 cursor、续页令牌或后端查询会话。

### 用户系统：Windows AD / LDAP

Ops Server 不自建用户表，也不保存用户密码。启用 LDAP 后，Starter 使用配置的管理账号搜索用户，再使用用户自己的密码执行 LDAP Bind；认证成功后建立 Spring Security 会话。

```yaml
io.github.surezzzzzz.sdk.ops.middleware:
  ldap:
    enabled: true
    url: ldaps://ad.example.com:636/DC=example,DC=com
    manager-dn: CN=middleware-ops-reader,OU=Service Accounts,DC=example,DC=com
    manager-password: ${MIDDLEWARE_OPS_LDAP_MANAGER_PASSWORD}
    user-search-base: OU=Users
    user-search-filter: (sAMAccountName={0})
```

| 配置项 | 必填 | 说明 |
| --- | ---: | --- |
| `ldap.enabled` | 是 | 设为 `true` 才启用 Starter 自动注册的 LDAP 认证；默认 `false`。 |
| `ldap.url` | 是 | LDAP/LDAPS 地址，必须包含搜索根 DN，例如 `ldaps://ad.example.com:636/DC=example,DC=com`。生产优先使用 LDAPS。 |
| `ldap.manager-dn` | 是 | 仅用于搜索用户的服务账号 DN，不是登录用户账号。 |
| `ldap.manager-password` | 是 | 管理账号密码，只从环境变量、密钥服务或受控配置注入。 |
| `ldap.user-search-base` | 否 | 相对于 URL 根 DN 的用户搜索基准，例如 `OU=Users`；默认空值表示从根 DN 搜索。 |
| `ldap.user-search-filter` | 否 | 用户搜索过滤器，登录名替换 `{0}`；Windows AD 通常使用 `(sAMAccountName={0})`。普通 LDAP 可按目录实际字段改为 `(uid={0})`。 |

认证入口和边界如下：

- 浏览器登录：`GET /middleware-ops/login` 展示自有登录页，提交到同一路径；成功后建立会话并进入 `/middleware-ops`。
- API 认证：`/api/v1/middleware-ops/**` 使用 HTTP Basic；未认证返回 JSON `401`，不会跳转 HTML 登录页。
- 页面与 API 使用同一个 LDAP `AuthenticationProvider`；会话固定保护使用 Spring Security 的 session fixation migration。
- 用户认证成功后，一期默认授权策略允许其访问已发布的只读能力；LDAP 组不会被 Starter 猜测、映射或转化为业务权限。需要按用户、组或数据范围授权时，由使用方提供 `MiddlewareOpsAuthorizationPolicy` 或接入 IAM。
- `manager-dn` 仅用于查找用户，不会作为操作者身份写入审计；审计主体来自实际登录用户的 Spring Security 身份。

如果 `ldap.enabled=false`，Starter 不会自动创建 LDAP 认证提供器；使用方必须提供自己的 Spring Security 认证链，并确保页面和 API 仍满足本 SDK 的认证边界。不要把 `manager-password`、用户密码或 LDAP 原始响应写入日志、审计、前端或提交配置。

### MySQL Route 示例

MySQL Route 1.1.1 使用扁平 `datasources` 映射，且必须指定 `primary-datasource`。映射键就是全部 MySQL 接口中的 `{datasourceKey}`；名称只能包含字母、数字、`-` 与 `_`，且必须以字母或数字开始。

```yaml
io.github.surezzzzzz.sdk.mysql.route:
  enable: true
  primary-datasource: ops-primary
  datasources:
    ops-primary:
      url: jdbc:mysql://mysql-primary.test:3306/sample_ops
      username: middleware_ops_reader
      password: <由部署平台保管的随机密码>
    ops-secondary:
      url: jdbc:mysql://mysql-secondary.test:3306/sample_ops
      username: middleware_ops_reader
      password: <由部署平台保管的随机密码>
```

`primary-datasource` 只满足 Route 的基础设施默认路由要求，不能代替操作者选择。Ops 的每次 MySQL 读操作都在 Route `executeOn(datasourceKey, ...)` 显式作用域内执行。

## 3. Elasticsearch 审计准备（启动前必须完成）

Ops 的人工操作会异步写入 Elasticsearch 审计记录，审计页面也从该索引读取。因此在启动 Ops Server **之前**，部署方必须在 Ops 所使用的 Elasticsearch 集群准备模板、写入 Route 和 Search 读侧配置。

未准备模板时，主操作不会因为异步审计写入失败而中断，但审计记录不可用，不能视为合格部署。

### 3.1 先理解一次性模板与每日物理索引

- 需要手工创建的是一次性的 **legacy index template**，固定名称为 `middleware-ops-audit-template`。
- 不需要每天手工创建物理索引。首次审计写入会按 UTC 日期自动使用 `middleware-ops-audit-YYYY.MM.DD`。
- Server 不创建、轮转、删除物理索引，不维护 alias，也不决定分片、副本或保留期限；这些由部署侧治理。

### 3.2 创建并验证模板

1. 新建文件 `middleware-ops-audit-template.json`，内容如下。将分片、副本数调整为实际集群容量和可用性要求。
2. 将下方命令中的 `localhost:9200` 换成 **Ops Elasticsearch Route 实际连接的集群地址**，补充该集群所需认证。
3. 在启动 Ops Server 前执行 `PUT`，再执行 `GET`。只有 `GET` 能返回模板后，才继续启动应用。

```json
{
  "index_patterns": ["middleware-ops-audit-*"],
  "order": 100,
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1
  },
  "mappings": {
    "_doc": {
      "dynamic": "strict",
      "properties": {
        "id": {"type": "keyword"},
        "occurredAt": {"type": "date"},
        "subject": {"type": "keyword"},
        "capability": {"type": "keyword"},
        "middlewareType": {"type": "keyword"},
        "datasourceKey": {"type": "keyword"},
        "clusterTag": {"type": "keyword"},
        "resourceDigest": {"type": "keyword"},
        "httpStatus": {"type": "integer"},
        "durationMillis": {"type": "long"},
        "elasticsearchIndex": {"type": "keyword"},
        "elasticsearchDsl": {"type": "keyword"},
        "mysqlSql": {"type": "keyword"},
        "redisKey": {"type": "keyword"},
        "redisField": {"type": "keyword"},
        "kafkaTopic": {"type": "keyword"},
        "kafkaGroupId": {"type": "keyword"},
        "page": {"type": "integer"},
        "size": {"type": "integer"},
        "offset": {"type": "long"}
      }
    }
  }
}
```

```bash
curl -X PUT "http://localhost:9200/_template/middleware-ops-audit-template" \
  -H "Content-Type: application/json" \
  --data-binary @middleware-ops-audit-template.json

curl -f "http://localhost:9200/_template/middleware-ops-audit-template"
```

`dynamic: strict` 会拒绝 URL、账号、密码、token、LDAP 凭据、原始响应、查询结果、异常等未列入契约的字段。不要为审计索引改为宽松 mapping。

### 3.3 配置审计写入 Route 与审计读侧

把以下配置合并到已有 Elasticsearch Route 与 Search 配置中：

- `datasource: primary` 必须替换为实际已登记的 Elasticsearch 数据源名称。
- 写侧逻辑索引固定为 `middleware-ops-audit`；Route 将它写入 UTC 日索引。
- 读侧固定使用 `middleware-ops-audit-*`，不要缩窄为某个日期的物理索引。
- 保持 `api.enabled: false`，Ops 只复用 Search 的 `QueryExecutor`，不会暴露通用 `/api/query`、任意 DSL、排序或索引选择接口。

```yaml
io.github.surezzzzzz.sdk.elasticsearch.route:
  rules:
    - pattern: middleware-ops-audit
      type: exact
      datasource: primary
      write-index:
        template: middleware-ops-audit-{yyyy.MM.dd}
        zone-id: UTC

io.github.surezzzzzz.sdk.elasticsearch.search:
  enable: true
  indices:
    - name: middleware-ops-audit-*
      date-split: true
      date-pattern: yyyy.MM.dd
      date-field: occurredAt
      lazy-load: true
      cache-mapping: true
      sensitive-fields:
        - field: elasticsearchIndex
          strategy: MASK
          mask-start: 2
          mask-end: 2
        - field: elasticsearchDsl
          strategy: MASK
          mask-start: 12
          mask-end: 8
        - field: mysqlSql
          strategy: MASK
          mask-start: 12
          mask-end: 8
        - field: redisKey
          strategy: MASK
          mask-start: 2
          mask-end: 2
        - field: redisField
          strategy: MASK
          mask-start: 2
          mask-end: 2
        - field: kafkaTopic
          strategy: MASK
          mask-start: 2
          mask-end: 2
        - field: kafkaGroupId
          strategy: MASK
          mask-start: 2
          mask-end: 2
  query-limits:
    default-date-range: 30d
    strict-date-filter: true
    ignore-unavailable-indices: true
    max-offset: 10000
  api:
    enabled: false

io.github.surezzzzzz.sdk.ops.middleware:
  audit:
    max-range-days: 90
    max-offset: 10000
```

Search `MASK` 是 SQL、DSL、Key、Hash field、Topic、Consumer Group 审计展示的唯一脱敏边界。Server 和页面只展示 Search 已脱敏后的值，不增加本地脱敏规则。

### 3.4 启动后验收审计链路

1. 以 UTC 启动所有运行节点，例如添加 JVM 参数 `-Duser.timezone=UTC`。
2. 登录 `GET /middleware-ops/login`，在任一控制台提交一次人工操作，例如 MySQL 数据源状态探测或 Redis/Kafka 数据源清单查询。
3. 在 Elasticsearch 检查当天物理日索引是否出现：

   ```bash
   curl -f "http://localhost:9200/_cat/indices/middleware-ops-audit-*?v"
   ```

4. 回到页面“审计”，确认记录存在且 SQL、DSL、Key、Hash field、Topic、Consumer Group 只显示脱敏值。
5. 刷新任一工作区概览；概览自动读取不应新增审计记录，人工控制台提交的操作才应出现审计记录。

常见排查顺序：先确认模板 `GET` 返回成功，再确认 Route 的 `datasource` 名称存在且能连接，随后确认 Search 索引通配符是 `middleware-ops-audit-*`，最后检查写入身份具备按部署自动创建策略所需的最小权限。审计读侧只需要受限查询权限。

部署方应使用现有索引生命周期工具、定时任务或运维流程清理过期日索引。本 Starter 不使用 ES 6.2.2 不具备的 ILM，也不代替部署策略。

## 4. 启动后如何使用

浏览器入口固定为 `GET /middleware-ops/login`。页面使用自有表单登录和会话；API 位于 `api-base-path`，未认证时返回 JSON `401`。

四个工作区概览都会自动加载数据源目录及当前安全状态：

| 工作区 | 自动显示的安全状态 | 控制台可执行的人工只读操作 |
| --- | --- | --- |
| Elasticsearch | Route 版本与探测摘要 | 受限 JSON DSL 首窗口。 |
| Redis | Redis 版本与部署模式 | 精确 Key 的元数据及已检测类型的受限值窗口。 |
| Kafka | Route 诊断、WARN 原因、集群标识、Broker 数与 Controller 可见性 | Topic 清单、Consumer Group 清单、Topic 运行态与 Consumer Group 积压。 |
| MySQL | 连接、逻辑数据库、服务端版本、普通只读与强制只读保护 | 单表、无 schema、无锁的受控 SELECT 首窗口。 |

概览自动读取不写审计。控制台中由操作者显式提交的 Redis/Kafka 数据源清单和 MySQL 状态探测会写审计。所有路径复用相同的授权、并发、校验与安全 DTO 边界，不返回物理地址、凭据、Route 配置、原始下游响应或异常。

Kafka topic 与消费组查询只通过 `KafkaRouteAdminClientFactory#withAdminClient(datasourceKey, callback)` 在 callback 内获取 Route 所有的 `AdminClient`。所有 `KafkaFuture` 都在 callback 内按 deadline 完成，callback 返回后不保留客户端或未完成异步任务。

## 5. 配置参考

### 查询与并发限制

| 配置项 | 说明 |
| --- | --- |
| `query.default-size` | 页面首次查询的默认条数。 |
| `query.max-size` | 每次受控查询返回的最大条数；同时限制 Kafka Topic runtime 的 partition 返回数量。 |
| `query.max-dsl-length` | Elasticsearch DSL 最大字符数。 |
| `query.max-sql-length` | MySQL SQL 最大字符数。 |
| `query.max-columns` | MySQL 结果最大列数。 |
| `query.max-cell-length` | 单元格内容最大字符数。 |
| `query.max-response-length` | 单个安全摘要字段的最大字符数。 |
| `query.deadline-millis` | Kafka 等下游操作的 deadline。 |
| `concurrency.global` | 全部请求的最大并发预算。 |
| `concurrency.datasource` | 单数据源的最大并发预算。 |

### 审计时间范围

四个审计页面首次加载不传时间条件，直接使用 Search Starter 默认最近 30 天。因此 `audit.max-range-days` 必须至少为 30 天。页面显示服务端实际生效的 UTC 起止时间；只有配置至少为 90 天时才显示 90 天快捷范围。

快捷范围支持 `1d`、`7d`、`30d`，以及满足上限时的 `90d`。自定义 `from`、`to` 必须同时提供并使用 `yyyy-MM-dd'T'HH:mm:ss` UTC 格式，最大跨度由 `audit.max-range-days` 限制。后续翻页固定复用首次响应的起止时间，避免相对范围漂移。`audit.max-offset` 不得大于 Search `query-limits.max-offset`。

## 6. 已发布只读接口

所有接口位于 `api-base-path`（默认 `/api/v1/middleware-ops`）。成功时直接返回具体 Response DTO，不使用业务 `code` 或统一成功包装。

| 方法 | 路径 | 一期安全响应 |
| --- | --- | --- |
| GET | `/elasticsearch/datasources/{datasourceKey}/summary` | Route `ClusterInfo` 的版本/探测安全摘要。 |
| GET | `/redis/datasources/overview` | 概览自动加载的 Redis 数据源安全状态清单，不写审计。 |
| GET | `/redis/datasources` | 人工控制台查询的 Redis 数据源安全清单，写审计。 |
| GET | `/redis/datasources/{datasourceKey}/summary` | Redis 版本与部署模式安全摘要。 |
| GET | `/kafka/datasources/overview` | 概览自动加载的 Kafka 数据源安全诊断，不写审计。 |
| GET | `/kafka/datasources` | 人工控制台查询的 Kafka Route 诊断安全清单，写审计。 |
| GET | `/elasticsearch/catalog`、`/redis/catalog`、`/kafka/catalog`、`/mysql/catalog` | 当前工作区的启动期数据源快照。 |
| GET | `/elasticsearch/datasources/{datasourceKey}/documents?index=&dsl=&size=` | 受限 JSON DSL 首窗口。 |
| GET | `/redis/datasources/{datasourceKey}/keys/metadata?key=` | 精确 Key 的存在性、类型与 TTL。 |
| GET | `/redis/datasources/{datasourceKey}/keys/value?key=&field=&offset=&size=` | 已检测类型的受限值窗口。 |
| GET | `/kafka/datasources/{datasourceKey}/topics?size=` | 固定排序的 Topic 首窗口。 |
| GET | `/kafka/datasources/{datasourceKey}/consumer-groups?size=` | 固定排序的 Consumer Group 首窗口。 |
| GET | `/kafka/datasources/{datasourceKey}/topics/runtime?topic=` | `query.max-size` 固定 partition 窗口内的 Topic 运行态；`truncated=true` 表示仍有未返回分区。 |
| GET | `/kafka/datasources/{datasourceKey}/consumer-groups/lag?groupId=&size=` | Consumer Group 积压首窗口。 |
| GET | `/mysql/datasources/{datasourceKey}/overview-status` | 概览自动加载的逻辑数据库、连通性与只读状态安全摘要，不写审计。 |
| GET | `/mysql/datasources/{datasourceKey}/status` | 人工控制台探测的逻辑数据库、连通性与只读状态安全摘要，写审计。 |
| GET | `/mysql/datasources/{datasourceKey}/select?sql=&size=` | 单表、无 schema、无锁的受控 SELECT 首窗口。 |

## 7. 部署安全边界

### MySQL 只读账号

为每个 MySQL Route 逻辑数据源创建独立、最小权限的只读账号；不要使用拥有写入、DDL、授权、复制管理权限的账号。Ops 不按用户名猜测权限，也不阻断 `root`、`admin` 等常见用户名；实际写防护由部署侧的窄范围 `SELECT` 授权承担。

以下示例仅授予一个逻辑数据库内受控查询所需的 `SELECT` 权限。将数据库名、账号、来源主机替换为实际值：

```sql
CREATE USER 'middleware_ops_reader'@'middleware-ops.test' IDENTIFIED BY '<由部署平台保管的随机密码>';
GRANT SELECT ON `sample_ops`.* TO 'middleware_ops_reader'@'middleware-ops.test';
```

不再使用该账号时，撤销授权并删除账号：

```sql
REVOKE SELECT ON `sample_ops`.* FROM 'middleware_ops_reader'@'middleware-ops.test';
DROP USER 'middleware_ops_reader'@'middleware-ops.test';
```

不要授予 `INSERT`、`UPDATE`、`DELETE`、`CREATE`、`ALTER`、`DROP`、`EXECUTE`、`FILE`、`PROCESS`、`SUPER`、`REPLICATION CLIENT`、`REPLICATION SLAVE` 或 `GRANT OPTION`，也不要授予全局通配符权限。账号密码只放在使用方受控配置中，不能写入日志、审计或本 Starter 的提交配置。

### 固定安全约束

- Elasticsearch 仅接受受限 JSON DSL；Redis 仅允许精确 Key 的已检测类型受限读取。
- Kafka 不提供消息、payload/header、任意 Admin 命令、Topic/ACL/config/offset/Consumer Group 修改。
- MySQL 只允许单条、单表、无 schema、无 Join、无函数、无锁的 SELECT。
- 不返回 Route 配置、地址、凭据、安全属性、原始中间件响应、原始异常或实现类名。
- 普通查询仅返回严格上限的首窗口。Kafka Topic runtime 不接收 `size` 参数，不提供客户端续页，按 partition ID 排序后固定从 `0` 开始截取窗口，并以顶层 `truncated` 表示是否仍有未返回 partition。
- 默认响应头包含 `Cache-Control: no-store` 与服务端生成的 `X-Request-Id`。

## 8. 身份、授权与扩展

LDAP 用户认证的配置和页面/API 入口见“用户系统：Windows AD / LDAP”。身份认证成功后，默认情况下任何已认证用户都可访问已发布的只读能力。Ops 不耦合 AD 用户名格式、组结构或认证协议，也不把 LDAP 组自动当作业务权限。后续接入 IAM 或自定义授权时可替换：

- `MiddlewareOpsIdentityResolver`
- `MiddlewareOpsAuthorizationPolicy`
- `MiddlewareOpsServerEngine`

身份、授权和中间件 Adapter 都不接受请求参数或 header 覆盖操作者身份。

## 9. HTTP 状态

| 场景 | 状态 |
| --- | --- |
| 查询成功 | 200 |
| 输入或 size 非法 | 400 |
| 未认证 | 401 |
| 已认证但策略拒绝 | 403 |
| 数据源不存在 | 404 |
| 不支持的 HTTP 方法 | 405 |
| 瞬时并发预算耗尽 | 429 |
| Route 或中间件不可用 | 503 |
| Kafka 查询超过 deadline | 504 |

详细资源所有权、扩展边界与非目标见 [DESIGN.md](DESIGN.md)。
