# simple-elasticsearch-route-starter 1.1.2 更新日志

## 发布日期

2026-07-07

## 版本定位

1.1.2 是内部重构版本，将「写索引渲染能力」从 `RouteRoutingInterceptor` 内部抽取为独立
`WriteIndexResolver` 接口，供 route AOP 拦截器和外部 SDK（persistence-starter）复用。
渲染算法与 1.1.1 完全一致，用户无感知。内部扩展点开放，为 persistence-starter 1.0.0 铺路。

## 新增功能

### 1. WriteIndexResolver 接口与默认实现

`resolver/` 包下新增两个文件：

- `WriteIndexResolver.java` —— 写索引渲染能力契约接口
- `DefaultWriteIndexResolver.java` —— 默认实现，1.1.1 渲染算法原样搬迁

**6 个方法：**

```java
public interface WriteIndexResolver {
    /** 给 raw index name，先 resolveRule 再渲染。未命中规则或模板为空时返回原值。 */
    String resolveWriteIndex(String rawIndex);

    /** 给已命中的 rule，直接渲染写索引。rule 为 null 或模板为空时返回 null。 */
    String resolveWriteIndex(RouteRule rule);

    /** 按模板 + 指定时区渲染。 */
    String renderTemplate(String template, ZoneId zoneId);

    /** 按模板 + JVM 默认时区渲染。 */
    String renderTemplate(String template);

    /** 清除 DateTimeFormatter 缓存。 */
    void clearFormatterCache();

    /** 获取缓存大小。 */
    int getFormatterCacheSize();
}
```

**注册方式**：`@Bean @ConditionalOnMissingBean(WriteIndexResolver.class)`，
用户可自定义实现替换默认行为（implements `WriteIndexResolver` 替换，或 extends
`DefaultWriteIndexResolver` 部分覆盖）。

### 2. 渲染状态与拦截器解耦

`RouteRoutingInterceptor` 不再持有渲染相关字段和方法（`globalWriteIndexZoneId` /
`formatterCache` / `warnedInvalidZoneIds` / `renderTemplate` / `resolveZoneId`），
统一委托给 `WriteIndexResolver`。拦截器职责回归「拦截 save() → 路由数据源 → async-write 派发」。

### 3. WriteIndexResolver 单测（WriteIndexResolverTest）

新增 11 个纯单测，覆盖 `resolveWriteIndex(String)` 和 `resolveWriteIndex(RouteRule)` 全部分支：

- null 入参：rawIndex=null → null；rule=null → null
- fallback 语义：`resolveWriteIndex(String)` 未命中/无模板 → 返回 rawIndex；
  `resolveWriteIndex(RouteRule)` 无模板 → **返回 null**（两者 fallback 语义不同，显式验证）
- 渲染路径：命中带模板规则 → 渲染，时区优先规则级
- 非法时区降级：rule 级非法 → 降级全局，warn 去重不阻断；全局 null → 降级 JVM 默认

## 行为说明

### 1. 渲染结果与 1.1.1 完全一致

`DefaultWriteIndexResolver` 的渲染算法（模板解析、时区解析、formatter 缓存、非法 zoneId 告警）
与 1.1.1 interceptor 内部实现逐字节一致。升级后现有日期分片行为不受影响。

### 2. 两种 resolveWriteIndex 的 fallback 语义差异

| 方法 | rule=null | rule 无模板 | 未命中规则 |
|------|-----------|------------|-----------|
| `resolveWriteIndex(String rawIndex)` | null | 返回 rawIndex | 返回 rawIndex |
| `resolveWriteIndex(RouteRule rule)` | null | **null** | 不适用（rule 已命中） |

外部 SDK（persistence）应使用 `resolveWriteIndex(String)`，因为请求拿到的是 raw index。
`resolveWriteIndex(RouteRule)` 是给 interceptor 内部用的（rule 已命中，不需要再查）。

### 3. 向后兼容

`RouteRoutingInterceptor` 的 AOP 行为不变。`WriteIndexResolver` 是新增 bean，
不影响现有 bean 图。`@ConditionalOnMissingBean` 允许用户注入自定义实现覆盖默认。

## 测试覆盖

### 1. 新增单测

- `WriteIndexResolverTest`：11 个测试，纯单元测试（mock RouteResolver，无 Spring/ES），
  覆盖两个新入口的全分支 + 时区降级链路

### 2. 迁移测试

- `DateTimeFormatterCacheTest`：从 `RouteRoutingInterceptor` 迁移到 `WriteIndexResolver`，
  验证缓存行为（命中/隔离/清空/实例隔离）
- `DateShardingAndAsyncWriteTest`：接入 `@ActiveProfiles(resolver = WriteTestProfilesResolver.class)`，
  纳入 4 版本矩阵；`application-write-test.yaml` 补齐 4 条路由规则（async_write.* / date_shard.*），
  确保 write-test profile 下也能命中
- `RouteConfigurationCglibFallbackTest`：更新 interceptor 构造参数，适配 6 参新签名

### 3. 拦截器回归

4 条 AOP 路径（`route()` / `doSaveForEs3x()` / `doIndexForEs3x()` / `doAsyncWrite()`）均通过
`DateShardingAndAsyncWriteTest` 集成验证，确认渲染时序与 1.1.1 一致。

## 多版本验证结果

2026-07-07 发布前已按现有测试矩阵完成全量验证：

| Spring Boot | Profile | Gradle / Java | 验证范围 | 结果 |
|-------------|---------|---------------|----------|------|
| 2.2.x | `2.2.x` | Gradle 7.6 / Java 8 | DateShardingAndAsyncWriteTest | 通过 |
| 2.3.12 | `2.3.12` | Gradle 7.6 / Java 8 | DateShardingAndAsyncWriteTest | 通过 |
| 2.4.5 | `2.4.5` | Gradle 7.6 / Java 8 | DateShardingAndAsyncWriteTest | 通过 |
| 2.7.9 | `2.7.9` | Gradle 8.5 / Java 11 | DateShardingAndAsyncWriteTest | 通过 |

> `DateShardingAndAsyncWriteTest` 已在本次更新中接入 `@ActiveProfiles(resolver = WriteTestProfilesResolver.class)`，
> 旧版本使用 `GRADLE_USER_HOME=/d/gradle-6-test` 避免 Windows MAX_PATH 限制。

## 非本版本范围

- persistence-starter 接入 `WriteIndexResolver`（属于 persistence-starter 1.0.0）
- 渲染算法变更（模板语法、时区解析与 1.1.1 完全一致）
- `read-index.pattern` 日期渲染
