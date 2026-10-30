# simple-license-client-core

License 客户端契约层：为需要向 License Server 发起在线判定、额度占用与信任资料同步的业务运行端提供纯契约与严格校验。License 线提供客户端，消费方只引入并配置认证传输。

## 解决什么、谁该接

- 你在 Java 8+ 上需要以编程接口消费 License Server 的 `/api/license/v1` 公开契约（在线判定、候选授权发现、并发预占/续占/释放、累计扣减、信任资料同步）。
- 传输与认证不在本模块：HTTP 装配与 AKSK 认证由传输 starter 提供（`simple-license-aksk-resttemplate-client-starter` / `simple-license-aksk-feign-client-starter`，jakarta 线另有同构变体），或你自行实现 `LicenseServerClient`。
- 引本模块获得 `LicenseServerClient` 接口、请求与结果值对象以及严格响应校验。判定响应核对动作回显，信任资料核对签发方和产品范围；它们不能替代服务端对当前认证对象、资格和三权的检查。

## 依赖坐标

```gradle
implementation 'io.github.sure-zzzzzz:simple-license-client-core:2.0.1'
```

本模块零 Spring、零 HTTP 客户端、零凭据概念、零传递依赖。

## 最小使用

```java
LicenseServerClient client = ...; // 传输 starter 装配或自行实现

// 在线判定（值对象形态；位置参数便捷形态同签名并存）
AuthorizationRequest request = AuthorizationRequest.of("product-1", "grant-1", "PERSON",
        "resource.read", facts);
ClientDecision decision = client.authorize(request);
if (!decision.isAllowed()) {
    // decision.getReasonCategory() 给出拒绝类别（DENY 不是异常）
}

// 并发预占：产品、动作、真实资格与预期授权修订都必须明确提供
ClientReservation reservation = client.reserve(new QuotaRequest(
        "product-1", "grant-1", "SERVICE", "session.open",
        "sessions", 2L, "operation-42", 7L, "assignment-1", null));
if (!reservation.isActive() || Instant.now().getEpochSecond() >= reservation.getValidUntil()) {
    throw new IllegalStateException("预占已终止或到期，禁止继续业务动作");
}
// 动作判定关联本次占用编号与稳定业务操作编号，禁止复用其他操作的扣减结果
ClientDecision quotaDecision = client.authorize(AuthorizationRequest.assigned(
        "product-1", "grant-1", "SERVICE", "session.open", "assignment-1", facts)
        .withBusinessOperationId("operation-42")
        .withReservationReferences(Collections.singletonList(reservation.getReservationId())));
if (!quotaDecision.isAllowed()) {
    throw new IllegalStateException("当前动作未获许可");
}
// 持续占用需要按回执围栏续期；只有获得新的有效回执后才继续使用
ClientReservation renewed = client.renewReservation(reservation.getReservationId(), reservation.getFence());
client.release(reservation.getReservationId()); // 累计扣减不可释放

// 信任资料同步（唯一允许调用方维护 last-known-good 的调用）
TrustMaterialSnapshot snapshot = client.fetchTrustMaterial("issuer-1", "product-1", knownSequence);

// 部署候选必须携带激活；显式分页保留所有分页元数据
GrantSelectionPage page = client.discoverGrants(new GrantSelectionRequest(
        "product-1", "DEPLOYMENT", "resource.read", "activation-1", 1, 20));
// 人员/服务旧入口会自动获取全部页，不把第一页冒充完整候选
List<GrantCandidate> candidates = client.discoverGrants("product-1", "SERVICE", "resource.read");
```

`knownSequence` 是客户端已安装的吊销资料序号，用于保留后续增量同步的兼容入口；当前 Server 会始终返回最新的完整快照，调用方仍须验签并按序号只增安装。

## 语义边界

- 额度请求必须携带产品、授权、对象种类、动作、指标、数量、业务操作编号和预期授权修订。部署携带激活编号，服务携带固定资格编号；四参数额度便捷入口缺少这些上下文，在官方传输发起 HTTP 前抛出异常，调用方应迁移到完整 `QuotaRequest`。
- `ClientReservation` 完整保留占用编号、授权、指标、数量、种类、动作、授权修订、操作编号、围栏、截止和状态。创建回执反校请求全部关联字段，续期反校占用编号、并发种类、ACTIVE 状态和不回退围栏；字段缺失、未知字段、未知枚举或错误数值整份拒绝。
- 幂等重放可返回 `RELEASED`/`EXPIRED`，状态原样保留且 `isActive()` 返回 false；ACTIVE 也只表示回执状态，业务仍须核对截止并取得关联判定。旧五参数回执构造方法保留兼容，但缺状态时绝不判断为 ACTIVE。
- 候选发现完整入口使用 `GrantSelectionRequest` 与 `GrantSelectionPage`，返回条目、总量、页码、页大小和总页数。部署必填激活编号；旧三参数入口只适用于人员/服务，自动获取全部页，缺激活的部署请求在发出 HTTP 前拒绝。分页元数据错误、坏枚举、重复编号或翻页期间总量变化整份拒绝；发现资料仍不是授权或原子快照。
- 永久 TOTAL 额度回执的 `validUntil` 仅允许精确整数 `Long.MAX_VALUE` 表达无期限；这是服务端契约定义的唯一超安全整数例外。其他超安全整数、小数、科学记数浮点或字符串形式均拒绝。
- `AuthorizationRequest.withReservationReferences` 保留资格或激活绑定；消费动作另用 `withBusinessOperationId` 携带稳定操作编号。服务端再次核对占用记录的真实对象、动作和操作号。
- 官方传输不提供 HYBRID 租约任务、轮询和签名内容下载。保留的 `leaseRenew(grantId, subjectKind)` 便捷签名因无法提供完整绑定与修订，直接抛出 `UnsupportedOperationException`，不会发送不完整请求。
- 当前请求值对象没有人员代理证明与设备证明字段；需要这些证明的场景应由宿主完整实现 `LicenseServerClient`，不能用裸人员或设备编号代替。

- 判定类调用（authorize/leaseRenew/reserve/release/consume）失败即抛异常且业务不执行：认证失败、网络故障、判定响应动作与请求不一致都整份拒绝，不冒充 DENY、无本地旧结论兜底。
- 官方传输件共享 `ClientResponseValidator.parseDecision`：`allowed` 必须为布尔，模型版本为非负整数，阶段与原因分类只接受已知值。合法拒绝允许 `commercialPhase: null`，表示尚无可报告的商业阶段；允许结论必须有阶段与 `ENTITLED` 原因。缺字段、错误类型和未知协议字段仍是异常。
- `modelVersion` 来自本次实际求值的模型，无模型或尚未进入求值时为 `0`，不等于商业修订。`recheckAt` 描述已知失效截止，不是允许调用方跳过下一次在线判定的缓存许可；无截止时为 `null`。
- 仅 `fetchTrustMaterial` 允许调用方保留最近有效快照：网络失败时继续用旧资料并等待下次同步，这是设计上唯一的降级点。
- 信任资料响应必须严格只包含 `issuerId`、`productId`、`sequence`、`issuedAt`、`snapshotId`、`manifestJws`、`revocationPartJws`、`trustedPublicKeysJson` 八个字段，并回显请求的 `issuerId` 和 `productId`；吊销分片数量为 `1..256`（空吊销名单也必须有一个空分片），代次是正安全整数、签发时间为非负 epoch 秒，禁止把小数或数字字符串转换为整数。安装器仍须独立验签、批准公钥并原子安装。
- 值对象防御复制：`AuthorizationRequest.getFacts()` 递归复制嵌套 Map/List，`TrustMaterialSnapshot` 的分片列表都是只读视图，外部改不动内部状态。
- 本模块不做协议版本协商与令牌管理；令牌由传输层（AKSK 客户端生态）注入。

## 聚散形态

引传输 starter = 聚（消费远端 License Server）；只引本模块自行实现传输、或一件不引 = 散（产品自管授权）。聚散由依赖表达，配置开关不构成假散。
