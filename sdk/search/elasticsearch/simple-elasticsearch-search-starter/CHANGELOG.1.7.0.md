# v1.7.0 更新日志

**发布日期：** 2026-07-09

**类型：** Feature - 适配 route-starter 1.2.0，支持请求具体索引匹配配置通配符 `name`

**依赖版本：**

| 依赖 | 版本 |
|------|------|
| `simple-elasticsearch-search-core` | 1.0.12 |
| `simple-elasticsearch-route-starter` | 1.2.0 |

---

## 新增能力

### 请求具体索引匹配通配符配置

配置 `name: "test_wildcard--*"` 后，请求可以传具体索引名：

```json
{
  "index": "test_wildcard--2026.07.09",
  "query": {
    "field": "extraField",
    "op": "eq",
    "value": "mock-value"
  }
}
```

匹配规则：

1. 精确匹配 `alias`。
2. 精确匹配 `name`。
3. 按配置顺序匹配带通配符的 `name`。
4. 多个通配符配置同时命中时，使用配置顺序靠前的一项并记录 warning。

`alias` 不参与通配符匹配。具体索引匹配到通配符配置后，实际 ES 查询仍使用请求索引，字段元数据、敏感字段配置和日期配置使用匹配到的配置。

### INDEX_NOT_CONFIGURED 错误信息增强

索引未配置时，错误信息会同时展示请求索引和当前已配置索引列表，便于定位是索引名写错、alias 未配置，还是通配符配置未覆盖。

---

## route-starter 1.2.0 适配

search-starter 直接复用 route-starter 1.2.0 的 ES 兼容公共 Helper，不再保留只做转发的 search 兼容 Helper。

复用范围包括：

- totalHits 提取。
- mapping source 提取。
- count 低级 API。
- mapping 低级 API fallback。
- scroll / PIT endpoint 和请求体构造。
- ES6/ES7 响应解析与 DSL 兼容处理。
- 测试侧索引创建、删除、写入、刷新等低级 API 操作。

search 仍保留自身语义：

- 查询响应 `QueryResponse` 组装。
- 聚合响应 `AggResponse` 组装。
- ES6 聚合原始响应解析。
- 字段元数据与通配符配置匹配。
- 敏感字段处理。
- 日期范围路由与降级。

---

## 兼容性修复

### ES 6.x composite 聚合 DSL 兼容

ES 7.x client 生成 composite terms source 时会带 `missing_bucket:false`，ES 6.2.2 不识别该字段。

v1.7.0 在 ES 6.x 低级聚合路径复用 route DSL 兼容 Helper，发送请求前移除 ES6 不支持字段，并保留 search 自身的 ES6 聚合响应解析。

### 低版本 Spring Boot 下 scroll 二进制兼容

Spring Boot 2.2.x / 2.3.12 对应的 ES client 与 2.7.9 的 `Scroll.keepAlive()` 二进制签名不同。

v1.7.0 在 search 的 ES6 查询低级路径中由请求模型读取 `scrollTtl`，再使用 route 低级请求 Helper 构造请求，避免直接读取 `SearchRequest.scroll().keepAlive()` 带来的二进制兼容问题。

---

## 文档更新

- README 依赖示例升级到 `simple-elasticsearch-search-starter:1.7.0`。
- README 增加 `_id` 标准查询示例。
- README 增加通配符配置匹配具体索引示例。
- README 明确 `dateRange` 负责日期分片路由和日期字段过滤，业务过滤条件仍写在 `query`。
- 根 README 更新 search-starter 版本和 search/route 兼容矩阵。

---

## 新增/变更文件

| 文件 | 类型 | 说明 |
|------|------|------|
| `version.properties` | 修改 | 1.6.10 -> 1.7.0 |
| `build.gradle` | 修改 | route-starter 依赖升级到 1.2.0 |
| `src/main/java/.../metadata/model/ResolvedIndexConfig.java` | 新增 | 区分请求索引、配置索引和配置标识 |
| `src/main/java/.../metadata/MappingManager.java` | 修改 | 支持通配符配置匹配具体请求索引，复用 route mapping Helper |
| `src/main/java/.../executor/AbstractExecutor.java` | 修改 | 执行链使用已解析索引配置 |
| `src/main/java/.../query/executor/QueryExecutor.java` | 修改 | 复用 route Helper，修复 ES6 scroll 兼容路径 |
| `src/main/java/.../query/executor/CountExecutor.java` | 修改 | 复用 route count 低级 API Helper |
| `src/main/java/.../agg/executor/AggExecutor.java` | 修改 | 复用 route Helper，保留 search 聚合响应语义 |
| `src/main/java/.../query/pagination/PitPaginationStrategy.java` | 修改 | 复用 route PIT endpoint / body Helper |
| `src/main/java/.../constant/SimpleElasticsearchSearchConstant.java` | 修改 | 移除 route 已承接的 endpoint/body 常量 |
| `src/main/java/.../support/ElasticsearchCompatibilityHelper.java` | 删除 | route 1.2.0 已承接公共兼容能力 |
| `src/main/java/.../support/XContentReflectionHelper.java` | 删除 | route 1.2.0 已承接 XContent 兼容能力 |
| `src/main/java/.../support/DslCompatibilityHelper.java` | 删除 | route 1.2.0 已承接 DSL 兼容能力 |
| `src/test/java/.../test/cases/SearchEndToEndTest.java` | 修改 | 增强通配符具体索引、`_id`、ES6 composite 和 scroll 覆盖 |
| `src/test/java/.../test/helper/EsApiHelper.java` | 修改 | 测试侧 ES API 操作委托 route 低级 Helper |
| `README.md` | 修改 | 更新版本选型、依赖示例、通配符具体索引与 dateRange/query 说明 |
| 根目录 `README.md` | 修改 | 更新 search-starter 版本和 search/route 兼容矩阵 |
| `CHANGELOG.1.7.0.md` | 新增 | 本版本更新日志 |

---

## 向后兼容性

- 请求结构不变，继续使用 `index/query/dateRange/pagination/fields/countOnly/aggs/after`。
- 原 alias 精确匹配优先级保持最高。
- 原 name 精确匹配行为保持不变。
- 新增的通配符匹配仅作用于配置 `name`，不改变 `alias` 语义。
- 具体索引匹配通配符配置后，实际查询索引仍是请求索引，不会改写为通配符配置名。
- search 与 route 1.2.0 形成直接 helper 依赖，后续改动需同步检查 Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9 和 ES 6.2.2 / 7.x 兼容。

---

## 验证结果

| Spring Boot | Gradle / JDK | 范围 | 结果 |
|-------------|--------------|------|------|
| 2.2.x | Gradle 7.6 / Java 8 | `:sdk:search:elasticsearch:simple-elasticsearch-search-starter:test` | 通过 |
| 2.3.12 | Gradle 7.6 / Java 8 | `:sdk:search:elasticsearch:simple-elasticsearch-search-starter:test` | 通过 |
| 2.4.5 | Gradle 7.6 / Java 8 | `:sdk:search:elasticsearch:simple-elasticsearch-search-starter:test` | 通过 |
| 2.7.9 | Gradle 8.5 / Java 11 | `:sdk:search:elasticsearch:simple-elasticsearch-search-starter:test` | 通过 |

覆盖重点：

- 精确 alias 匹配。
- 精确 name 匹配。
- 请求具体索引匹配配置通配符 `name`。
- `alias` 不参与通配符匹配。
- 多通配符命中时按配置顺序选择。
- 未配置索引错误信息包含请求索引和配置列表。
- `_id` `eq/in/ne/not_in` 查询与 `countOnly=true`。
- ES 6.x composite 聚合 DSL 兼容。
- ES 6.x scroll 翻页兼容。
