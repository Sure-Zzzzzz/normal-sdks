# SmartRedisLimiter Core

[![Version](https://img.shields.io/badge/version-2.2.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

`smart-redis-limiter-core` 是限流体系的纯中立契约层，定义限流事件、审计记录模型、动态策略协议（v1 三元组与 v2 类型化）、扩展接口、错误码和统一常量。无 Redis、无 HTTP、无身份系统实现。

## 版本定位

`2.2.0` 在 2.1.0 基础上新增类型化限流契约（v2）：计数维度、规则选择器、服务控制模式三枚举，类型化规则键/规则/快照模型，计数桶身份摘要与操作人短摘要 Helper，以及类型化管理事件。v1 契约零改动，为运行端 2.2.0/1.1.0、management client 线与 Management 2.0.0 提供公共协议。

`2.1.0` 提供动态策略、快照和管理操作事件协议；`2.0.0` 完成 Redis Route 公共事件契约升级。1.x 已封版，历史文档见 [README.1.x.md](README.1.x.md)。

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

### 5. 类型化策略协议（2.2.0 新增）

v2 契约面向多维门禁形态：一条类型化规则按 `serviceCode + resourceCode + dimension + selector + namespace + customType + objectId` 定位，携带完整 limits 集合。

```java
SmartRedisLimiterTypedPolicyKey key = new SmartRedisLimiterTypedPolicyKey(
        "test-service", "test-resource",
        SmartRedisLimiterDataDimension.CUSTOMER, SmartRedisLimiterRuleSelector.EXACT,
        "customer-root", null, "cust-01");
SmartRedisLimiterTypedPolicy rule = new SmartRedisLimiterTypedPolicy(key, true,
        Collections.singletonList(new SmartRedisLimiterLimit(100L, 1L, SmartRedisLimiterTimeUnit.MINUTES)));
SmartRedisLimiterTypedPolicySnapshot snapshot = new SmartRedisLimiterTypedPolicySnapshot(
        SmartRedisLimiterConstant.TYPED_POLICY_SCHEMA_VERSION,
        "test-service", 1L, 5L, Instant.now(), Collections.singletonList(rule));
```

组合约束在构造器强制执行：RESOURCE 维度只允许 DEFAULT；DEFAULT 的 objectId 必须为 null；CUSTOM 必须携带 customType 而其他维度必须为 null；EXACT 对象保留原值（不 trim、不转大小写、不做 Unicode 归一化，最多 256 码点）；窗口 1 至 16 个且单位规范化后不重复。快照携带 `policyEpoch`（策略代次，不小于 1）与单调 revision；规则键唯一、服务编码必须匹配，非法整份拒绝；协议版本固定为 `"2"`，与 v1 快照互不解析（可空字段 customType/objectId 序列化时省略）。

三个枚举定义协议取值：`SmartRedisLimiterDataDimension`（RESOURCE / IP / USER / SERVICE / CREDENTIAL / **CUSTOMER** / CUSTOM，其中 CUSTOMER 是与 License 同源消费客户绑定事实的商业配额维度）、`SmartRedisLimiterRuleSelector`（DEFAULT / EXACT）、`SmartRedisLimiterServiceControlMode`（LEGACY_V1 / TYPED_V2）。

两个 Helper 提供中立计算：

| Helper | 职责 |
|---|---|
| `SmartRedisLimiterBucketIdentityHelper` | 计数桶身份摘要（六字段长度前缀+原始字符单元的 SHA-256，`typed-v2` 业务类型；RESOURCE 用固定共享对象；ruleId/revision/限额值不进桶身份）；操作人短摘要（`resource:v1:sha256:` 83 字符） |
| `SmartRedisLimiterTypedPolicyValidationHelper` | 类型化字段与组合校验（维度/选择器/命名空间/customType/objectId/窗口集合） |

### 6. 管理操作事件

`SmartRedisLimiterManagementEvent` 表达 CREATE / UPDATE / ENABLE / DISABLE / DELETE 的前后策略、启用状态、revision 和操作人。core 只定义 Spring Event，不依赖审计 listener 或持久化实现。

`SmartRedisLimiterTypedManagementEvent`（2.2.0）表达类型化管理事件：操作、服务、资源、维度、选择器、规则标识、revision、结果（SUCCESS / FAILURE）与受控原因；计数对象只记录命名空间受控摘要，不输出原始用户、客户、IP 或自定义 key；操作人完整身份事实由 attributes 的 `operatorIdentity` 保留键携带（schema=`resource-principal-v1`）。

管理事件载荷会校验前后策略 Key 和操作状态矩阵：

| 操作 | 合法状态 |
|---|---|
| CREATE | 仅包含 after policy/state |
| UPDATE | 同时包含前后 policy/state，启用状态不变 |
| ENABLE | 策略不变，状态从 false 变为 true |
| DISABLE | 策略不变，状态从 true 变为 false |
| DELETE | 仅包含 before policy/state |

管理事件的 operation、policyKey、revision、operator、occurredAt 均为 JSON 必填字段；attributes 使用与执行事件一致的严格递归不可变快照。

### 7. 执行策略可观测字段

Event/Payload/Record 新增：

- `resourceCode`
- `policySource`：local / remote
- `policyRevision`

上下文规则：

- local：`policyRevision` 必须为空，`resourceCode` 可为空；
- remote：`resourceCode` 必填，`policyRevision` 必填且不能小于 0；
- 旧构造器默认使用 local，不影响 2.0.0 调用方。

执行事件 payload 为空时使用标准错误码 `VALIDATION_013`；策略上下文非法时使用 `VALIDATION_011`。

### 8. Provider 接口

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

### 9. 错误码和错误消息

core 使用标准错误码和错误消息类；2.1.0 追加动态策略校验契约，2.2.0 追加类型化契约（VALIDATION_014 至 VALIDATION_019）：

| 类 | 说明 |
|----|------|
| `ErrorCode` | 标准错误码 |
| `ErrorMessage` | 标准错误消息 |

旧 `SmartRedisLimiterConstant.ERROR_CODE_*` / `MSG_*` 常量保留为 `@Deprecated` 兼容别名。

### 10. 常量与枚举

| 类 | 说明 |
|---|------|
| `SmartRedisLimiterConstant` | 统一常量类，包含配置前缀、来源标识、fallback reason、Redis mode、Redis Route 类名、HTTP 头、Key 模板、类型化协议版本与字段常量等 |
| `SmartRedisLimiterRedisKeyConstant` | Redis Key 常量类，包含前缀、分隔符、时间单位后缀、滑动窗口标识等 |
| `SmartRedisLimiterKeyStrategy` | Key 生成策略枚举：method / path / path-pattern / ip / key-provider |
| `SmartRedisLimiterMode` | 限流模式枚举：annotation / interceptor / both |
| `SmartRedisLimiterFallbackStrategy` | 降级策略枚举：allow / deny |
| `SmartRedisLimiterContextAttribute` | 上下文属性枚举，包含 route / fallback / datasource 相关属性 |
| `SmartRedisLimiterHttpMethod` | HTTP 方法枚举：GET / POST / PUT / DELETE 等 |
| `SmartRedisLimiterTimeUnit` | 动态策略时间单位：SECONDS / MINUTES / HOURS / DAYS |
| `SmartRedisLimiterManagementOperation` | 动态策略管理操作：CREATE / UPDATE / ENABLE / DISABLE / DELETE |
| `SmartRedisLimiterDataDimension` | 类型化计数维度（2.2.0）：RESOURCE / IP / USER / SERVICE / CREDENTIAL / CUSTOMER / CUSTOM |
| `SmartRedisLimiterRuleSelector` | 类型化规则选择器（2.2.0）：DEFAULT / EXACT |
| `SmartRedisLimiterServiceControlMode` | 服务策略控制模式（2.2.0）：LEGACY_V1 / TYPED_V2 |
| `SmartRedisLimiterAttributeSnapshotHelper` | 扩展属性受控递归不可变快照 Helper |
| `SmartRedisLimiterTypedPolicyValidationHelper` | 类型化字段与组合校验 Helper（2.2.0） |
| `SmartRedisLimiterBucketIdentityHelper` | 计数桶身份与操作人摘要 Helper（2.2.0） |

`DEFAULT_EXCLUDE_PATTERNS` 公开数组为保持历史二进制兼容继续保留并标记为 `@Deprecated`；新代码使用不可变的 `DEFAULT_EXCLUDE_PATTERN_LIST`。

## 依赖说明

本模块仅以 `compileOnly` 依赖 `spring-context` 和 `jackson-annotations`，不传递 Jackson databind，不依赖 Redis Route，也不包含 Spring Boot 自动配置。

## 使用方式

通常情况下，不需要直接依赖本模块，而是使用限流 starter 或策略客户端：

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-starter:2.2.0'
// 或 jakarta 线
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-jakarta-starter:1.1.0'
```

如果需要自定义 Provider、直接使用核心事件模型或消费类型化契约（如自建快照消费方），可以直接依赖本模块：

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-core:2.2.0'
```

## 架构位置

```text
smart-redis-limiter-core               ← Event、Payload、Record、Provider、Exception、常量与枚举、v1/v2 策略契约
        ↑
        ├── smart-redis-limiter-starter                              (javax 运行端，发布事件)
        ├── smart-redis-limiter-jakarta-starter                      (jakarta 运行端，发布事件)
        ├── smart-redis-limiter-management-client-core               (策略客户端契约层)
        └── smart-redis-limiter-management-starter                   (策略管理与快照服务端)
```

## 测试

core 2.2.0 覆盖以下单元测试：

- `SmartRedisLimiterEventPayloadTest`：执行事件载荷、递归 attributes 快照、未知属性类型；
- `SmartRedisLimiterEventTest`：事件 getter 委托、原始 source、空 payload 标准错误；
- `SmartRedisLimiterCoreContractTest`：常量、错误码、不可变默认集合、Record 校验矩阵；
- `SmartRedisLimiterPolicyValidationTest`：字段边界、控制字符和敏感值不回显；
- `SmartRedisLimiterPolicyModelTest`：标准化窗口 equality、策略排序/去重、快照校验；
- `SmartRedisLimiterPolicyJsonTest`：快照和管理事件 JSON fixture、必填字段、round-trip；
- `SmartRedisLimiterManagementEventTest`：五种操作状态矩阵和事件来源；
- `SmartRedisLimiterCompatibilityTest`：2.0.0 构造器和 source 方法描述符；
- `SmartRedisLimiterTypedPolicyModelTest`：类型化键组合约束、码点上限与原值保留、窗口数量与单位唯一、快照严格性、类型化事件载荷；
- `SmartRedisLimiterBucketIdentityTest`：桶身份摘要确定性与区分度、未配对代理与空白保留、RESOURCE 共桶、操作人 83 字符摘要。

最终验证使用 Gradle 8.5 对本模块执行完整 `test`（`--rerun-tasks` 强制重跑，未过滤），40 用例全部通过。

## 版本历史

### 2.2.0

- 新增类型化限流契约（v2 增强第一批）：计数维度（资源/IP/主体/凭据/客户/自定义）、规则选择器（DEFAULT/EXACT）、服务控制模式（LEGACY_V1/TYPED_V2）三个枚举。
- 新增类型化规则键、规则与服务级快照：协议 "2"、policyEpoch 代次、单调 revision；EXACT 对象保留原值（不 trim/不归一，≤256 码点），CUSTOM 必带 customType，RESOURCE 仅 DEFAULT；快照规则键唯一、服务匹配，非法整份拒绝。
- 新增计数桶身份 Helper：六字段长度前缀+原始字符单元的 SHA-256 摘要；ruleId/revision/限额值不进桶身份，改额度不重置计数；未配对代理字符不合并。
- 新增操作人摘要 Helper 与 `operatorIdentity` 事件保留键：`resource:v1:sha256:` 83 字符短摘要，仅作稳定标识不用于授权。
- 新增类型化管理事件与载荷：计数对象只带受控摘要，不输出原始用户/客户/IP/自定义 key。
- v1 契约零改动；本版本仍是纯中立契约模块，无 Redis/HTTP/身份实现，运行端与 Management 的消费版本发布前不视为能力交付。

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
