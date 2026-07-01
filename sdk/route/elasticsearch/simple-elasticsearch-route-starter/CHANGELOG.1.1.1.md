# simple-elasticsearch-route-starter 1.1.1 更新日志

## 发布日期

2026-07-01

## 版本定位

1.1.1 是 1.1.0 日期分片能力的增强版本，重点补齐时区配置与 DateTimeFormatter 本地缓存，保持 1.1.0 现有写入、读取、异步写和多版本兼容行为不变。

## 新增功能

### 1. 日期分片支持显式时区

`write-index.template` 渲染日期时支持配置 `write-index.zone-id`：

- 全局默认：`io.github.surezzzzzz.sdk.elasticsearch.route.write-index.zone-id`
- rule 级覆盖：`rules[].write-index.zone-id`
- 兼容旧平铺配置：`write-index-zone-id`、`write-index-template`、`read-index-pattern`

优先级：

```text
rule.write-index.zone-id > rule.write-index-zone-id > 全局 write-index.zone-id > 全局 write-index-zone-id > JVM 默认时区
```

示例：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        elasticsearch:
          route:
            write-index:
              zone-id: Asia/Shanghai
            rules:
              - pattern: "app-log-*"
                type: wildcard
                datasource: logging
                write-index:
                  template: "app-log-{yyyy.MM.dd}"

              - pattern: "audit-log-*"
                type: wildcard
                datasource: logging
                write-index:
                  template: "audit-log-{yyyy.MM.dd}"
                  zone-id: UTC
```

上述配置中：

- `app-log-*` 继承全局 `Asia/Shanghai`
- `audit-log-*` 使用 rule 级 `UTC` 覆盖全局时区
- 旧平铺字段仍兼容；新分组字段与旧字段同时配置时，新分组字段优先

### 2. DateTimeFormatter 本地缓存

`write-index.template` / `write-index-template` 渲染时会按 pattern 缓存 `DateTimeFormatter`，避免高频写场景反复创建 formatter 对象。

缓存行为：

- 相同 pattern 复用同一个 `DateTimeFormatter`
- 不同 pattern 独立缓存
- 支持 `clearFormatterCache()` 和 `getFormatterCacheSize()` 便于测试与排查
- pattern 语法合法但无法由 `LocalDate` 渲染时，原样返回模板且不保留该 formatter 缓存

## 行为说明

### 1. 向后兼容

未配置 `write-index.zone-id` / `write-index-zone-id` 时，行为与 1.1.0 一致，仍使用 JVM 默认时区。

### 2. 非法时区处理

非法 `write-index.zone-id` / `write-index-zone-id` 不阻断启动：

- 全局非法：WARN 后降级为 JVM 默认时区
- rule 级非法：WARN 后降级为全局默认时区；若全局也不可用则使用 JVM 默认时区

### 3. 读操作不受影响

`read-index.pattern` / `read-index-pattern` 仍作为静态索引名或通配符直接传给 Elasticsearch，不做日期模板渲染，因此不受 `write-index.zone-id` 影响。

## 测试覆盖

### 1. 单元测试

新增/扩展测试覆盖：

- `renderTemplate(template, ZoneId)` 按指定时区渲染
- 无参 `renderTemplate(template)` 仍使用 JVM 默认时区
- DateTimeFormatter 同 pattern 命中缓存
- 多 pattern 独立缓存
- 清空缓存后可重新建立
- 不同 `RouteRoutingInterceptor` 实例缓存互不干扰

### 2. 集成测试

新增 `TimezoneConfigIntegrationTest`：

- 使用 `Pacific/Kiritimati`（全局）与 `Etc/GMT+12`（rule 级）固定跨日组合，稳定验证 rule 级覆盖全局
- 验证未配置 rule 级时继承全局时区
- 通过代理 `save` 写入，断言返回对象类型、`id`、`value`
- 使用低层 RestClient 直查写入索引，严格断言 HTTP status、`found`、`_index`、`_id`、`_source.id`、`_source.value`
- 直查错误时区对应索引并断言 404，确认不会写入错误分片
- 通过代理 `get` 走 `read-index.pattern` 读回，断言 `id`、`value`

## 多版本验证结果

2026-07-01 发布前已按现有测试矩阵完成全量验证：

| Spring Boot | Profile | Gradle / Java | 验证范围 | 结果 |
|-------------|---------|---------------|----------|------|
| 2.2.x | `2.2.x` | Gradle 7.6 / Java 8 | 全量 test | 通过 |
| 2.3.12 | `2.3.12` | Gradle 7.6 / Java 8 | 全量 test | 通过 |
| 2.4.5 | `2.4.5` | Gradle 7.6 / Java 8 | 全量 test | 通过 |
| 2.7.9 | 默认 / `2.7.9` | Gradle 8.5 / Java 11 | 全量 test | 通过 |

> 旧的 `es23` profile 不再作为测试入口；旧版 Spring Boot 测试需使用对应版本 profile，确保 `application-write-test-<版本>.yaml` 生效。

## 非本版本范围

- 多占位符日期模板（当前仍支持单个 `{pattern}`）
- `read-index.pattern` / `read-index-pattern` 日期渲染
- 新路由类型或代理机制变更
