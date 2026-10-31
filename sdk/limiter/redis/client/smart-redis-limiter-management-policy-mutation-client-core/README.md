# smart-redis-limiter-management-policy-mutation-client-core

管理面类型化策略变更客户端契约层（License 履约联动等写场景）：幂等 upsert、按 ruleId+CAS 变更、
完整策略键精确回读，统一返回 ruleId/rowVersion/revision/enabled。零 Spring、零 HTTP；
传输由 javax/Jakarta AKSK RestTemplate starter 装配。

## 接口面

| 方法 | 语义 | 管理端端点 |
| --- | --- | --- |
| `upsert(command)` / `upsert(command, maxAttempts)` | 幂等写入：键无规则创建，唯一规则内容一致幂等返回（`NO_REVISION`），不一致以现 rowVersion 整体替换；409 冲突重读重试 | 组合回读+POST/PUT/PATCH |
| `findByKey(key)` | 完整策略键（七字段）精确回读；无匹配 null，多命中拒绝（数据异常不盲改） | `GET /v2/policy/rules`（身份过滤） |
| `create` / `updateLimits` / `updateEnabled` / `delete` | 单步变更（CAS expectedRowVersion） | `POST/PUT/PATCH/DELETE /v2/policy/rule` |
| `findByIdentityRow(ruleId)` | 按主键回读现值（冲突重试依赖） | `GET /v2/policy/rule/{id}` |

## 授权矩阵（凭据控制点）

**权限控制完全经 AKP 授权实现，客户端无任何开关**：未授对应码的调用一律 403 失败关闭。

### 本客户端（变更面）AKP 授权清单

| 能力 | API 码 | DATA |
| --- | --- | --- |
| 变更族（create/update/delete/upsert 写段） | `smartLimiterPolicy:write` | `limiter-policy` write，serviceCode IN 限定 |
| 回读族（findByKey/findByIdentityRow，upsert 依赖） | `smartLimiterPolicy:read` | `limiter-policy` read，serviceCode IN 限定 |

- **write+read 双码必须同时授予**：变更（PUT/PATCH/DELETE）依赖 `ruleId + expectedRowVersion`，
  二者只能来自回读；仅 write 时幂等 upsert 与精确回读均不可用（管理端读端点挂 read 码，硬闸）。
- DATA 范围限定履约 serviceCode 集合（IN），不授 all——凭据泄露影响面收敛在履约范围内。

### 与快照拉取客户端（运行端）的授权分离

| 客户端 | 消费方 | AKP 授权 | 配置前缀 |
| --- | --- | --- | --- |
| 快照拉取（`smart-redis-limiter-management-client-core` 族） | 各限流运行端 | `smartLimiterPolicySnapshot:read` + DATA read | `io.github.surezzzzzz.sdk.limiter.management.client` |
| 本客户端（变更面） | License 履约联动 | `smartLimiterPolicy:write` + `read` + DATA write/read | `io.github.surezzzzzz.sdk.limiter.management.policy-mutation-client` |

- 两个客户端**配置前缀互不干扰**，引哪个哪个生效，不引不装配。
- **AKP 按消费方申请，不按制品数**：当前形态下运行端与 License 分属不同进程，各自申请
  一个 AKP 即可（运行端授 snapshot:read，License 授 policy 双码），不存在一进程双 AKP。
- 若未来同一进程既拉快照又变更：一个 AKP 勾全部所需码即可（AKSK 授权模型的 API 码为集合、
  DATA grants 文档可含多动作）；如需按用途审计隔离再拆两个 AKP（前提=底座装配双
  RestTemplate 实例分携凭据，当前形态为单实例共用，不支持时再立项）。

## 编排语义

- 幂等命中判定 = 现值 limits 逐档相等（有序）且 enabled 相同；命中返回 `rowVersion` 现值 +
  `revision = NO_REVISION(-1)`（无新版本）。
- 409（身份重复/行版本过期）重读重试，默认上限 3 次；耗尽以冲突异常浮出，不静默。
- 身份字段（serviceCode/resourceCode/dimension/selector/namespace）必填、limits 1–16 档、
  CUSTOM 维度必带 customType——编排前置校验。
