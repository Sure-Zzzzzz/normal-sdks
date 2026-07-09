# simple-elasticsearch-route-starter 1.2.0 更新日志

## 发布日期

2026-07-09

## 版本定位

1.2.0 是 ES 兼容公共能力下沉版本，将 search-starter / persistence-starter 中可复用、且不依赖下游业务模型的 ES 6.x / 7.x 兼容经验沉淀到 route 的公共 support 层。

本版本只新增公共 helper、模型、异常、常量和 `ServerVersion` 判断方法，不删除、不重命名、不改变现有 public API，不改变 route 自动配置、路由、日期分片、异步写和客户端创建行为。

## 新增功能

### 1. 11 个 ES 兼容公共 Helper

`support/` 包新增或扩展以下公共 helper：

| Helper | 职责 |
|--------|------|
| `ElasticsearchVersionHelper` | ES6/ES7、PIT、composite、stable after_key、missing_bucket 能力判断 |
| `ElasticsearchReflectionHelper` | class / method / field / constructor 探测与反射调用 |
| `XContentCompatibilityHelper` | ES6/ES7 XContent 包路径、NamedXContentRegistry、SearchResponse / count response 解析 |
| `ElasticsearchEndpointHelper` | `_search` / `_count` / `_mapping` / by-query / task / PIT / scroll / index / doc / refresh endpoint 与 body 构造 |
| `ElasticsearchLowLevelRequestHelper` | low-level Request 构造、参数、body、response 读取、search/count/mapping 执行、索引/文档 RestClient 协议操作 |
| `ElasticsearchRequestOptionHelper` | `IndicesOptions`、refresh、timeout、by-query 参数映射 |
| `ElasticsearchWriteRequestHelper` | ES6/ES7 typed 写请求构造与高层写请求 setter |
| `ElasticsearchResponseHelper` | totalHits、bulk failure、task Map、mapping sourceAsMap、RestStatus、doc write result、兼容异常识别 |
| `ElasticsearchAggregationCompatibilityHelper` | metrics / pipeline 聚合类包路径、方法签名、filters buckets Map/List、after_key 提取 |
| `ElasticsearchSearchContextHelper` | PIT 类探测、PIT endpoint/body/apply、scroll 第一页/续页/清理协议 |
| `ElasticsearchDslCompatibilityHelper` | 递归清理 ES6 不支持的 composite DSL 字段 |

这些 helper 均为无状态静态工具，不新增 Spring bean，不改变现有 bean 图。

### 2. RestClient 索引与文档协议能力下沉

`ElasticsearchLowLevelRequestHelper` 补齐 search / persistence 测试夹具中重复出现的 RestClient 协议操作：

- index exists / create / delete / recreate
- ES6 typed mapping body 与 ES7 mappings body 自适配
- index doc / delete doc
- doc exists / get doc `_source`
- refresh index
- 404 语义归一：exists 返回 false，get doc 返回 null，delete 忽略缺失资源
- 其它 low-level 请求失败统一抛 `ElasticsearchLowLevelRequestException`

这部分只提供 ES 协议级 helper，不引入 persistence request/result/event/executor 语义。

### 3. XContent 与 SearchResponse 解析兼容

新增 XContent 兼容层：

- 自动探测 `org.elasticsearch.xcontent.*` 与 `org.elasticsearch.common.xcontent.*`
- 兼容不同 ES 客户端版本下 `NamedXContentRegistry` / `SearchModule` 构造差异
- low-level `_search` 响应可解析为 `SearchResponse`
- ES6 聚合响应在 ES7 client parse 不稳定时可保留 raw response，由 search 自己处理聚合业务语义

### 4. 聚合协议兼容能力

新增 ES 原生聚合协议 helper：

- metrics 聚合类 ES6/ES7 包路径兼容
- pipeline aggregation builder 包路径兼容
- `BucketSortPipelineAggregationBuilder.size(Integer)` 方法签名兼容
- filters buckets Map/List 结构识别
- composite `after_key` 提取
- composite `missing_bucket` / `missing_order` 机械清理

route 只处理 ES 原生协议差异，不转换 search 的聚合响应模型。

### 5. PIT / scroll / count / mapping 协议能力

新增搜索上下文和 low-level 查询协议能力：

- PIT 支持版本判断与 `PointInTimeBuilder` 反射探测
- open PIT / close PIT endpoint 与 body 构造
- `SearchSourceBuilder` apply PIT 反射调用
- scroll 第一页 `_search?scroll=`、续页、清理请求构造
- `_count` low-level 执行并完整应用 `IndicesOptions`
- `_mapping` low-level fallback 与 mapping sourceAsMap 提取

分页策略、生命周期决策、返回结构仍属于 search-starter。

### 6. route 兼容异常体系

新增兼容公共能力相关异常：

- `ElasticsearchCompatibilityException`
- `ElasticsearchReflectionException`
- `ElasticsearchXContentException`
- `ElasticsearchLowLevelRequestException`

新增 `ROUTE_COMPAT_*` 错误码和中文错误消息，helper 失败统一进入 route 自定义异常体系，不使用 `IllegalStateException` 表达兼容错误。

### 7. ServerVersion 语义化判断方法

`ServerVersion` 新增实例判断方法：

- `isEs6()`
- `isEs7()`
- `isSameMajor(int)`
- `isAtLeast(int)`
- `isAtLeast(int, int)`
- `isBefore(int)`
- `isBefore(int, int)`

下游不需要重复解析 major/minor。

## 行为说明

### 1. 向后兼容

本版本是新增能力版本：

- 不改变 route 配置前缀和配置结构
- 不改变数据源初始化逻辑
- 不改变路由匹配逻辑
- 不改变 `ElasticsearchRestTemplate` 代理逻辑
- 不改变日期分片、时区、async-write 行为
- 不改变 `WriteIndexResolver` 行为

现有业务升级后不需要改配置或代码。

### 2. 下沉边界

本版本下沉的是 ES 客户端层、协议层、响应结构层的公共兼容能力。

不下沉：

- search 查询 DSL、聚合 DSL、聚合结果模型、分页策略、字段元数据、安全语义、事件语义
- persistence request/result/executor/event、bulk 分批策略、失败分类扩展点、文档元数据处理

### 3. 直接引用约束

实现中不直接引用 ES7-only 类型：

- `org.elasticsearch.xcontent.*`
- `org.elasticsearch.search.builder.PointInTimeBuilder`
- `org.apache.lucene.search.TotalHits`

涉及 ES6/ES7 差异的类名和方法签名均通过字符串常量与反射处理。

## 测试覆盖

### 1. 新增 / 扩展单测

本版本新增或扩展 11 组 helper 测试：

- `ElasticsearchVersionHelperTest`
- `ElasticsearchReflectionHelperTest`
- `XContentCompatibilityHelperTest`
- `ElasticsearchEndpointHelperTest`
- `ElasticsearchLowLevelRequestHelperTest`
- `ElasticsearchRequestOptionHelperTest`
- `ElasticsearchWriteRequestHelperTest`
- `ElasticsearchResponseHelperTest`
- `ElasticsearchAggregationCompatibilityHelperTest`
- `ElasticsearchSearchContextHelperTest`
- `ElasticsearchDslCompatibilityHelperTest`

重点覆盖：

- ES6/ES7 版本能力判断
- XContent 包路径与 SearchResponse 解析
- `totalHits` 反射读取
- `_count` / `_mapping` / `_search` low-level 协议
- index/doc/refresh RestClient 协议请求 method、path、query、body
- PIT / scroll 协议构造
- 聚合类包路径和 `size(Integer)` 签名
- composite DSL 清理
- mapping sourceAsMap 提取失败走 route 自定义异常
- bulk failure reason 优先读取 cause message，兼容 ES 6.2.2

### 2. 发布前自查

发布前已完成以下自查：

- 根 `build.gradle` 已恢复 Spring Boot `2.7.9`
- 未发现 ES7-only / Lucene TotalHits 直接 import
- route support helper 未使用 `IllegalStateException` / `IllegalArgumentException` / `RuntimeException` 表达兼容错误
- 未使用 JDK 9+ / 11+ API 破坏 Java 8 编译兼容
- 文档示例未写入真实业务索引、字段或敏感信息

## 多版本验证结果

2026-07-09 发布前已按 normal-sdks 现有测试矩阵完成全量验证：

| Spring Boot | Profile | Gradle / Java | 验证范围 | 结果 |
|-------------|---------|---------------|----------|------|
| 2.2.x | `2.2.x` | Gradle 7.6 / Java 8 | route 全量 test | `BUILD SUCCESSFUL` |
| 2.3.12 | `2.3.12` | Gradle 7.6 / Java 8 | route 全量 test | `BUILD SUCCESSFUL` |
| 2.4.5 | `2.4.5` | Gradle 7.6 / Java 8 | route 全量 test | `BUILD SUCCESSFUL` |
| 2.7.9 | 默认 / `2.7.9` | Gradle 8.5 / Java 11 | route 全量 test | `BUILD SUCCESSFUL` |

## 非本版本范围

- search-starter 接入 route 1.2.0 helper
- persistence-starter 接入 route 1.2.0 helper
- route 配置结构调整
- route 代理机制调整
- 日期分片和 async-write 行为调整
- 将 search / persistence 的业务模型移动到 route
