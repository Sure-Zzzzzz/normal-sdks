# 1.1.0

类型：功能升级（与 javax 线 2.2.0 同批：类型化多维门禁执行 + 策略客户端拆分，v2 增强第二批；jakarta 线变更对齐 javax CHANGELOG.2.2.0，差异仅在线基线）。

## 变更

- 新增类型化多维门禁执行（默认关闭，`typed.enabled=true` 显式启用）：固定顺序 IP→资源→客户→用户/服务（择一）→凭据→自定义串行独立门禁；任一拒绝即停、先得额度不退还；每门禁必须完整本地限额。
- 新增维度事实提供方 SPI（四态事实：有效/不适用/明确拒绝/不可用；403/503/429 语义分离）。
- 新增门禁声明配置（`typed.gates`：维度+命名空间+customType+完整本地限额；维度查重与组合校验；预期策略代次门禁）。
- 新增类型化远程策略刷新链（独立于 v1：只拉 v2、只写类型化存储、last-known-good 保留、代次不符按拉取失败处理）。
- 策略客户端拆分：内嵌 `policy/client` 与 `policy/json` 迁出至 `smart-redis-limiter-management-client-core`（契约）与 jakarta 传输件 `smart-redis-limiter-management-aksk-resttemplate-client-jakarta-starter`（认证传输，Boot3 基线、hc5 连接池）；本模块 api 依赖 client-core。
- 远程策略（v1）开启但类路径无 client 制品时启动响亮失败；散形态关闭 remote-policy 仅用本地限额。
- Redis 故障按 typed.redis-degradation 显式处理（默认 deny=503 语义）。

## 升级注意

- 1.0.0 使用远程策略的宿主：升级 1.1.0 需补引 jakarta 传输件（按其 README 配置端点与 aksk 底座 jakarta 线），或关闭 remote-policy 改用本地限额。
- Spring Boot 3 / Java 17 基线不变；jakarta 传输件连接池为 httpclient5（javax 线是 httpclient4）。

## 覆盖与兼容性

- 全量 138/138（真实 Redis）；client-core 4/4、jakarta 传输件 5/5、jakarta 审计监听 18/18 同批交付。
- v1 单规则行为不变；类型化默认关闭。
