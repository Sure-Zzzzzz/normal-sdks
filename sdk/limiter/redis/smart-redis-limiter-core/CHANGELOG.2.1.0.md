# smart-redis-limiter-core 2.1.0 Changelog

## 发布信息

- 版本：`2.1.0`
- 发布日期：待发布
- 类型：Feature / 向后兼容契约扩展
- 基线版本：`2.0.0`

## 版本定位

`2.1.0` 在已发布 `2.0.0` 的 Redis Route 公共事件契约基础上，新增动态限流策略公共协议，供 limiter 执行端和 management 管理端共同依赖。

core 仍只负责公共模型、事件、扩展接口、常量和错误契约，不依赖 Redis、JDBC、Web、安全模块或任何 starter。

## 依赖变更

| 依赖 | 作用域 | 说明 |
|---|---|---|
| `org.springframework:spring-context` | `compileOnly` | 保留 Spring ApplicationEvent 契约 |
| `com.fasterxml.jackson.core:jackson-annotations` | `compileOnly` | 固定不可变模型 JSON Creator/Property 协议 |
| `jackson-databind` / `jackson-datatype-jsr310` | `testImplementation` | 仅用于 JSON 契约测试，不向调用方传递 |

未引入 Redis、JDBC、Spring Web、Spring Security 或 Spring Boot 自动配置依赖。

## 主要变更

### 1. 动态策略公共模型

新增：

- `SmartRedisLimiterPolicyKey`
- `SmartRedisLimiterTimeUnit`
- `SmartRedisLimiterLimit`
- `SmartRedisLimiterPolicy`
- `SmartRedisLimiterPolicySnapshot`

公共协议支持：

- 使用 `serviceCode + resourceCode + subject` 三元组进行精确策略匹配；
- 一条策略携带完整 limits，不进行窗口级隐式合并；
- 单条策略最多包含 16 个窗口；
- 服务级快照携带 schemaVersion、serviceCode、revision、publishedAt 和完整 policies；
- 空 policies 是合法完整快照，表示当前服务没有动态覆盖；
- 快照拒绝跨服务策略、重复 PolicyKey 和 null 策略项。

### 2. 标准化窗口语义

时间单位固定为：

- `SECONDS`
- `MINUTES`
- `HOURS`
- `DAYS`

窗口统一换算为 windowSeconds：

- `60 SECONDS` 与 `1 MINUTES` 视为同一窗口；
- Policy 按 windowSeconds 检测重复窗口并确定性排序；
- Limit 的 equals/hashCode 按 `count + windowSeconds` 比较；
- 时间换算使用 `Math.multiplyExact`，溢出转换为标准错误。

### 3. 管理操作事件

新增：

- `SmartRedisLimiterManagementOperation`
- `SmartRedisLimiterManagementEventPayload`
- `SmartRedisLimiterManagementEvent`

支持 CREATE、UPDATE、ENABLE、DISABLE、DELETE 五种操作，并校验：

- CREATE：仅包含变更后的策略和状态；
- UPDATE：包含前后策略，启用状态不变；
- ENABLE：策略不变，状态从 false 变为 true；
- DISABLE：策略不变，状态从 true 变为 false；
- DELETE：仅包含变更前的策略和状态；
- beforePolicy / afterPolicy 的 Key 必须与 payload policyKey 一致。

管理事件使用标准 `ApplicationEvent#getSource()` 发布者语义，不复制执行事件历史上的 `getSource()` 覆盖行为。

### 4. 执行策略可观测字段

`SmartRedisLimiterEventPayload`、`SmartRedisLimiterEvent` 和 `SmartRedisLimiterRecord` 新增：

- `resourceCode`
- `policySource`
- `policyRevision`

上下文规则：

- local：policyRevision 必须为空，resourceCode 可为空；
- remote：resourceCode 必填，policyRevision 必填且不能小于 0；
- 不增加 subject 字段，避免敏感标识和高基数信息扩散。

EventPayload 和 Record 共用策略上下文校验规则。Record 保持可变 DTO 兼容性，并新增 `validatePolicyContext()` 作为最终处理边界校验。

### 5. JSON 协议加固

不可变策略模型和管理事件载荷通过显式 `@JsonCreator` / `@JsonProperty` 固定 Java 8 JSON 构造协议，不依赖 `-parameters` 或 Jackson 参数名推断。

必填字段使用 required creator property；revision 使用包装参数接收并显式校验：

- 缺少 revision 时拒绝反序列化；
- revision 为 null 时拒绝构造；
- revision 小于 0 时拒绝构造；
- 不再将缺失 revision 静默转换为 0。

JSON 字段名统一收口到 `SmartRedisLimiterConstant.JSON_FIELD_*`。

### 6. 扩展属性不可变快照

新增 `SmartRedisLimiterAttributeSnapshotHelper`。执行事件和管理事件的 attributes 在构造时生成受控递归不可变快照。

支持：

- JSON 基础值和 null；
- 枚举、Instant、UUID；
- 键为 String 的 Map；
- List、Set 和数组。

嵌套容器递归复制并包装为不可变对象；数组转换为不可变 List。以下输入会使用 `VALIDATION_012` 拒绝：

- 未知可变对象；
- 非字符串 Map Key；
- 循环引用。

### 7. 错误码与硬编码治理

新增动态策略、管理事件、执行上下文和属性快照错误码，当前新增校验错误范围为：

- `VALIDATION_001`～`VALIDATION_013`

其中：

- `VALIDATION_011`：执行策略上下文非法；
- `VALIDATION_012`：扩展属性值非法；
- `VALIDATION_013`：执行事件载荷非法。

同时完成：

- ErrorCode / ErrorMessage 同名主体配对；
- JSON 字段名集中定义；
- 校验原因集中定义；
- 生产代码不再拼接散落的校验消息；
- 执行事件 payload 为空时进入标准异常体系。

### 8. 默认排除路径安全 API

为保持历史二进制兼容，公开数组 `DEFAULT_EXCLUDE_PATTERNS` 继续保留并标记为 `@Deprecated`。

新增不可变 `DEFAULT_EXCLUDE_PATTERN_LIST`，供新代码安全读取默认排除路径。

## 向后兼容性

- 保留 `SmartRedisLimiterEventPayload` 2.0.0 旧 23 参数构造器；
- 保留 `SmartRedisLimiterEvent` 旧 18 参数构造器；
- 保留 `SmartRedisLimiterEvent` 的 `serialVersionUID=2L`；
- 保留 `SmartRedisLimiterEvent#getSource()` 返回 sourceType 的历史语义；
- 保留 `getRawSource()` 返回原始 publisher；
- 保留 `SmartRedisLimiterRecord` 2.0.0 旧 29 参数构造器；
- 保留 Record 无参构造器、setter/getter 和 builder；
- 旧构造器新增字段默认值为 `resourceCode=null`、`policySource=local`、`policyRevision=null`；
- 不删除或修改 2.0.0 已发布错误码、枚举 code 和历史兼容常量。

## 新增与扩展测试

- 策略字段 null、blank、长度、格式、控制字符和敏感值不回显；
- 四种时间单位、正数边界和时间换算溢出；
- 标准化窗口 equality、等价窗口去重和确定性排序；
- Policy / Snapshot 集合防御性拷贝与不可变包装；
- 快照 schema、service、revision、publishedAt、跨服务和重复 Key；
- 快照 JSON fixture、必填字段、未知时间单位和 round-trip；
- 管理事件 JSON fixture、必填字段、未知 operation 和状态矩阵；
- CREATE / UPDATE / ENABLE / DISABLE / DELETE 前后状态校验；
- attributes 嵌套容器快照、不可修改、未知类型和循环引用治理；
- Event/Payload/Record 动态策略字段和 local / remote 校验；
- Record 最终校验完整矩阵和旧构造器默认行为；
- 反射验证 2.0.0 已发布构造器及 source 方法描述符。

## 验证结果

最终使用以下主环境执行 core 完整验证：

- Spring Boot：`2.7.9`
- Gradle：`8.5`
- Java toolchain：`11`
- 目标兼容：Java 8
- 执行方式：`clean + test`
- 测试过滤：无

结果：

```text
BUILD SUCCESSFUL
4 actionable tasks: 4 executed
```

最终从 2.1.1 维护版本视角进行只读反审，未发现会迫使立即发布 2.1.1 的真实问题。

## 升级指南

普通调用方无需修改。需要直接使用动态策略公共协议时依赖：

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-core:2.1.0'
```

升级后注意：

1. 旧 Event / Payload / Record 构造方式仍可继续使用；
2. 新代码通过 builder 填写 resourceCode、policySource、policyRevision；
3. Record 完成字段组装后，在处理边界调用 `validatePolicyContext()`；
4. attributes 仅传入受支持的 JSON-compatible 类型，不能放入任意可变 POJO；
5. 新代码使用 `DEFAULT_EXCLUDE_PATTERN_LIST`，不要直接读取或修改废弃数组。
