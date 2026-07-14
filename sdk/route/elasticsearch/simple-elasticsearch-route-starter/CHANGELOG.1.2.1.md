# simple-elasticsearch-route-starter 1.2.1 更新日志

## 发布日期

2026-07-14

## 版本定位

1.2.1 是 by-query low-level REST 参数位置修复版本，修复 1.2.0 公共 helper 无法正确表达 `_update_by_query` / `_delete_by_query` URL query parameter 的问题。

本版本为 Patch Release，只调整 route 公共协议 helper，不新增 Spring Bean，不改变自动配置、路由、客户端创建和 High Level Client 执行行为。

## 问题修复

1.2.0 的 `ElasticsearchRequestOptionHelper.applyByQueryBodyOptions(...)` 会将 `refresh`、`timeout`、`slices`、`conflicts`、`scroll_size` 写入 JSON body。

真实 Elasticsearch 7.9.3 / 7.17.16 已验证：

- `refresh`、`slices`、`scroll_size` 位于 body 时返回 HTTP 400 `parsing_exception`。
- `conflicts`、`timeout` 虽可被部分服务端版本从 body 解析，也应按 REST 协议统一放入 URL query parameter。

1.2.1 新增正确的 Request 参数映射入口，统一将以下执行选项写入 URL query：

| 参数对象字段 | URL 参数 |
|--------------|----------|
| `waitForCompletion` | `wait_for_completion` |
| `refresh` | `refresh` |
| `timeoutMs` | `timeout` |
| `slices` | `slices` |
| `conflicts` | `conflicts` |
| `scrollSize` | `scroll_size` |
| `requestsPerSecond` | `requests_per_second` |
| `maxDocs` | `max_docs` |
| `waitForActiveShards` | `wait_for_active_shards` |
| `routing` | `routing` |

## 新增 API

新增不可变参数模型：

```java
ByQueryRequestOptions options = ByQueryRequestOptions.builder()
        .waitForCompletion(false)
        .refresh(false)
        .timeoutMs(30000L)
        .slices(2)
        .conflicts("proceed")
        .scrollSize(500)
        .requestsPerSecond(500f)
        .maxDocs(1000L)
        .waitForActiveShards(1)
        .routing("shard_key")
        .build();
```

新增对象参数 helper：

```java
ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request, options);
```

参数对象使用 `@Getter + @Builder` 和 `final` 字段。后续增加可选 by-query 参数时，不需要改变 helper 方法描述符或增加多参数重载。

## 兼容性

- 保留 `applyByQueryBodyOptions(Map, Boolean, Long, Integer, String, Integer)` 原签名和原行为，避免 1.2.0 已编译调用方出现 `NoSuchMethodError`。
- 旧 body helper 自 1.2.1 起标记 `@Deprecated`，新代码必须迁移到 Request 参数 helper。
- 不保留开发期间尚未发布的七参数 `applyByQueryRequestOptions(...)` 临时签名。
- 不新增依赖，不使用 JDK 9+ API，不引入 Spring Boot 3.x / jakarta 依赖。
- 支持 Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9 既有兼容矩阵。
- `SimpleElasticsearchRouteConstant` 新增 `PARAM_REQUESTS_PER_SECOND`、`PARAM_MAX_DOCS`、`PARAM_WAIT_FOR_ACTIVE_SHARDS`、`PARAM_ROUTING`。

## 测试

新增覆盖：

- 十个 URL query parameter 的完整映射（含 `requests_per_second`、`max_docs`、`wait_for_active_shards`、`routing`）。
- `waitForCompletion`、`refresh` 的 true / false 显式写入。
- `request == null`、`options == null`、空 builder 对象安全处理。
- `conflicts` 为空串或全空白时不透传，带空格时原值透传。
- `requestsPerSecond` 的 null / 正数 / 0 / -1 / 小数行为。
- `maxDocs` 和 `waitForActiveShards` 的 null / 正数 / 0 行为。
- `routing` 的 null / 正常值 / 空白不透传。
- 部分字段配置时仅写入对应参数。
- JSON body 不混入 URL 参数。
- 新对象参数方法的 public/static/void API 结构。
- 旧 body helper 的原值类型、原方法签名和 `@Deprecated` 兼容性。

| Spring Boot | 结果 |
|-------------|------|
| 2.7.9 | 139 测试全通过 |

## 升级指南

旧代码：

```java
ElasticsearchRequestOptionHelper.applyByQueryBodyOptions(
        body, refresh, timeoutMs, slices, conflicts, scrollSize);
```

新代码：

```java
ByQueryRequestOptions options = ByQueryRequestOptions.builder()
        .waitForCompletion(waitForCompletion)
        .refresh(refresh)
        .timeoutMs(timeoutMs)
        .slices(slices)
        .conflicts(conflicts)
        .scrollSize(scrollSize)
        .requestsPerSecond(requestsPerSecond)
        .maxDocs(maxDocs)
        .waitForActiveShards(waitForActiveShards)
        .routing(routing)
        .build();
ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request, options);
```

JSON body 只保留 `query` / `script` 等请求内容，执行选项全部通过 Request URL 参数传递。
