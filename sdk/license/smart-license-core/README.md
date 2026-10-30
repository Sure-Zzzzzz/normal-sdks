# smart-license-core

License 协议 2 的 Java 8 内核，提供签名文件编解码、类型化权益模型、结构化条件求值、可信公钥与状态端口，以及离线凭据和混合租约的验证流程。Server、消费客户端与离线验证装配共用这份契约。

适用于签发服务、需要离线验证的产品，以及实现 License 协作适配的宿主。Core 不提供 HTTP、数据库、身份认证、私钥存储或远端传输；不直接依赖 IAM、CRM、KMS 或 Spring。

## 引入依赖

```gradle
implementation 'io.github.sure-zzzzzz:smart-license-core:2.0.0'
```

公开编解码面使用 Jackson 2.18.6，并由 api 依赖传递。宿主的依赖管理不能将实际运行版本降至缺少严格深度约束的旧版；Core 使用自己维护的 ObjectMapper，不消费宿主的 JSON 配置。

Core 无 Spring 配置项。协议预算是固定支持边界，不能用启动参数放宽。

## 最小使用：结构化条件

权益是授权内固化的值，事实是本次动作由宿主采集的输入。下面的动作只在请求数量不超过授权容量时允许：

```java
RuleModel model = RuleModel.builder()
        .right(FieldDefinition.builder("capacity", FieldType.INTEGER)
                .required().range(0, 10000).unit("item").build())
        .fact(FieldDefinition.builder("requested", FieldType.INTEGER)
                .range(0, 10000).unit("item")
                .source(FieldDefinition.FactSource.PRODUCT).build())
        .action("use", ConditionNode.compare(CompareOperator.LTE,
                Operand.ref(Operand.Namespace.FACTS, "requested"),
                Operand.ref(Operand.Namespace.RIGHTS, "capacity")))
        .build();
RuleModelValidator.validate(model);

Map<String, Object> rights = Collections.<String, Object>singletonMap("capacity", 100L);
Map<String, Object> facts = Collections.<String, Object>singletonMap("requested", 20L);
FactValues values = FactValues.of(model, rights, facts, true);
boolean allowed = ConditionEvaluator.evaluate(model, "use", values);
```

示例类型位于 `io.github.surezzzzzz.sdk.license.core.model`；Map、Collections 来自 java.util。这个结果只代表条件成立，不证明签名、目标身份、商业期限、资格或额度预留有效。业务授权必须在基础验证后执行，不能直接把此示例当完整 License 判定。

## 完整验证接入

离线验证需要五个端口及受控安装上下文：

| 输入 | 解释 |
| --- | --- |
| TrustStore | 已信任公钥，按 issuer/product/kid/用途选钥；信任版本用于防止旧资料覆盖 |
| RevocationStore | 不可撤回吊销与资料新鲜度；文件要求 REQUIRED 时没有新鲜资料必须拒绝 |
| ClockProvider | 有效时间及时间异常判断；不通过清状态规避回拨检查 |
| StateStore | 保存最大可信时间，提供否定水位与恢复代数的存储契约；跨文件更新不能重置 |
| VerificationContext | 独立登记的签发方、产品、客户、授权和预期对象绑定，不由待验文件自报 |
| FileVerificationInputs | 宿主完成真实设备核验并返回固定 profile 对应 SubjectRef 的结论；人员/服务不能只返回布尔通过 |
| TrustedSubjectProvider | 宿主提供当前真实人员/服务、profile 版本和证明有效截止；Verifier 每次 authorize 重核，缺失或过期即拒绝 |
| TrustedFacts | 本次由可信适配采集的事实；字段 source 只是类别声明，不证明输入可信 |

```java
// trustStore 等端口由消费宿主提供；受控安装的公钥与目标绑定不来自待验文件。
DefaultLicenseVerifier verifier =
        new DefaultLicenseVerifier(trustStore, revocationStore, clockProvider, stateStore);
VerifiedCredential handle = verifier.verify(credentialBytes, verificationContext);
Decision decision = verifier.authorize(handle, "use", bindingEvidence, trustedFacts);
```

DefaultLicenseVerifier、VerificationContext、TrustedFacts 位于 `core.verify`，VerifiedCredential、Decision 位于 `core.model`。输入文件是 compact JWS 的 UTF-8 字节。生产宿主必须提供真实绑定证据及新鲜度策略要求的资料，不能用常量 true 或空仓冒充核验。

TrustStore 安装的是 `TrustedPublicKey`：签发方、产品、业务 kid、用途、单调信任版本与 SPKI 公钥。安装来自受控渠道或既有可信链；待验文件及其内嵌公钥不能自授权。凭据签名、吊销、状态恢复三个用途相互隔离；新 kid 不可重绑旧钥。

## 类型、规则与期限

| 类型 | Java 值 | 约束 |
| --- | --- | --- |
| INTEGER | Integer/Long，归一为 Long | 显式 minimum/maximum；小数用约定单位的缩放整数，不接受浮点替代 |
| BOOLEAN | Boolean | false 是合法值，不等于缺失 |
| STRING | String | 遵守长度与枚举约束；空文本按声明处理 |
| STRING_SET | List&lt;String&gt; | 遵守成员、长度及数量约束；空集合不等于缺失 |

`license-condition/1` 支持 all、any、not、bool、compare、member 六种节点。未声明动作、引用字段缺失、类型不符或预算超限都必须拒绝；缺失不是 false，不能被 not 翻成允许。发布前调用 RuleModelValidator 完成引用、类型、单位与预算检查。

ValidityPolicy 区分固定期限与永久期限；永久凭据省略 exp。商业宽限与混合租约断网窗口分别限制动作，实际允许动作取条件、商业阶段与连接阶段的交集，宽限不绕过签名、吊销或目标绑定。

DefaultLicenseVerifier 的文件判定边界如下。基础 `verify` 成功只说明签名、对象及基础政策有效，动作仍须通过 `authorize`：

| 文件政策 | 动作判定行为 |
| --- | --- |
| 无额度约束 | 每次复查信任、吊销、可信时间、真实主体/设备证据及结构化条件 |
| NAMED_SEAT | 需要与固定资格编号、代数及指标对应的签名实名引用；缺少引用或保留截止失效时拒绝 |
| HYBRID 的 ONLINE CONCURRENT | 需要签入真实并发占用编号、数量、围栏及有效截止，租约最终截止不能超出占用截止 |
| LOCAL 或 ONLINE CONSUMPTION 动作 | 此入口没有业务操作编号及原子扣减事务，返回 DENY/CAPABILITY_MISSING；部署额度分配只表示上限，不能作为消费成功证明 |
| 独立维护截止 | 此入口没有类型化维护动作白名单，返回 DENY/CAPABILITY_MISSING，不把维护截止当作全部动作持续可用 |
| 组织成员政策 required=true | 文件缺少可核验的成员观察及成员截止结构时，报 CAPABILITY_UNSUPPORTED，不用主体核验布尔值替代组织资格 |

额度定义、动作约束、固定窗口锚及部署分配会在载荷装载时校验；数量和版本必须为 JSON 安全整数，浮点截断、未知指标、错修订及缺少分配均拒绝。载荷深复制并冻结嵌套对象，调用方不能在校验后通过原集合或 getter 改写权益与额度。

`QuotaLedger` 定义宿主原子预占/扣减端口，业务提交必须关联稳定操作编号；接口的存在不表示 DefaultLicenseVerifier 已自动调用它。接入本地额度时应独立完成真实台账与业务事务，不能把上述明确拒绝改成常量成功。

## 协议约束与预算

| 项目 | 支持边界 |
| --- | --- |
| 文件格式 | 三段 Compact JWS；Base64URL 无 padding |
| 签名 | ES256，固定 64 字节 JOSE R||S，不接受 ASN.1 DER 签名 |
| protected header | 恰含 alg、kid、typ；alg 固定 ES256 |
| 文件用途 typ | license+jwt、license-lease+jwt、license-revocations+jwt、license-revocation-manifest+jwt、license-state-recovery+jwt |
| 版本 | 协议 2 与 license-condition/1；不自动接受旧协议或降级 |
| 时间 | UTC Epoch 秒，不能传毫秒；截止边界为左闭右开 |
| 文件预算 | header 4096 字节、payload 524288 字节、整文件 710000 字节 |
| JSON | 嵌套深度最多 64，拒绝未知字段、重复键、数字字符串及标量到文本的隐式转换 |
| 规则 | 每动作 256 节点、深度 16、全模型 4096 节点；单次求值预算 4096 单位 |

typ 的定义不表示所有用途都由 DefaultLicenseVerifier 验证；该入口接受离线凭据及混合租约，其他用途由对应流程消费。Core 的结构或预算校验异常不能转换成成功文件或省略部分内容。

## 协作 SPI 的归属

SPI 是调用方可实现的业务接口。以下接口统一位于 `io.github.surezzzzzz.sdk.license.core.spi`，宿主实现接口只需依赖 Core 契约，不需要编译依赖 Server 内部实现：

| 接口 | License 所需能力 | 实现约束 |
| --- | --- | --- |
| LicenseSigner | 按签名档案对不可变原文签名 | 返回64字节JOSE签名；使用精确钥版本并核回显，签名路径不现场换公钥 |
| KeyMaterialPort | 登记时读取完整公钥资料 | 返回引用、版本、算法、逻辑钥/版本状态及SPKI；不能只验证字节长度 |
| IamDirectoryPort | 核验主体在客户授权根内 | 核验完整启用祖先链；不可达或无法确定时拒绝，不共用IAM表 |
| CrmFulfillmentPort | 游标拉取、点查及履约认领/结果回写 | 使用License自有值类型；保留cursor与序号水位，不暴露CRM DTO |
| LimiterPolicyPort | 将授权中的客户 OpenAPI 限频快照投影到 limiter Management，并在暂停、吊销或到期时停用 | 只依赖 limiter 的稳定管理契约；以 License 操作编号幂等；远端返回的资源键、namespace、版本和启用状态必须完整核验 |
| OnlineDecisionClient | 消费在线判定 | 远端传输由客户端装配件提供，不在Core复写HTTP |

这些目录、履约、钥材料、限频和在线判定接口没有旧 Server 包名的兼容别名，宿主 import 应指向 core.spi。实现由宿主组合官方客户端；Core 不提供认证、传输或成功替身。

## 最佳实践

- 签发前固化模型、权益、商业期限与精确签名档案。重试签名消费原冻结输入，不能按重试时间延长授权或切换 kid。
- 可信公钥独立交付；保留 RETIRED 档案与旧 kid 信任资料。机器凭据轮换不等于撤回历史 License。
- 完成文件 verify 后，对每次业务动作执行 authorize，不永久缓存 ALLOW。运行事实从可信采集端口取得，不信任请求自报身份或设备匹配结果。
- 验证端状态目录、吊销资料及信任资料是自己的持久事实；软件备份恢复不能造成否定水位回退。防整盘回滚需要额外可信锚点。
- 业务聚合、事务、额度、幂等及协作结果仍归各业务域。聚形态引官方传输件调用远端；不引传输件的离线验证端不连接 IAM、CRM 或 KMS。
- 日志只记录安全关联编号及错误分类，不输出令牌、私钥、完整签名文件、权益明文或请求正文。

## 模块边界

Server 提供商业授权台账、HTTP 和持久化；Verifier Starter 提供可替换的本地验证端口装配；在线客户端提供认证传输。它们共享 Core 类型，不共享数据库、不转发浏览器人员令牌，也不把 Core 的单项能力当作整个业务部署已验收。
