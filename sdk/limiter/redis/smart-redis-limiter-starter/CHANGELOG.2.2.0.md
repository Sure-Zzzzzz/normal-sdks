# 2.2.0

类型：功能升级（类型化多维门禁执行 + 策略客户端拆分，v2 增强第二批）。

## 变更

- 新增类型化多维门禁执行（默认关闭，`typed.enabled=true` 显式启用）：按固定顺序 IP→资源→客户→用户/服务（择一）→凭据→自定义串行执行独立门禁；任一拒绝即停、先得额度不退还；同桶多窗口原子；每门禁必须提供完整本地限额（无快照/空快照/远程规则缺失执行本地）。
- 新增维度事实提供方 SPI（`SmartRedisLimiterTypedFactProvider`）：四态事实（有效/不适用/明确拒绝/不可用）；明确拒绝按 403、事实不可用按 503、额度耗尽按 429 语义；提供方异常按不可用处理，不猜测匿名身份。
- 新增门禁声明配置（`typed.gates`）：维度+固定命名空间+自定义类型+完整本地限额；维度查重、组合校验（CUSTOM 必带 customType、RESOURCE 仅 DEFAULT）；预期策略代次（`expected-policy-epoch`）不符的快照按无快照处理走本地门禁。
- 新增类型化远程策略刷新链（独立于 v1）：只拉取 v2 快照、只写类型化存储；刷新失败保留 last-known-good；类型化与 v1 的 ETag/schema/已接受状态相互分离。
- 策略客户端拆分（聚散消费形态，对齐 KMS 客户端线）：内嵌 `policy/client` 与 `policy/json` 迁出至 `smart-redis-limiter-management-client-core`（纯契约）与 `smart-redis-limiter-management-aksk-resttemplate-client-starter`（认证传输，复用 AKSK 底座）；本模块 api 依赖 client-core（散形态引它零远程依赖）。
- 远程策略（v1）开启但类路径无 client 制品时启动响亮失败（不再默认内嵌私有客户端）；散形态请关闭 remote-policy 仅用本地限额。
- Redis 故障按 typed.redis-degradation 显式处理：allow 只放过当前门禁并继续（不绕过资格检查）、deny 返回 503 语义不冒充额度耗尽（默认 deny）。

## 升级注意

- 2.1.0 使用远程策略的宿主：升级 2.2.0 需补引 `smart-redis-limiter-management-aksk-resttemplate-client-starter`（并按其 README 配置端点与 AKSK 底座），或将 remote-policy 关闭改用本地限额；原内嵌客户端与固定 policy-token 路径不再由本模块提供。
- 固定 policy-token 语义随内嵌客户端一并迁出；Console 过渡期的固定 token 由 Management 侧旧版本承载。
- 类型化模式与 v1 单规则模式互斥于配置层：typed.enabled=true 时入口统一走类型化计划（入口本地限额与 key 策略仅作透传，不参与门禁）。

## 覆盖与兼容性

- 新增测试：类型化执行（顺序/四态/EXACT-DEFAULT-本地/择一冲突/先得不退还/降级拒绝）、类型化刷新链（代次门禁/last-known-good/304 条件请求）、客户端装配（无制品响亮失败/带桩起链/关 remote 零装配）。
- 本模块全量 139/139（真实 Redis）；client-core 4/4、传输件 5/5、Core 2.2.0 回归 40/40。
- v1 单规则行为不变；类型化默认关闭，不开启则行为与 2.1.0 一致（客户端拆分除外）。
