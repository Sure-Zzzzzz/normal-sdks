# SmartRedisLimiter Core

[![Version](https://img.shields.io/badge/version-2.1.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

`smart-redis-limiter-core` 是 `smart-redis-limiter-starter` 的核心契约层，定义限流事件、审计记录模型、扩展接口、错误码和统一常量。

## 版本定位

`2.1.0` 是在已发布 core 2.0.0 Redis Route 公共事件契约基础上的向后兼容功能版本，为后续 limiter starter 2.0.0 与 management starter 1.0.0 提供动态策略公共协议。

`2.0.0` 已完成 Redis Route 公共事件契约升级；`2.1.0` 在保持 2.0.0 兼容的前提下新增动态策略、快照和管理操作事件协议。1.x 已封版，历史文档见 [README.1.x.md](README.1.x.md)。

## 核心组件

### 1. SmartRedisLimiterEvent

限流事件，在 Interceptor（拦截器模式）或 Aspect（注解模式）限流结果产生后发布。

`SmartRedisLimiterEventPayload` 继续作为事件载荷：

```java
SmartRedisLimiterEvent event = new SmartRedisLimiterEvent(publisher,
        SmartRedisLimiterEventPayload.builder()
                .limitKey(limitKey)
                .routeKey(routeKey)
                .datasourceKey(datasourceKey)
                .redisMode(redisMode)
                .routeRequired(true)
                .routeResolved(true)
                .keyStrategy(keyStrategy)
                .algorithm(algorithm)
                .limitRules(limitRules)
                .passed(passed)
                .sourceType(SmartRedisLimiterConstant.SOURCE_INTERCEPTOR)
                .fallbackReason(fallbackReason)
                .build());
```

新增 route / fallback 字段：

| 字段 | 说明 |
|------|------|
| `routeKey` | 用于 redis-route 选择 datasource 的逻辑 key |
| `datasourceKey` | redis-route 解析得到的 datasource key |
| `redisMode` | Redis 模式：standalone / cluster / unknown |
| `routeRequired` | 是否要求通过 redis-route 执行 |
| `routeResolved` | 是否成功拿到可观测 datasource 信息 |
| `fallbackReason` | 降级原因 |

兼容说明：

- 旧 18 参构造器保留为 `@Deprecated` 兼容桥。
- 1.x 常用 getter 继续保留并委托到 payload。
- `getSource()` 继续返回限流来源字符串，兼容 1.x 语义。
- `getRawSource()` 返回原始事件发布者。

### 2. SmartRedisLimiterEventPayload

事件载荷模型，字段不可变，使用 builder 构建。

`attributes` 在构造时生成递归不可变快照，避免异步监听器读取到发布后的修改。属性值仅支持：

- JSON 基础值和 `null`；
- 枚举、`Instant`、`UUID`；
- 键为 `String` 的 `Map`；
- `List`、`Set` 和数组。

嵌套容器会递归复制并包装为不可变对象，数组转换为不可变 `List`；未知可变对象、非字符串 Map Key 和循环引用会以 `VALIDATION_012` 拒绝。

### 3. SmartRedisLimiterRecord

审计记录 DTO，事件监听器可将 Event 转换为 Record 后交给 Handler 处理。

已发布 2.0.0 提供 route 字段：

- `routeKey`
- `datasourceKey`
- `redisMode`
- `routeRequired`
- `routeResolved`
- `fallbackReason`

Record 为兼容既有 API 保持可变，并保留无参构造器、setter/getter、builder 和 2.0.0 旧全参构造器。动态策略字段完成组装后，应在进入审计处理边界前调用：

```java
record.validatePolicyContext();
```

该方法会将空 `policySource` 规范化为 `local`，规范化 `resourceCode`，并验证 local / remote 与 `policyRevision` 的组合。

### 4. 动态策略协议

`2.1.0` 新增一事一议的动态策略公共模型：

```java
SmartRedisLimiterPolicyKey key = new SmartRedisLimiterPolicyKey(
        "test-service", "test-resource", "test-subject");
SmartRedisLimiterPolicy policy = new SmartRedisLimiterPolicy(key, Arrays.asList(
        new SmartRedisLimiterLimit(10L, 1L, SmartRedisLimiterTimeUnit.SECONDS),
        new SmartRedisLimiterLimit(300L, 1L, SmartRedisLimiterTimeUnit.MINUTES)));
SmartRedisLimiterPolicySnapshot snapshot = new SmartRedisLimiterPolicySnapshot(
        SmartRedisLimiterConstant.POLICY_SCHEMA_VERSION,
        "test-service", 1L, Instant.now(), Collections.singletonList(policy));
```

策略键按 `serviceCode + resourceCode + subject` 精确匹配；一条策略包含完整 limits，不进行窗口级隐式合并。空 policies 是有效完整快照，表示当前服务没有动态覆盖。

窗口按标准化秒数确定语义：`60 SECONDS` 与 `1 MINUTES` 属于同一窗口。`SmartRedisLimiterLimit` 的 equals/hashCode 同样按 `count + windowSeconds` 比较，确保窗口去重、策略比较和快照比较使用一致语义。

快照 JSON 必填字段通过显式 creator/property 契约固定；缺失或为 null 的 revision 不会被静默转换为 0。

### 5. 管理操作事件

`SmartRedisLimiterManagementEvent` 表达 CREATE / UPDATE / ENABLE / DISABLE / DELETE 的前后策略、启用状态、revision 和操作人。core 只定义 Spring Event，不依赖审计 listener 或持久化实现。

管理事件载荷会校验前后策略 Key 和操作状态矩阵：

| 操作 | 合法状态 |
|---|---|
| CREATE | 仅包含 after policy/state |
| UPDATE | 同时包含前后 policy/state，启用状态不变 |
| ENABLE | 策略不变，状态从 false 变为 true |
| DISABLE | 策略不变，状态从 true 变为 false |
| DELETE | 仅包含 before policy/state |

管理事件的 operation、policyKey、revision、operator、occurredAt 均为 JSON 必填字段；attributes 使用与执行事件一致的严格递归不可变快照。

### 6. 执行策略可观测字段

Event/Payload/Record 新增：

- `resourceCode`
- `policySource`：local / remote
- `policyRevision`

上下文规则：

- local：`policyRevision` 必须为空，`resourceCode` 可为空；
- remote：`resourceCode` 必填，`policyRevision` 必填且不能小于 0；
- 旧构造器默认使用 local，不影响 2.0.0 调用方。

执行事件 payload 为空时使用标准错误码 `VALIDATION_013`；策略上下文非法时使用 `VALIDATION_011`。

### 7. Provider 接口

`SmartRedisLimiterUserProvider` 用于从当前请求上下文中提取用户身份：

```java
public interface SmartRedisLimiterUserProvider {
    String getClientId();
    String getClientType();
    String getUserId();
    String getUsername();
}
```

`SmartRedisLimiterTraceIdProvider` 用于提取链路追踪 ID：

```java
public interface SmartRedisLimiterTraceIdProvider {
    String getTraceId();
}
```

SDK 不提供默认实现，由调用方按需注册。

### 8. 错误码和错误消息

core 使用标准错误码和错误消息类；2.1.0 在其中追加动态策略校验契约：

| 类 | 说明 |
|----|------|
| `ErrorCode` | 标准错误码 |
| `ErrorMessage` | 标准错误消息 |

旧 `SmartRedisLimiterConstant.ERROR_CODE_*` / `MSG_*` 常量保留为 `@Deprecated` 兼容别名。

### 9. 常量与枚举

| 类 | 说明 |
|---|------|
| `SmartRedisLimiterConstant` | 统一常量类，包含配置前缀、来源标识、fallback reason、Redis mode、Redis Route 类名、HTTP 头、Key 模板等 |
| `SmartRedisLimiterRedisKeyConstant` | Redis Key 常量类，包含前缀、分隔符、时间单位后缀、滑动窗口标识等 |
| `SmartRedisLimiterKeyStrategy` | Key 生成策略枚举：method / path / path-pattern / ip / key-provider |
| `SmartRedisLimiterMode` | 限流模式枚举：annotation / interceptor / both |
| `SmartRedisLimiterFallbackStrategy` | 降级策略枚举：allow / deny |
| `SmartRedisLimiterContextAttribute` | 上下文属性枚举，包含 route / fallback / datasource 相关属性 |
| `SmartRedisLimiterHttpMethod` | HTTP 方法枚举：GET / POST / PUT / DELETE 等 |
| `SmartRedisLimiterTimeUnit` | 动态策略时间单位：SECONDS / MINUTES / HOURS / DAYS |
| `SmartRedisLimiterManagementOperation` | 动态策略管理操作：CREATE / UPDATE / ENABLE / DISABLE / DELETE |
| `SmartRedisLimiterAttributeSnapshotHelper` | 扩展属性受控递归不可变快照 Helper |

`DEFAULT_EXCLUDE_PATTERNS` 公开数组为保持历史二进制兼容继续保留并标记为 `@Deprecated`；新代码使用不可变的 `DEFAULT_EXCLUDE_PATTERN_LIST`。

## 依赖说明

本模块仅以 `compileOnly` 依赖 `spring-context` 和 `jackson-annotations`，不传递 Jackson databind，不依赖 Redis Route，也不包含 Spring Boot 自动配置。

## 使用方式

通常情况下，不需要直接依赖本模块，而是使用限流 starter：

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-starter:2.0.0'
```

如果需要自定义 Provider 或直接使用核心事件模型，可以直接依赖本模块：

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-core:2.1.0'
```

## 架构位置

```text
smart-redis-limiter-core               ← Event、Payload、Record、Provider、Exception、常量与枚举
        ↑
        └── smart-redis-limiter-starter                     (发布事件)
```

## 测试

core 2.1.0 覆盖以下单元测试：

- `SmartRedisLimiterEventPayloadTest`：执行事件载荷、递归 attributes 快照、未知属性类型；
- `SmartRedisLimiterEventTest`：事件 getter 委托、原始 source、空 payload 标准错误；
- `SmartRedisLimiterCoreContractTest`：常量、错误码、不可变默认集合、Record 校验矩阵；
- `SmartRedisLimiterPolicyValidationTest`：字段边界、控制字符和敏感值不回显；
- `SmartRedisLimiterPolicyModelTest`：标准化窗口 equality、策略排序/去重、快照校验；
- `SmartRedisLimiterPolicyJsonTest`：快照和管理事件 JSON fixture、必填字段、round-trip；
- `SmartRedisLimiterManagementEventTest`：五种操作状态矩阵和事件来源；
- `SmartRedisLimiterCompatibilityTest`：2.0.0 构造器和 source 方法描述符。

最终验证使用 Spring Boot 2.7.9、Gradle 8.5、Java 11 toolchain 执行 `clean + test`，未过滤测试，结果为 `BUILD SUCCESSFUL`。

## 版本历史

### 2.1.0

- 新增动态策略 Key、时间单位、限额、完整策略与服务级快照模型。
- 新增动态策略管理操作和 Spring Event 契约。
- Event / Payload / Record 新增 resourceCode / policySource / policyRevision。
- 新增动态策略校验错误码、错误消息、常量和校验 Helper。
- 使用显式 Jackson creator/property 注解固定 Java 8 JSON 契约。
- 按标准化秒数统一窗口去重和 equals/hashCode 语义。
- 执行事件和管理事件 attributes 使用受控递归不可变快照。
- Record 提供 local / remote 最终策略上下文校验。
- 保留 2.0.0 Event / Payload / Record 构造器和 getter 兼容。

### 2.0.0

- Redis Route 原生化配套的 core 契约升级。
- Event / Record / ContextAttribute 新增 route / fallback 字段。
- 引入不可变 EventPayload，并保留旧 Event 构造器兼容桥。

### 1.x

1.x 已封版，历史说明见 [README.1.x.md](README.1.x.md)。
