# Simple Elasticsearch Search Jakarta Starter

为 Spring Boot 3 应用提供配置驱动的 Elasticsearch 查询、精确计数、聚合和分页。通过 `SearchEngine` 使用结构化条件，或通过 `ExpressionService` 使用条件表达式；连接和路由交给 Elasticsearch Route，也可显式开启 HTTP 端点。

适合按允许索引目录构建业务查询，不是集群管理工具，也不替宿主实现认证和数据权限。

## 依赖

使用 Spring Boot `3.4.2`、Java `17` 或 `21`；编译目标为 Java `17`。Elasticsearch Route（数据源连接和索引路由组件）和 `condition-expression-parser-jakarta-starter:1.0.0`（条件文本解析器）由本组件传递引入，无需单独声明。自然语言查询不包含在内。

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-elasticsearch-search-jakarta-starter:1.0.0'
    implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'
}
```

仅通过 Java 调用时无需引入 Web 依赖。要启用 HTTP 端点，另外引入 `org.springframework.boot:spring-boot-starter-web`，并打开 `search.api.enabled`。

## 最小配置

示例使用一个普通索引，要求索引已存在且 `status` 映射为 keyword（精确值字段）、`amount` 为数值。组件不自动创建索引、字段映射或测试数据：

```yaml
io.github.surezzzzzz.sdk.elasticsearch:
  route:
    enable: true
    default-source: primary
    sources:
      primary:
        urls: https://es.example.test:9200
        server-version: 8.17.0
  search:
    enable: true
    indices:
      - name: sample-record
        alias: record
        field-mapping:
          status: [状态]
          amount: [数值]
    api:
      enabled: false
```

`alias` 是本组件的应用标识，不是 ES alias（集群中的索引别名）。请求只能访问配置目录；地址、认证和 TLS（传输加密）只在 Route 配置。启用 Search 却没有 Route 注册表会启动失败。

### 最小调用与结果读取

在 Spring 管理的类中注入 `SearchEngine`，即可查询上述 `record`。以下代码在已注入的 `SearchEngine engine` 上执行；未传分页参数时使用 offset（按页码偏移查询），默认第 1 页、每页 20 条：

```java
import io.github.surezzzzzz.sdk.elasticsearch.search.engine.SearchEngine;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import java.util.List;
import java.util.Map;

QueryResponse result = engine.query(QueryRequest.builder()
        .index("record")
        .query(QueryCondition.builder().field("status").op("eq").value("ready").build())
        .fields(List.of("status", "amount"))
        .build());

List<Map<String, Object>> documents = result.getItems();
long matchedDocuments = result.getTotal();
boolean hasMore = result.getPagination().getHasMore();
```

`items` 是本页文档列表，空结果为 `[]`；`total` 是精确命中总数，不等于本页条数。文档除所选字段外还附带 `_id`、`_index`，默认不返回 `_score`。Java 构造器的 `.op("eq")` 对应 JSON 的 `operator`，不是 `op`。

## 完整配置

下面列出 Search 的全部配置项，Route 部分只保留最小连接配置。示例使用默认查询限制、游标保活与开关值，显式启用组件并填写索引信息。PIT（固定查询快照）和 scroll（分批遍历）使用游标保存续页位置；不使用这两种模式时可省略游标密钥。

```yaml
io.github.surezzzzzz.sdk.elasticsearch:
  route:
    enable: true
    default-source: primary
    sources:
      primary:
        urls: https://es.example.test:9200
        server-version: 8.17.0
  search:
    enable: true
    indices:
      - name: sample-record
        alias: record
        date-split: false
        date-pattern: yyyy.MM.dd
        date-field: occurredAt
        zone-id: UTC
        cache-mapping: true
        lazy-load: true
        tiebreaker-field: uniqueKey
        field-mapping:
          status: [状态]
          amount: [数值]
        sensitive-fields:
          - field: internalValue
            strategy: forbidden
          - field: displayValue
            strategy: mask
            mask-start: 0
            mask-end: 0
            mask-pattern: "****"
      - name: "sample-event-*"
        alias: event
        date-split: true
        date-pattern: yyyy.MM.dd
        date-field: occurredAt
        zone-id: UTC
        cache-mapping: true
        lazy-load: true
        tiebreaker-field: uniqueKey
        field-mapping: {}
        sensitive-fields: []
    query-limits:
      default-size: 20
      max-size: 1000
      max-offset: 10000
      max-indices: 366
      max-depth: 32
      max-nodes: 1000
      mapping-cache-size: 256
      strict-date-filter: true
      ignore-unavailable-indices: false
      allow-full-scan: false
      default-date-range: 30d
    cursor:
      encryption-key: ${SEARCH_CURSOR_ENCRYPTION_KEY:}
      keep-alive: 1m
    expression:
      max-length: 16384
      max-tokens: 4096
    api:
      enabled: false
      base-path: /api
      include-score: false
    mapping-refresh:
      enabled: false
      interval-seconds: 300
```

该示例要求 `occurredAt` 为 date/date_nanos（日期）字段，`uniqueKey` 为可排序、唯一的 keyword 或数值字段。日期分片名称如 `sample-event-2026.01.01`。普通索引配置 `date-field` 后也能使用显式时间过滤，但 `default-date-range` 只会自动应用于 `date-split=true` 的索引。

`${SEARCH_CURSOR_ENCRYPTION_KEY:}` 表示从环境读取密钥，未提供时为空；也可在独立私密配置中填写，不能将真实密钥提交入库。多数据源时还须配置明确的 Route 规则，保证每个查询目标唯一归属一个数据源。

## 配置项说明

### 连接配置

下列路径相对于 `io.github.surezzzzzz.sdk.elasticsearch.route`。连接、认证、TLS 和客户端关闭均由 Route 管理。

| 配置项 | 示例值 | 说明 |
| --- | --- | --- |
| `enable` | `true` | 启用 Route 数据源托管 |
| `default-source` | `primary` | 默认数据源键，须与 `sources` 中的键一致 |
| `sources.<key>.urls` | `https://es.example.test:9200` | ES 连接地址；示例域名仅作占位 |
| `sources.<key>.server-version` | `8.17.0` | 显式指定实际 ES 服务端版本，不能填宿主客户端的版本 |

### 组件和索引目录

以下各表路径均相对于 `io.github.surezzzzzz.sdk.elasticsearch.search`。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `enable` | `false` | 显式启用组件；需要 Route 注册表和非空索引目录 |
| `indices` | 空列表 | 可访问的索引配置列表，不是用户或角色的授权列表 |
| `indices[].name` | 无 | 必填，实际索引名或受支持的索引表达式；不能重复 |
| `indices[].alias` | 无 | 可选的应用标识；未设时用 `name`。标识不能与其他配置的名称或标识重复 |
| `indices[].date-split` | `false` | 是否按日期展开索引；开启时要求名称为单个结尾 `*` 的形式，且提供 `date-field` |
| `indices[].date-pattern` | `yyyy.MM.dd` | 日期转换成物理索引后缀的格式，如 `yyyy.MM.dd`、`yyyy.MM`、`yyyy`；自动月/年降级针对 `yyyy.MM.dd` |
| `indices[].date-field` | 无 | 时间过滤字段，必须为 date/date_nanos；日期分片或请求传 `dateRange` 时需要 |
| `indices[].zone-id` | `UTC` | 索引日期与无时区输入的解析时区，使用 Java ZoneId 支持的标识 |
| `indices[].cache-mapping` | `true` | 是否缓存 mapping（ES 字段定义）；关闭时每次读取元数据 |
| `indices[].lazy-load` | `true` | 首次访问时加载字段定义；设为 `false` 时启动加载 |
| `indices[].tiebreaker-field` | 无 | 普通 search_after 的唯一判定字段，必须可排序且在本次全部索引范围内稳定唯一；不允许用 `_id` |
| `indices[].field-mapping` | 空映射 | 实际字段名到标签列表；表达式可使用标签，结构化请求仍使用实际字段名。标签不能映射多个字段或与其他字段名冲突 |
| `indices[].sensitive-fields` | 空列表 | 字段保护规则，覆盖点路径、嵌套对象和数组；同一个字段不能重复配置 |

### 字段保护规则

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `indices[].sensitive-fields[].field` | 无 | 必填，固定字段路径，例如 `internalValue` 或 `details.internalValue` |
| `indices[].sensitive-fields[].strategy` | 无 | 必填，仅支持 `forbidden`（禁止访问）或 `mask`（返回时脱敏） |
| `indices[].sensitive-fields[].mask-start` | 未设，按 `0` 处理 | 脱敏时保留的开头字符数，不能为负数，仅对 `mask` 有效 |
| `indices[].sensitive-fields[].mask-end` | 未设，按 `0` 处理 | 脱敏时保留的结尾字符数，不能为负数，仅对 `mask` 有效 |
| `indices[].sensitive-fields[].mask-pattern` | `****` | 脱敏替换文本，不能为空；不返回可保留的字符时整值替换 |

### 查询范围与资源限制

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `query-limits.default-size` | `20` | 请求未传 `size` 时的分页大小，必须为正数 |
| `query-limits.max-size` | `1000` | 单页大小上限，不能小于默认大小；不是整个遍历的总数限制 |
| `query-limits.max-offset` | `10000` | offset 的 `from+size` 上限，不能小于 `max-size`；超过时请求失败，不自动换分页模式 |
| `query-limits.max-indices` | `366` | 单次读取的索引目标数上限；日期展开超预算时按时间过滤保护规则降级 |
| `query-limits.max-depth` | `32` | 条件或聚合树的最大深度，必须为正数 |
| `query-limits.max-nodes` | `1000` | 条件或聚合树的最大节点数，必须为正数 |
| `query-limits.mapping-cache-size` | `256` | 字段定义缓存的最大条目数，必须为正数；条目按配置、数据源和实际目标区分 |
| `query-limits.strict-date-filter` | `true` | 为已解析的时间范围追加字段过滤；月/年/全通配降级依赖此保护 |
| `query-limits.ignore-unavailable-indices` | `false` | 是否允许跳过不存在的索引；仅在索引缺失属于正常情况时开启，不会吞掉其他查询错误 |
| `query-limits.allow-full-scan` | `false` | 是否允许日期范围缺省时全扫或进一步全通配降级；不是所有普通通配索引的统一全扫开关 |
| `query-limits.default-date-range` | `30d` | 日期分片未传范围时采用的最近固定时长；支持正整数 `s/m/h/d`。留空且不允许全扫时，缺省范围的日期查询失败 |

### 表达式限制

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `expression.max-length` | `16384` | 表达式最大长度，按 Java 字符串长度计算，必须为正数 |
| `expression.max-tokens` | `4096` | 解析后的词法单元数量上限，必须为正数；限制过大的条件文本 |

表达式同时受 `query-limits.max-depth/max-nodes` 约束，包含括号解析、条件节点和 IN 值数量。默认使用 Condition 自带解析策略；若关闭其默认自动配置，须自行提供可用的解析器或替换 `ExpressionService`，否则启用 Search 会因缺少解析器而启动失败。

### 游标、HTTP 与刷新

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `cursor.encryption-key` | 无 | Base64 编码的 32 字节 AES 密钥，PIT/scroll 必需，多实例须相同；缺失不阻断 offset 或普通 search_after |
| `cursor.keep-alive` | `1m` | 请求未传 PIT/scroll 保活时间时的默认值；支持正整数 `s/m/h/d`，最长一小时 |
| `api.enabled` | `false` | 是否注册 Servlet HTTP 端点，同时需要宿主引入 Web 依赖 |
| `api.base-path` | `/api` | HTTP 基路径，须以 `/` 开头，不能含路径变量 |
| `api.include-score` | `false` | 是否在文档结果中返回 `_score`（相关性分数），Java 查询同样生效 |
| `mapping-refresh.enabled` | `false` | 是否启动字段定义定时刷新；失败保留上一份缓存，不代替索引或权限配置更新 |
| `mapping-refresh.interval-seconds` | `300` | 定时刷新的间隔秒数，必须为正数 |

## 最佳实践

使用文本筛选条件时，直接看[表达式搜索](#2-表达式搜索)；使用 Java 条件对象或 JSON 条件树时，看结构化查询。

### 1. 构造查询与读取结果

结构化查询使用 `QueryRequest`，HTTP 请求使用相同字段名：

| 参数 | 用途与缺省行为 |
| --- | --- |
| `index` | 必填，配置中的应用标识或名称，不能传任意 ES 索引 |
| `query` | 条件树；省略时不额外过滤，仍受索引范围、日期过滤和宿主权限约束 |
| `dateRange` | 时间范围对象，字段为 `from/to`；日期分片省略时使用 `default-date-range` |
| `pagination` | 分页对象；省略时为 offset 第 1 页，大小取 `default-size` |
| `fields` | 返回的实际字段名列表；省略或空列表时返回全部允许字段，仍执行字段保护 |
| `collapse` | 按 `field` 折叠重复结果，仅用于 offset；总数仍是折叠前文档数 |
| `countOnly` | 默认 `false`；`true` 时只返回精确计数，不读取文档 |

需要自定义页码或仅计数时，在注入的 `SearchEngine engine` 上调用：

```java
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;

QueryRequest request = QueryRequest.builder()
        .index("record")
        .query(QueryCondition.builder().field("status").op("eq").value("ready").build())
        .pagination(PaginationInfo.builder().type("offset").page(1).size(20).build())
        .build();
QueryResponse result = engine.query(request);
QueryResponse counted = engine.count(request);

long matchedDocuments = counted.getTotal();
```

Java 构造器使用 `.op("eq")`，JSON 对应字段名是 `operator`，不能写成 `op`。多个条件用 `logic=and/or` 加 `conditions` 组合；`logic=not` 必须恰好包含一个子条件。不在一个叶子条件里混用两种形态。

查询结果 `items` 是文档 Map 列表，附带 `_id`、`_index`；`total` 是精确命中数，`took` 单位毫秒。默认不返回 `_score`（相关性分数），需要时设置 `api.include-score=true`，对 Java 查询同样生效。

`count` 和 `countOnly=true` 都只执行 ES 的 `_count`；`items`、`page`、`size`、`pagination` 为 null。超时、分片失败或非精确 total 不返回部分成功。

条件支持 `and`、`or`、`not` 分组，以及 21 种操作符：

`eq/ne/gt/gte/lt/lte/in/not_in/between/like/not_like/prefix/not_prefix/suffix/not_suffix/regex/not_regex/exists/not_exists/is_null/is_not_null`。

`_id` 仅支持 `eq/ne/in/not_in`。未知字段、冲突映射、非法类型和过深条件在查询 HTTP 前失败。text（分词文本）有可用 keyword 子字段时使用子字段；无 keyword 的 text 仅允许 eq/ne 的短语匹配，不冒充精确值查询。

`fields` 控制返回字段；`collapse`（按字段折叠重复结果）仅用于 offset，total 仍是折叠前文档数，`hasMore` 是按本页是否填满估算的继续查询提示。

只需要记录数时用 `engine.count(request)` 或 HTTP 请求的 `countOnly=true`，不先查出所有文档再计数，也不通过 `items.size()` 估算总数。计数会忽略分页参数，且不打开 PIT/scroll；它不提供已有 PIT 的快照内计数。

例如，`POST /api/query` 可以在一个请求中组合 AND/OR、数值条件和 `_id` 查询，并只返回总数：

```json
{
  "index": "record",
  "query": {
    "logic": "and",
    "conditions": [
      {"field": "status", "operator": "eq", "value": "ready"},
      {"logic": "or", "conditions": [
        {"field": "amount", "operator": "gte", "value": 10},
        {"field": "_id", "operator": "in", "values": ["record-001", "record-002"]}
      ]}
    ]
  },
  "countOnly": true
}
```

单值条件用 `value`，`in/not_in/between` 用 `values`，其中 `between` 需要两个端点；`exists/not_exists/is_null/is_not_null` 不传条件值。数值和布尔值使用相应 JSON 类型，不用字符串代替。

### 2. 表达式搜索

适合条件编辑器或调用方已有筛选文本的场景，例如 `状态='ready' AND 数值>=10`。通过 `ExpressionService` 直接搜索，不需要调用方先把文本拆成条件树，也不需要自行拼接 ES 查询描述对象（DSL）。表达式进入相同的 Engine，仍执行字段保护、日期范围、分页和聚合校验。

#### Java 表达式搜索

在 Spring 管理的类中注入 `ExpressionService`。下面在已注入的 `ExpressionService expressions` 上执行，使用最小配置中的 `record` 和字段标签，查询第 1 页并读取结果：

```java
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.ExpressionService;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionQueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import java.util.List;
import java.util.Map;

QueryResponse result = expressions.query(ExpressionQueryRequest.builder()
        .index("record")
        .expression("状态='ready' AND 数值>=10")
        .fields(List.of("status", "amount"))
        .pagination(PaginationInfo.builder().type("offset").page(1).size(20).build())
        .build());

List<Map<String, Object>> documents = result.getItems();
long matchedDocuments = result.getTotal();
boolean hasMore = result.getPagination().getHasMore();
```

表达式中的字段位置可使用标签，`fields` 和 `sort` 仍使用实际字段名。结果与结构化搜索相同：`items` 是本页文档，`total` 是精确总数，`pagination` 提供是否继续及续页参数。

#### 表达式计数、聚合、校验与翻译

仅计数、统计指标、校验输入和取得结构化条件也使用同一个 `ExpressionService`：

```java
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionQueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionAggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionValidationRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import java.util.List;

QueryResponse count = expressions.query(ExpressionQueryRequest.builder()
        .index("record").expression("NOT (status='closed' OR amount<10)")
        .countOnly(true).build());
AggResponse grouped = expressions.aggregate(ExpressionAggRequest.builder()
        .index("record").expression("status='ready'")
        .aggs(List.of(AggDefinition.builder().name("totalAmount")
                .type("sum").field("amount").build())).build());
boolean valid = expressions.validate(ExpressionValidationRequest.builder()
        .index("record").expression("amount>=10").build()).isValid();
QueryCondition condition = expressions.translate("amount>=10", "record");
```

翻译结果可放入普通 `QueryRequest`；需要精确计数时设置 `countOnly=true`，不先取文档再数条数。

#### HTTP 表达式搜索

引入 Web 依赖并设置 `search.api.enabled=true` 后，将查询发送到 `POST /api/query/expression`，请求正文使用 JSON：

```json
{
  "index": "record",
  "expression": "status='ready' AND amount>=10",
  "fields": ["status", "amount"],
  "pagination": {"type": "offset", "page": 1, "size": 20}
}
```

响应直接是 `QueryResponse`，从顶层读取 `items/total/pagination`，没有 `data` 包装。列表翻页只改变 offset 的 `page`；深分页按 PIT/search_after/scroll 的续页规则传参，不将页码套用到这些模式。

表达式请求不传结构化 `query`，由 `expression` 生成过滤条件。参数按入口区分如下：

| 参数 | 适用入口 | 说明 |
| --- | --- | --- |
| `index` | 查询、聚合、校验 | 必填，配置中的应用标识或名称 |
| `expression` | 查询、聚合、校验 | 条件文本，可使用实际字段名或已配置标签 |
| `dateRange` | 查询、聚合、校验 | 索引解析及严格日期过滤范围，不由表达式或锚点自动推导 |
| `timeZone` | 查询、聚合、校验 | 相对时间的时区，默认使用索引 `zone-id` |
| `timeRangeEnd` | 查询、聚合、校验 | 默认 `now`；`today_start` 将滚动时间范围上界置为当天零点 |
| `timeRangeAnchor` | 查询、聚合、校验 | 带时区的固定时间点；含时间关键字的非 offset 或 composite 遍历必须提供，纯计数无需提供 |
| `pagination/fields/collapse/countOnly` | 查询 | 与结构化查询的用途及限制相同 |
| `aggs` | 聚合 | 必填，非空的聚合定义列表；定义中的字段使用实际字段名 |
| `after` | 聚合 | composite 续页时回传上一响应完整的 `afterKey` |

表达式聚合发送到 `POST /api/agg/expression`，过滤后再计算指标，返回与结构化聚合相同的 `aggregations/afterKey`：

```json
{
  "index": "record",
  "expression": "status='ready' AND amount>=10",
  "aggs": [{"name": "totalAmount", "type": "sum", "field": "amount"}]
}
```

支持比较 `= != > >= < <=`、`IN/NOT IN`、`LIKE/NOT LIKE`、`PREFIX LIKE/NOT PREFIX LIKE`、`SUFFIX LIKE/NOT SUFFIX LIKE`、`EXISTS/NOT EXISTS`、`IS NULL/IS NOT NULL`、`AND/OR/NOT` 和括号，以及解析器提供的中文同义语法。正则与 BETWEEN 的字面语法不在表达式语法内，需使用结构化条件。

字段标签只在解析树的字段位置转换，不替换值文本；含空格或特殊字符的标签用反引号，例如 `` `展示 状态`='ready' ``。NOT 是文档集合的补集，缺失字段和多值字段不会通过简单翻转比较符误判。

数值、布尔、时间关键字会被解析器识别，**加引号不等于强制字符串类型**。Search 对 keyword/text 等字符串字段和 `_id` 保留 `001`、`TRUE` 的拼写，对数值和布尔字段使用解析后的类型；LIKE 也保留模式拼写。比较中的时间关键字仅用于 date/date_nanos 或以秒为单位的 long 时间字段，不能作为普通字符串或 IN 值；要查询与时间关键字同名、或超出解析器数值范围的纯数字文本，使用结构化条件保留字面值。引号内不提供反斜杠转义规则；需在 JSON 层正确编码文本，含单引号的值可使用双引号包围。

LIKE 含 `*` 或 `?` 时按 ES 通配模式匹配，否则采用包含匹配；PREFIX LIKE 使用前缀，SUFFIX LIKE 使用后缀。它们不是 SQL 的 `%/_` 通配语法。

#### 时间条件与续页

请求的 `timeZone` 缺省为索引 `zone-id`；`timeRangeAnchor` 为带时区的固定时间点，缺省取本次当前时间。昨天、前天、上周、上月、上季度和去年使用完整左闭右开周期；今天、本周、本月、本季度和今年从周期起点到锚点。近 N 小时/天/月等按解析器支持的日历单位回退；`timeRangeEnd=now` 使用锚点，`today_start` 将滚动范围上界固定到所在时区当天零点。

对周期 `[from,to)`，等于为该区间，不等于为其补集；大于为 `>=to`，大于等于为 `>=from`，小于为 `<from`，小于等于为 `<to`。日期字段使用绝对时间，long 时间字段使用 epoch seconds（从 1970 年起的秒数），不自动猜测毫秒。

含时间关键字的 search_after、PIT、scroll 和 composite 遍历，首页就必须显式传 `timeRangeAnchor`，后续保持表达式、锚点、时区和其他查询参数一致，避免边界随时间移动。纯计数无需锚点；普通 offset 未传锚点时，每次按当前时间重新计算。

```json
{
  "index": "event",
  "expression": "occurredAt=近7天",
  "dateRange": {"from": "2026-01-01T00:00:00Z", "to": "2026-01-08T00:00:00Z"},
  "timeRangeAnchor": "2026-01-08T00:00:00Z",
  "timeZone": "UTC",
  "timeRangeEnd": "now",
  "pagination": {"type": "search_after", "searchAfterMode": "pit", "size": 20}
}
```

表达式**不自动推导日期分片的 `dateRange`**。索引范围仍由显式 `dateRange` 或 `default-date-range` 决定，严格日期过滤与表达式取交集。查历史锚点时同步传入历史范围，不能只改锚点而仍使用当前最近 30 天的索引范围。PIT/scroll 续页读取首次冻结的实际索引，其余游标规则与结构化查询相同。

#### 校验与字段提示

`POST /api/expression/validation` 接收含 `index/expression` 的请求体，还可传日期与时间上下文：

```json
{
  "index": "record",
  "expression": "status='ready' AND amount>=10"
}
```

有效时 `valid=true`、`message=null`；宿主 JSON 配置也可能省略空的 `message`。语法或字段参数无效时返回 `{"valid": false, "message": "安全提示"}`，两者均为 HTTP 200 的校验结果。真实连接或协议故障仍返回非成功 HTTP 状态，不伪装成无效表达式。校验读取字段元数据但不执行搜索，不能证明宿主用户有业务权限，也不保证 ES 自定义日期格式或所有字面值在执行时合法。

`GET /api/expression/hints?index=record` 返回 `fields/operators/timeRanges`，用于编辑器提示；字段包含可用标签，隐藏禁止访问、冲突、nested 和不可过滤字段，不枚举文档值。原文只放在 POST 请求体中，不放入 URL，也不打印到日志。

### 3. 选择合适的分页方式

| 模式 | 使用方式 | 返回与限制 |
| --- | --- | --- |
| offset | `type=offset`，page 从 1 开始 | 默认每页 20，默认最大 1000，默认 `from+size` 上限 10000，可通过查询限制配置调整 |
| search_after | `type=search_after`，传 sort，续页带 `nextSearchAfter` | 不支持随机页码；默认 tiebreaker 模式要求配置可排序唯一字段 |
| PIT | `type=search_after`、`searchAfterMode=pit` | 查询快照；续页同时带 `pitId` 和 `nextSearchAfter` |
| scroll | `type=scroll` | 顺序遍历；续页带 `scrollId` |

`pagination` 的请求参数如下，Java 与 JSON 使用相同字段名：

| 参数 | 默认值或来源 | 说明 |
| --- | --- | --- |
| `type` | `offset` | `offset/search_after/scroll`；PIT 是 search_after 的模式，不是单独的 type |
| `page` | `1` | offset 页码，从 1 开始；其他模式省略或保持 1，不能用于跳页 |
| `size` | `query-limits.default-size` | 正整数，不超过 `max-size`；PIT/scroll 续页保持不变 |
| `sort` | 未设 | 数组项为 `{field, order}`，order 默认 `asc`，也可用 `desc`；使用实际字段名 |
| `searchAfterMode` | `tiebreaker` | search_after 的 `tiebreaker/pit/none` 模式；tiebreaker 追加配置的唯一字段，none 需自行提供唯一排序 |
| `searchAfter` | 上一响应的 `pagination.nextSearchAfter` | search_after 首页不传，续页回传完整数组，包含组件追加的排序值 |
| `pitId` | 上一响应的 `pagination.pitId` | PIT 首页不传，续页与 `searchAfter` 一起回传最新加密游标 |
| `pitKeepAlive` | `cursor.keep-alive` | PIT 两次请求之间的保活时间，如 `1m`，最长一小时 |
| `scrollId` | 上一响应的 `pagination.scrollId` | scroll 首页不传，续页回传最新加密游标 |
| `scrollTtl` | `cursor.keep-alive` | scroll 两次请求之间的保活时间，格式及上限同 PIT |

PIT 可不传 `sort`，组件使用 `_shard_doc` 排序；scroll 可不传，默认 `_doc`。普通 search_after 的 tiebreaker 模式即使省略 `sort`，也必须配置可排序唯一的 `tiebreaker-field`。只有 `pagination.hasMore=true` 时才继续使用返回的续页参数；没有下一页时无需期待游标字段仍有值。

| 使用场景 | 推荐方式 | 注意事项 |
| --- | --- | --- |
| 页码列表、浅分页和随机跳页 | offset | 保持 `from+size` 在上限内；总数不等于折叠后的分组数 |
| 不要求固定快照的顺序深翻页 | search_after + tiebreaker | 配置可排序唯一字段，不能仅按重复时间值排序 |
| 需要固定数据快照的深翻页 | PIT + search_after | 保存最新 `pitId` 和 `nextSearchAfter`，结束或取消时释放 |
| 导出文档或后台顺序遍历 | scroll | 分批处理，不在内存中累积全部结果；不能随机跳页 |
| 全量遍历聚合分组 | composite | 用 `afterKey` 续页，不用 terms 的大 `size` 冒充全量 |

普通 search_after 的 `tiebreaker`（相同排序值时的唯一判定字段）使用 `indices[].tiebreaker-field`，要求可排序且在本次全部索引范围内稳定唯一，**不自动追加不可排序的 `_id`**。`searchAfterMode=none` 不追加字段，调用方负责排序唯一性；非快照分页可能受并发写入影响。

PIT（point in time，固定查询快照）自动追加 `_shard_doc`，scroll 默认用 `_doc` 排序。它们返回的 `pitId/scrollId` 是本组件的认证加密游标，不是 ES 原始 ID。部署实例须共享同一个 Base64 编码的 32 字节 AES 密钥，放在私有配置中。

key（密钥）缺失只阻断 PIT/scroll，不影响 offset 或普通 search_after。保活支持正数 s/m/h/d，最多一小时；游标绑定原数据源、索引、查询、投影、排序和配置。续页不可改查询或拿它访问别的数据源，多实例还须使用一致配置。

最后一页自动释放上下文。用户放弃翻页时调用 `engine.closePit(token)` 或 `engine.clearScroll(token)`；过期但仍可认证的游标也可用于关闭。ES 上下文已失效时须重新开始分页。

### 4. PIT/scroll 续页与资源释放

下面是 scroll 首页请求，发送到 `POST /api/query`；也可构造相同的 Java `QueryRequest`：

```json
{
  "index": "record",
  "query": {"field": "status", "operator": "eq", "value": "ready"},
  "fields": ["status", "amount"],
  "pagination": {"type": "scroll", "size": 200, "scrollTtl": "1m"}
}
```

先处理返回的 `items`，再判断 `pagination.hasMore`。有下一页时，把最新响应的 `pagination.scrollId` 原样放到续页请求；其他参数保持一致：

```json
{
  "index": "record",
  "query": {"field": "status", "operator": "eq", "value": "ready"},
  "fields": ["status", "amount"],
  "pagination": {
    "type": "scroll",
    "size": 200,
    "scrollTtl": "1m",
    "scrollId": "<上一页返回的最新加密游标>"
  }
}
```

续页不能省略最初的过滤、投影或自定义排序，也不能修改 `size`；省略参数导致归一化后请求不同，同样会被拒绝。默认无排序的 scroll 使用 `_doc`，无需为了遍历强制加排序。

PIT 首页可按 `amount` 排序，组件自动追加 `_shard_doc` 作为快照内唯一判定值：

```json
{
  "index": "record",
  "query": {"field": "status", "operator": "eq", "value": "ready"},
  "pagination": {
    "type": "search_after",
    "searchAfterMode": "pit",
    "size": 20,
    "pitKeepAlive": "1m",
    "sort": [{"field": "amount", "order": "asc"}]
  }
}
```

假设响应中的 `nextSearchAfter` 为 `[12, 100]`，下一页请求如下。这里的数组仅为结构示例，必须用实际响应中的完整数组，不能自己计算或删除自动追加的排序值：

```json
{
  "index": "record",
  "query": {"field": "status", "operator": "eq", "value": "ready"},
  "pagination": {
    "type": "search_after",
    "searchAfterMode": "pit",
    "size": 20,
    "pitKeepAlive": "1m",
    "sort": [{"field": "amount", "order": "asc"}],
    "pitId": "<上一页返回的最新加密游标>",
    "searchAfter": [12, 100]
  }
}
```

应用需要维护“最新游标”，而不是一直保留第一页的值。正常结束由组件尝试清理；用户取消、消费者异常或提前退出时，用最新游标显式关闭。显式关闭失败会抛异常，应记录安全的错误类别并处置，不能在 `finally` 中覆盖原来的业务异常。保活只覆盖两次请求间的空闲时间，不是整个导出任务的总时限。

游标不能用于用户授权，宿主仍须对每次查询和关闭请求做权限检查。不要把游标打印到日志、拼入告警文本或跨环境重用；更换密钥或改变绑定配置后，已有遍历应重新开始。

### 5. 聚合、分组遍历与结果含义

```java
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import java.util.List;

AggResponse result = engine.aggregate(AggRequest.builder()
        .index("record")
        .aggs(List.of(AggDefinition.builder()
                .name("byStatus").type("terms").field("status").size(10).build(),
                AggDefinition.builder().name("totalAmount").type("sum")
                        .field("amount").build()))
        .build());
```

支持 sum、avg、min、max、count（字段值数量）、cardinality（去重估算）、stats、extended_stats、percentiles、percentile_ranks；以及 terms、date_histogram、histogram、range、date_range、ip_range、filter、filters、missing。聚合算法本身的近似值仍遵循 ES 语义，不把 cardinality 或 terms 分桶当作精确统计证明。

指标返回标量或统计 Map；桶返回 `key/count` 列表，子聚合按名称嵌入。`rawResponse` 始终为 null。支持嵌套聚合、composite（按桶键顺序翻页）和 bucket_sort/bucket_selector（对已生成的桶排序或筛选）。

聚合及 pipeline 名称以英文字母开头，只包含字母、数字和下划线，并须在整棵定义树中唯一。`key`、`count` 是桶结果的保留名称，不能用作聚合名称；`count` 聚合类型仍受支持，可命名为 `valueCount`。

设置 `composite=true` 的 terms/date_histogram/histogram，续页将响应的 `afterKey` 回传到 `AggRequest.after`。一个 composite 使用一个字段源，不能放在多桶父聚合或携带 pipeline。

pipeline 的路径必须指向可验证的兄弟指标或穿过单桶 filter/missing；stats 等多指标必须给属性名。首版不支持按百分位属性或嵌套多桶路径引用 pipeline 指标，错误不会退化为空聚合。

从 `result.getAggregations()` 按聚合名称读取指标或桶。terms 适合取前若干分组，不保证返回全部分组；需要全量时使用 composite，保持过滤条件和聚合定义不变，将整个 `response.afterKey` 作为下一次的 `request.after`，直到没有对应的续页键。单次桶数应结合返回体大小设置，不直接把 `size` 放大到所有分组数。

聚合名使用 `byStatus`、`totalAmount`、`valueCount` 等中性标识，不能使用保留名称 `key/count`。处理空结果时区分“没有命中”和“指标没有值”，不要把 ES 查询失败或字段冲突转换成伪造的零。

#### composite 遍历

发送到 `POST /api/agg`，按分组键顺序获取一页桶，并在每个桶中计算指标：

```json
{
  "index": "record",
  "aggs": [{
    "name": "byStatus",
    "type": "terms",
    "field": "status",
    "size": 200,
    "composite": true,
    "aggs": [{"name": "totalAmount", "type": "sum", "field": "amount"}]
  }]
}
```

例如响应的 `afterKey` 为 `{"byStatus": {"status": "ready"}}`，下一请求保留相同定义，并增加 `"after": {"byStatus": {"status": "ready"}}`。续页键以实际响应为准，不能取最后一个桶的 `key` 自行替代。

#### pipeline 桶筛选与排序

下面对已经生成的 terms 桶按指标筛选，再取排序后的前 5 个桶：

```json
{
  "index": "record",
  "aggs": [{
    "name": "byStatus",
    "type": "terms",
    "field": "status",
    "size": 100,
    "aggs": [{"name": "totalAmount", "type": "sum", "field": "amount"}],
    "pipelineAggs": [
      {"name": "keepAmount", "type": "bucket_selector",
       "script": "params.amountTotal >= 10", "bucketsPath": {"amountTotal": "totalAmount"}},
      {"name": "topAmount", "type": "bucket_sort", "sort": {"totalAmount": "desc"}, "size": 5}
    ]
  }]
}
```

`bucket_selector` 必须同时传脚本和显式 `bucketsPath` 映射，不会从脚本自动提取变量。pipeline 只处理父聚合已生成的桶，上例不是在所有可能分组中求全局指标 Top 5；扩大父桶集合也不能保证其成为全量统计。需要完整分组结果时应使用 composite 遍历后再做明确的应用端处理。

### 6. 日期索引、缺失索引与降级

完整配置中的 `event` 使用按日分片。显式指定时间范围优于依赖“当前最近 30 天”，便于重复查询和核对结果：

```json
{
  "index": "event",
  "dateRange": {"from": "2026-01-01T00:00:00Z", "to": "2026-02-01T00:00:00Z"},
  "pagination": {"type": "offset", "page": 1, "size": 20}
}
```

该时间范围是左闭右开区间，包含 1 月但不含 2 月 1 日。仅传日期时，`to: "2026-01-31"` 覆盖末日整天；不带时区的输入按 `zone-id` 解析，建议跨系统请求统一使用带 `Z` 或明确偏移量的时间戳。

保留 `strict-date-filter=true`，不要把物理索引名当成文档时间的证明。日期范围超过 `max-indices` 的展开预算时，`yyyy.MM.dd` 可改用月/年通配；进一步全通配须显式允许，并保留严格日期过滤。这里的降级是“减少索引目标表达式”，不是忽略失败、降低结果精度或自动重试任意 ES 错误。

日期空档属于正常情况时才开启 `ignore-unavailable-indices=true`；否则保留默认值，及时发现拼错名称、遗漏分片或路由问题。普通通配索引即使配置了 `default-date-range` 也不会自动获得日期范围，需显式传 `dateRange` 并配置 `date-field`，或改为日期分片配置。

PIT/scroll 续页冻结首次解析的索引和时间范围，不随当前时间移动。查询、计数和聚合需保持相同范围，才能对齐统计口径。

### 7. 字段类型、投影与敏感信息

`forbidden` 字段从结果和字段目录中删除，也不允许过滤、排序、投影、折叠或聚合。`mask` 字段允许过滤和投影，返回值递归脱敏，但禁止排序、折叠和聚合，防止通过排序值或桶键泄露原值。保护覆盖点路径、嵌套对象和数组。

对精确筛选、排序和分组使用 keyword、数值或日期字段。纯 text（分词文本）没有可用 keyword 子字段时，eq/ne 是短语匹配，不是原值精确相等；需要精确匹配应调整字段映射，而不是改用昂贵的模糊查询绕过限制。前缀匹配优先于无约束的双向通配或正则，并结合时间和其他条件缩小范围。

只取需要的 `fields`，减少文档内容传输。禁止字段也不能放入 `field-mapping` 的同路径或父子路径标签中；脱敏字段过滤仍可能泄露匹配关系，是否允许某类过滤由宿主权限控制。

### 8. 字段缓存与运行性能

mapping（ES 字段定义）缓存按配置标识、数据源和实际目标区分，默认最多 256 项。冲突字段显式标记；失败刷新不覆盖上次快照。`cache-mapping=false` 每次重新加载，`lazy-load=false` 启动加载；定时刷新通过 `mapping-refresh.enabled=true` 启用，默认间隔 300 秒。

稳定字段结构优先保留 `cache-mapping=true`；要及早发现连接或字段定义问题，可设 `lazy-load=false`，代价是增加启动期外部调用。字段会变化时启用定时刷新，或通过刷新端点主动更新。刷新只更新字段元数据，不创建索引，也不能使宿主中新改的权限配置自动生效。

多索引字段类型冲突要先统一写入侧 mapping，或缩小查询到兼容目标；不要用“刷新缓存”期待消除真实冲突。连接池、连接超时和读取超时在 Route 调整；优先限制单页大小、索引范围和聚合桶数，而不是单纯增大超时掩盖过大请求。

`nested`（独立嵌套文档）查询、ES mapping alias 查询和 runtime field（查询时计算的字段）不属于首版编译能力；不会将它们伪装成普通标量。字段目录提供 `indexed/docValues/conflict/nested/labels`；没有建立索引的字段不用于过滤，有 doc values（列式值）的字段仍可排序。

### 9. HTTP 端点与调用方集成

宿主引入 `spring-boot-starter-web` 并设置 `search.api.enabled=true` 才启用，默认基路径 `/api` 可通过 `api.base-path` 修改。

| 方法 | 路径 | 结果 |
| --- | --- | --- |
| POST | /api/query | 查询或 countOnly |
| POST | /api/agg | 聚合 |
| POST | /api/query/expression | 表达式查询或 countOnly |
| POST | /api/agg/expression | 表达式过滤聚合 |
| POST | /api/expression/validation | 校验语法和字段能力 |
| GET | /api/expression/hints | 字段、操作符和时间关键字提示 |
| GET | /api/indices | 配置索引目录 |
| GET | /api/indices/{alias}/fields | 可见字段及标签 |
| PUT | /api/indices/{alias}/mapping-cache | 刷新指定 mapping，204 |
| PUT | /api/indices/mapping-cache | 刷新配置目录，204 |
| DELETE | /api/query/pit/{cursorToken} | 关闭快照，204 |
| DELETE | /api/query/scroll/{cursorToken} | 释放遍历，204 |

查询直接返回 QueryResponse，聚合直接返回 AggResponse；索引目录为 `{indices: [...]}`，字段目录为 `{fields: [...]}`。宿主必须保护全部端点，目录允许列表不是用户授权；访问日志也须屏蔽关闭路径中的游标。

#### 成功响应与结果读取

下面为只投影 `status/amount` 的 offset 查询响应示例，假设命中两条记录。`took` 是耗时毫秒数，示例值不代表性能承诺：

```json
{
  "total": 2,
  "page": 1,
  "size": 20,
  "items": [
    {"status": "ready", "amount": 12, "_id": "record-001", "_index": "sample-record"},
    {"status": "ready", "amount": 15, "_id": "record-002", "_index": "sample-record"}
  ],
  "pagination": {
    "type": "offset", "hasMore": false,
    "nextSearchAfter": null, "pitId": null, "scrollId": null
  },
  "took": 1
}
```

PIT/scroll 及普通 search_after 的 `page` 为 null；计数结果只有 `total/took` 有值，`items/page/size/pagination` 为 null。宿主 JSON 配置可能省略这些空字段，调用方不应依赖它们一定出现。

聚合结果按请求中的名称读取，不是原始 ES 的 `value/buckets` 包装。对上面两条记录计算 `totalAmount` 的示例响应如下：

```json
{
  "aggregations": {"totalAmount": 27.0},
  "took": 1
}
```

composite 响应另外返回按聚合名组织的 `afterKey`，下一次放到请求 `after`，不是 `pagination` 中。没有续页键时结束；`rawResponse` 在本组件中始终为 null，默认不序列化。

前端从响应顶层读取 `items/total/pagination` 或 `aggregations/afterKey`，不要额外期待 `data` 包装。使用 HTTP 状态区分参数拒绝、资源失效和外部调用失败，再用 `requestId` 关联安全诊断信息；不要将后两类错误展示成“没有数据”。关闭路径中的游标是一个不透明值，构造 URL 时必须编码路径段。

#### 错误处理

本组件处理的参数错误、字段拒绝和 ES 调用故障使用 400/404/502 等非成功 HTTP 状态。组件的错误响应仅包含安全的 `message/timestamp/requestId`，不包含业务错误码、异常链或 ES 原始响应，例如参数无效时：

```json
{
  "message": "查询参数无效或超出限制",
  "timestamp": "2026-01-08T00:00:00Z",
  "requestId": "00000000-0000-4000-8000-000000000001"
}
```

宿主鉴权失败、框架产生的 405/415 等错误不由本组件统一改写，响应结构取决于宿主。调用方应先检查 HTTP 状态，再读取可用的安全错误字段；不能将所有错误都假定为上述结构。

### 10. 扩展、事件与诊断

默认 Engine、ExpressionService、查询/聚合编译器、结果解析器、元数据管理器、字段保护器和游标/JSON 编解码器均按类型让位。JSON 使用组件独占 ObjectMapper，不受宿主自定义 JSON Bean 影响。

同次查询不跨源；无法证明唯一归属的多源通配请求拒绝。客户端和连接由 Route 关闭，业务不得自行关闭。

DEBUG 仅记录数据源键、计数、类型、状态和耗时；Core 查询/聚合事件使用独立统计快照，不携带原始条件、文档、游标、实际索引或原响应。监听器失败不改变结果。确需排障时按包 `io.github.surezzzzzz.sdk.elasticsearch.search` 开启 DEBUG，不记录正文或游标；自定义扩展也应遵守相同边界。

## 兼容性与能力边界

| 项目 | 范围与验证基线 |
| --- | --- |
| 本组件 | `1.0.0`，Java 17 编译目标 |
| Spring Boot | `3.4.2` |
| Java 运行时 | 已验证 `17.0.20.1`、`21.0.12.1` |
| ES 支持范围 | `7.17+`、`8.x`；不支持 `6.x`、`9.x` |
| 真实 ES 验证 | `7.17.16`、`8.17.0`，两个独立单节点集群，不代表多节点或真实网络故障验收 |

上述环境均执行完整模块测试；生成的 JAR/POM 已由独立 Spring Boot `3.4.2` / Java `17` 宿主消费，完成 ES 两版本的写入后计数验证。其他 Spring Boot 补丁版本和 ES 补丁组合没有由此自动获得实测结论。

自然语言查询不属于 `1.0.0` 能力，NL 端点不注册；条件表达式使用独立的 Condition Jakarta 解析器并进入既有 Engine。本组件不包含独立分页策略注册表，可替换整体 Engine、ExpressionService 或现有编译/解析接口。与 javax Search Starter 互斥使用，不支持旧 RestHighLevelClient（旧版高层客户端）扩展。
