# CHANGELOG - simple-elasticsearch-route-starter 1.1.0

## 发布日期

2026-07-01

## 版本类型

功能增强 / 多版本兼容增强

## 变更概述

1. **增强 Spring Boot 2.x 多版本兼容**：主版本保持 Spring Boot 2.7.9，同时补齐 2.2.x、2.3.12、2.4.5 下的编译与运行兼容。
2. **新增日期分片路由**：写操作支持 `write-index-template`，读操作支持 `read-index-pattern`，覆盖年、年月、年月日等常见分片格式。
3. **新增异步写**：规则维度支持 `async-write=true`，写请求提交到数据源独立线程池后立即返回。
4. **新增代理类型配置**：支持 `proxy-type: CGLIB | JDK | AUTO`，默认 `AUTO`，优先使用 CGLIB，失败后回退到 JDK 代理。
5. **完善 Spring Data Elasticsearch 3.x 兼容调用**：在旧版 API 未声明 `save(Object)`、`get(String, Class)` 或缺少 `IndexCoordinates` 时，使用 HTTP 兼容路径完成路由。
6. **完善端到端测试**：日期分片、异步写、写入路由、读取路由均增加真实 ES 写入/读取与严格断言。
7. **清理日志、错误文案与注释**：避免将 Spring Data API、RestHighLevelClient 依赖版本和 ES 服务端版本混为一谈。

---

## 1. Spring Boot 2.x 多版本兼容

### 支持范围

| Spring Boot | Spring Data Elasticsearch API 风格 | 当前状态 | 说明 |
|-------------|------------------------------------|----------|------|
| 2.2.x | 3.x | 支持 | 无 `IndexCoordinates`，部分方法未声明在接口上，走 HTTP 兼容调用 |
| 2.3.12 | 3.x / 4.x 过渡 | 支持 | 通过反射和 JDK 代理兼容不同 API 形态 |
| 2.4.5 | 4.x | 支持 | `AUTO` 模式下 CGLIB 不可用时回退到 JDK 代理 |
| 2.7.9 | 4.x | 支持，主版本 | 默认验证版本，优先保障该版本行为不退化 |

### 兼容策略

- `IndexCoordinates` 不再作为硬编码编译依赖，统一通过反射检测。
- Spring Data Elasticsearch 3.x 环境下：
  - `save(Object)` 通过低层 HTTP 写入目标索引。
  - `get(String, Class)` 通过 HTTP 查询兼容路径读取。
  - 配置了 `read-index-pattern` 时，按 `_search` + `ids` 查询读取通配索引。
  - `index/createIndex/deleteIndex/indexExists/indexOps` 等字符串索引方法使用兼容路径处理。
- JDK 代理通过 `SaveAndGetInterface` 补齐旧版接口未声明的方法，确保 save/get 仍能被路由拦截。
- `proxy-type=AUTO` 优先 CGLIB，创建失败时回退到 JDK 代理。

---

## 2. 日期分片索引路由

### 配置示例

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          route:
            rules:
              - pattern: date_shard-*
                type: wildcard
                datasource: secondary
                write-index-template: date_shard-{yyyy.MM.dd}
                read-index-pattern: date_shard-*
                enable: true
```

### 行为说明

| 场景 | 使用索引 |
|------|----------|
| 写操作 + `write-index-template=date_shard-{yyyy}` | `date_shard-2026` |
| 写操作 + `write-index-template=date_shard-{yyyy.MM}` | `date_shard-2026.07` |
| 写操作 + `write-index-template=date_shard-{yyyy.MM.dd}` | `date_shard-2026.07.01` |
| 读操作 + `read-index-pattern=date_shard-*` | `date_shard-*` |
| 未配置模板或读模式 | 使用原始索引名继续普通路由 |

### 注意事项

- 日期模板使用 JDK `DateTimeFormatter` 语法。
- 日期在主线程渲染后再执行写入，异步写场景不会因为线程延迟重新计算日期。
- 模板格式非法时记录 WARN 日志并使用原始模板，不中断业务调用。

---

## 3. 读取路由增强

`read-index-pattern` 不只覆盖 `search`，也覆盖按 ID 读取场景。

- `get(id, EntityClass.class)` 命中带 `read-index-pattern` 的规则时，会使用 `_search` + `ids` 查询。
- 该实现用于支持通配索引，例如 `date_shard-*`。
- 返回结果取第一条命中的 `_source` 并反序列化为目标实体。
- 反序列化忽略未知字段，兼容 Spring Data 写入的 `_class` 字段。

---

## 4. 异步写

### 配置示例

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          route:
            sources:
              secondary:
                urls: http://localhost:9200
                async-write-thread-pool-size: 8
            rules:
              - pattern: async_write-*
                type: wildcard
                datasource: secondary
                async-write: true
                write-index-template: async_write-{yyyy.MM.dd}
```

### 行为说明

- `async-write=true` 时，写操作提交到线程池后立即返回 `null`。
- 线程池按 datasource 隔离。
- 异步写异常记录日志，不向调用线程传播。
- Spring 容器关闭时会先优雅等待，超时后中断线程池。

### 适用场景

- 日志、审计、埋点等允许最终一致的写入。
- 不适合需要立即确认写入结果、强一致性或事务语义的业务写入。

---

## 5. 代理类型配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          route:
            proxy-type: AUTO
```

| 模式 | 说明 | 建议 |
|------|------|------|
| `CGLIB` | 使用 CGLIB 子类代理，方法覆盖最完整 | Spring Boot 2.7.9 主版本可用 |
| `JDK` | 使用 JDK 动态代理，依赖接口方法和扩展接口 | 旧版本兼容或 CGLIB 受限时使用 |
| `AUTO` | 优先 CGLIB，失败后回退到 JDK | 默认推荐 |

---

## 6. 日志与错误文案清理

本版本统一清理了主代码日志、异常消息和注释：

- 删除或改写把客户端依赖版本、Spring Data API 形态和 ES 服务端版本混在一起的描述。
- 明确区分：
  - ES 服务端版本：由 `server-version` 或自动探测得到。
  - RestHighLevelClient 依赖版本：由当前 Spring Boot / Spring Data 依赖树决定。
  - Spring Data Elasticsearch API 形态：决定是否需要 HTTP 兼容调用或反射调用。
- 错误消息统一走 `ErrorCode` / `ErrorMessage`，避免裸字符串分散。
- 常量类改为 `final`，补齐 HTTP 状态、JSON 字段、反射类名/方法名等常量。

---

## 7. 测试增强

### 覆盖内容

- Spring Boot 2.7.9 主版本编译与端到端测试。
- Spring Boot 2.2.x / 2.3.12 / 2.4.5 旧版本兼容测试。
- 日期分片写入覆盖：年、年月、年月日。
- 异步写真实写入 ES 并读取校验。
- `read-index-pattern` 下的 `get(id, Class)` 读取路由。
- 写入后使用低层 RestClient 读取 ES 原始响应，并严格断言 `_index`、`_id`、`_source` 字段。

---

## 8. 配置新增/增强字段

| 路径 | 字段 | 类型 | 默认值 | 说明 |
|------|------|------|--------|------|
| 顶级 | `proxy-type` | String | `AUTO` | 代理模式：`CGLIB` / `JDK` / `AUTO` |
| `sources.*` | `async-write-thread-pool-size` | Integer | `8` | 当前数据源异步写核心线程数 |
| `rules[]` | `write-index-template` | String | 空 | 写索引日期模板 |
| `rules[]` | `read-index-pattern` | String | 空 | 读索引通配模式 |
| `rules[]` | `async-write` | Boolean | `false` | 是否启用异步写 |

---

## 9. 向后兼容性

- 默认 `proxy-type=AUTO`，Spring Boot 2.7.9 下仍优先使用 CGLIB。
- 未配置 `write-index-template` / `read-index-pattern` / `async-write` 时，保持原普通路由行为。
- 旧版本 Spring Data Elasticsearch 缺失的 API 通过反射或 HTTP 兼容路径处理，不要求业务侧修改调用方式。

---

## 10. 升级方式

```gradle
implementation "io.github.sure-zzzzzz:simple-elasticsearch-route-starter:1.1.0"
```

