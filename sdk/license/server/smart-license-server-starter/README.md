# smart-license-server-starter

License 商业授权服务，固定 Spring Boot 2.7.9、Java 8。提供签发方、客户、产品、模型、方案、授权、资格、激活、签名档案、凭据交付、吊销与在线业务判定的 HTTP 和持久化实现，协议共用 smart-license-core 的协议 2 与 license-condition/1。

适用于建设授权签发服务，以及将 License 作为独立业务模块接入统一门户的宿主。License 自己拥有商业授权与业务事实，人员与机器身份来自官方资源组件，协作调用由宿主组合官方客户端完成。

## 引入依赖

```gradle
implementation 'io.github.sure-zzzzzz:smart-license-server-starter:2.0.0'
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.boot:spring-boot-starter-validation'
implementation 'io.github.sure-zzzzzz:simple-data-permission-core:1.2.0'
implementation 'io.github.sure-zzzzzz:simple-resource-server-core:1.1.1'

// 人员与机器协作宿主采用整组 javax 资源组件
implementation 'io.github.sure-zzzzzz:simple-resource-server-starter:1.1.1'
implementation 'io.github.sure-zzzzzz:simple-iam-resource-server-starter:1.0.0'
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:3.1.0'
implementation 'org.springframework.security:spring-security-oauth2-resource-server'
```

Server 传递 License Core 与授权契约；宿主补齐 Web/JPA/Validation、Spring MVC DATA 装配、MySQL Route、资源验证与所需客户端。DATA MVC starter 负责在 HTTP 请求中评估并注入 Core 的访问计划，属于宿主装配依赖；License Server 只编译依赖 Core 类型。实际 Jackson 运行版本应满足 Core 的 Jackson 2.18.6 严格解析要求，不能被旧宿主 BOM 降级。

KMS、IAM、CRM 官方客户端及其 AKSK 认证传输按实际协作需求由宿主选择，本模块不复写 HTTP、令牌交换或其他系统的存储实现。

## 最小宿主接入

宿主扫描 License 的实体和仓储，数据源由 MySQL Route 接管：

```java
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableJpaRepositories(basePackages = "io.github.surezzzzzz.sdk.license.server.repository")
@EntityScan(basePackages = "io.github.surezzzzzz.sdk.license.server.entity")
public class LicenseApplication {
    public static void main(String[] args) {
        SpringApplication.run(LicenseApplication.class, args);
    }
}
```

还须注册实现 Core 契约的 LicenseSigner 和 KeyMaterialPort：前者按登记档案签名，后者读取完整公钥材料；其他协作端口按下文接入。公共资源上下文在类路径时，License HTTP 自动配置按自定义组件标记扫描装配，无业务 enabled 开关。缺签名服务或必需仓储不能提供成功替身。

以下为人员管理入口的最小配置，AKSK 入站显式关闭以避免缺少内省凭据时启动失败；它不构成机器调用验收：

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false

io:
  github:
    surezzzzzz:
      sdk:
        mysql:
          route:
            enable: true
            primary-datasource: default
            datasources:
              default:
                url: ${LICENSE_MYSQL_URL}
                username: ${LICENSE_MYSQL_USERNAME}
                password: ${LICENSE_MYSQL_PASSWORD}
                driver-class-name: com.mysql.cj.jdbc.Driver
        auth:
          resource:
            server:
              enabled: true
              security:
                protected-paths: [/api/license/**]
                permit-all-paths: []
          iam:
            resource:
              server:
                enabled: true
                verification-endpoint: ${LICENSE_IAM_VERIFICATION_ENDPOINT}
                client-id: ${LICENSE_IAM_VC_ID}
                client-secret: ${LICENSE_IAM_VC_SECRET}
          aksk:
            resource:
              server:
                enabled: false
        license:
          server:
            stale-signing-after-seconds: ${LICENSE_STALE_SIGNING_AFTER_SECONDS:}
```

初始化用本模块 docs/01_schema.sql；它包含 DROP，仅用于明确可丢弃的新库或测试库。已有数据先备份并做增量迁移，不能依靠 ddl-auto=update 代替历史事实恢复。

## 完整协作配置与说明

需要机器调用时，在上面配置基础上启用官方 AKSK 入站 Provider，并提供内省三项。内省是资源服务向认证提供方核对机器令牌的过程；其凭据与宿主访问 CRM/KMS 的出站机器凭据是两个用途。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            resource:
              server:
                enabled: true
                introspect:
                  endpoint: ${LICENSE_AKSK_INTROSPECT_ENDPOINT}
                  client-id: ${LICENSE_AKSK_INTROSPECT_CLIENT_ID}
                  client-secret: ${LICENSE_AKSK_INTROSPECT_CLIENT_SECRET}
```

| 配置/装配项 | 解释 |
| --- | --- |
| mysql.route.enable、primary-datasource、datasources | 开启官方Route并选择License自己的数据源；禁止共用协作系统事实表 |
| datasource url/username/password/driver | 自有MySQL连接；凭据运行时注入，JDBC时间参数应与部署约定一致 |
| jpa.ddl-auto | 正式库validate核对结构；update不回填旧期限或冻结载荷归属 |
| resource.server.security.protected-paths | 保护全部License API；不要为联调把业务路径加入permit-all |
| iam.resource.server.verification-endpoint | 官方人员令牌验证入口 |
| iam.resource.server.client-id/secret | 绑定License可信应用的服务端验证凭据，不能与浏览器公共客户端混用 |
| aksk.resource.server.enabled | 机器入站开关；启用后必须具备官方内省配置和真实机器授权 |
| aksk.resource.server.introspect.* | 内省地址与验证客户端凭据，不等于出站AKP配置 |
| license.server.stale-signing-after-seconds | SIGNING任务的超时接管窗口（秒）；未配置时关闭接管。启用接管还必须同时提交当前`expectedRowVersion`、`expectedExecutionGeneration`与`reasonCategory`，不能仅凭超时强行重签 |
| license.server.limiter-expiry-scan-delay-millis | 到期授权扫描间隔（毫秒），默认60000；扫描先停用已投影的客户限频策略，再将授权置为SUSPENDED，远端结果未知时保留重试 |
| 官方出站客户端 | 分别配置IAM、CRM、KMS地址及自己的AKP；不转发人员令牌 |
| LicenseSigner、KeyMaterialPort | 签名与登记材料适配，缺失不允许伪造签发成功 |
| IamDirectoryPort | 客户组织准入；目录无法确认时拒绝 |
| CrmFulfillmentPort | 机器履约拉取/回写，缺失时相关同步入口明确失败 |

浏览器 PAGE、菜单、PKCE（授权码校验）及应用准入在 IAM 官方管理面登记。Server 从验证上下文消费 API/DATA，Web 从 /me 读取 PAGE/API，不能按角色名称猜权限。

## 业务主链与接口

基路径为 `/api/license/v1`，精确字段、请求头与响应以公开 [OpenAPI](contract/openapi/simple-license-admin-web.openapi.yaml) 为准。

| 业务 | 主要入口 | 结果语义 |
| --- | --- | --- |
| 当前主体 | GET /me | 当前PAGE/API摘要，不返回原始DATA或令牌 |
| 基础台账 | issuers、customers、products | License自己的签发方、客户和产品，外部引用显式绑定 |
| 商业配置 | models/model-versions、plans/plan-versions | 发布快照与四种权益类型；试算不代替真实身份判定 |
| 授权 | grants、grants/{id}/state | DRAFT/ENABLED/SUSPENDED/REVOKED及持久商业期限；可声明客户 OpenAPI 限频快照，`serviceCode`/`resourceCode` 使用 Limiter StableCode（以字母或数字开头，后续仅字母、数字、点、下划线或连字符，最多128字符）；启用/暂停、整份授权吊销或到期时通过 `LimiterPolicyPort` 投影到 limiter Management |
| 资格与激活 | subject-assignments、activations、replacements | 人员/服务资格和部署对象绑定；部署激活可登记并回读 LOCAL 额度分配快照，撤回不重置历史 |
| 签名档案 | signing-profiles、products/{id}/signing-profiles/{purpose} | 精确钥版本、kid、公钥快照；轮换保留RETIRED历史 |
| 凭据 | grants/{id}/credentials、credentials/{id}/content | 冻结→签名→快照验签→READY→受控交付 |
| 签发任务 | operations、recoveries、abandonments | 版本与执行代数围栏；UNCERTAIN受控恢复，不盲重签 |
| 吊销 | revocations、revocation-packages、products/{id}/trust-material | 不可撤回否定事实，已吊销目标不得交付旧凭据；信任资料接口保留 `knownSequence` 兼容参数，但当前始终返回最新完整快照，调用方验签后按序号只增安装 |
| 在线判定 | POST /decisions | 资格/状态/商业阶段/规则交集，机器入站另需真实验证链 |
| 候选授权 | POST /grant-selections | 按真实认证对象、模型、商业阶段与授权 DATA 求交后分页；候选不代替最终判定 |
| 在线额度 | quota-reservations、quota-reservations/{id}、release、renewal | 持久并发预占与累计消费；受控读取元数据，按授权 DATA 先过滤再分页；同业务号重放不重复扣减，释放仅归还并发占用 |
| CRM履约 | POST /fulfillment-integration/synchronization | 建授权草稿，配置并启用后才回报completed |
| IAM角色联动 | role-projection-plans、confirmations、integration-commands、attempts、reconciliations | 默认MANUAL清单与人工确认；显式AUTO通过宿主端口执行并回读，MANUALLY_CONFIRMED不代表VERIFIED |

固定期限在授权创建时持久化；重签不随当前时间滑动，永久期限省略 exp。凭据列表的 grantId/activationId 筛选与 DATA 先在数据库求交，再分页计数；列表和元数据不包含完整文件。创建签发接口的 `201/202` 只返回凭据元数据和状态，不返回完整 JWS；只有单独的 `/credentials/{credentialId}/content` 在通过 deliver 权限与当前业务状态核验后返回 compact JWS，并设置 `no-store`，调用方不得记录原文。

产品编码在同一签发方内由数据库组合唯一键保证，不同签发方可以复用编码；并发重复创建返回冲突。签发、角色联动和额度操作按各自接口执行持久幂等；基础台账创建虽然要求请求标识头，尚不提供同键响应重放。基础台账创建结果不明时先按业务编码回读，不能把换键重试当作安全重放。

离线凭据签发仅接受 `OFFLINE` 授权；`ONLINE` 无需离线文件，`HYBRID` 不能通过该入口冒充混合租约。模式校验先于幂等任务回读，签名执行、恢复与交付也复核当前授权模式，历史错误任务不能绕过。

在线判定的业务拒绝统一返回 `200` 与完整判定字段。`modelVersion` 是本次实际求值的模型版本，无模型或未进入模型求值时为 `0`，不表示商业授权修订。未识别授权等拒绝的 `commercialPhase` 显式为 `null`，允许结论必须有有效商业阶段。固定期限允许结论的 `recheckAt` 为已知商业截止，永久且无截止时为 `null`；它不是缓存许可，调用方每次敏感入口仍需复核。目录或规则依赖暂不可用返回 `503`，不伪装成确定的业务拒绝。

部署型激活的 `callerBinding` 是已登记的调用来源（来源标识与主体标识），在线判定必须用当前官方认证主体与登记值逐项匹配；存量激活没有绑定时按失败关闭。当前设备政策只有 `NONE`，空设备证据表示不启用设备证明；提交非空 `componentDigests` 不能把它升级为可信设备证明。

## PAGE、API 与 DATA

PAGE 是前端入口权限，API 是接口操作权限，DATA 是资源和动作的数据范围。PAGE 不代替后端授权，机器不要求 PAGE，但仍要求应用准入、API 和 DATA；通过资源授权后还要满足 License 商业规则。

| DATA resource | actions | 唯一业务维度 |
| --- | --- | --- |
| license-issuer | read/manage | issuerId |
| license-customer | read/manage | customerId |
| license-product | read/manage/publish | productId |
| license-grant | read/manage/issue/deliver/use | grantId |
| license-signing-profile | read/manage | profileId |
| license-integration | read/manage | customerId |

受限范围按对应维度 IN 约束执行，不支持的表达式不能悄悄放宽。凭据、资格、激活及任务经 grantId 归属授权；planId、commandId 不是联动 DATA 维度。创建需要全范围治理的对象时必须具备相应 manage/all，不凭“管理员”名称越过授权。

## 协作端口与聚散边界

以下接口属于 `io.github.surezzzzzz.sdk.license.core.spi`，只使用 License 自有值类型和 JDK；宿主实现它们不需要编译依赖 Server 内部接口。

- IamDirectoryPort：宿主通过官方 IamOpenRoleClient 查询授权根及主体完整启用部门链；不可达、缺父、停用祖先或无法确认时拒绝。组织清库/重绑后重新核验，不伪造远端环境代际。
- CrmFulfillmentPort：宿主消费官方CRM机器客户端，保留cursor/limit与回写序号。CRM客户ID显式映射到License客户code，商品引用显式映射到productId；CRM管理台页码不改变机器契约。
- KeyMaterialPort：宿主读取官方KMS公钥及逻辑钥状态，登记精确keyRef/keyVersion、ES256、ACTIVE与P-256 SPKI；签名通过LicenseSigner使用登记快照，不现场选新版本或新公钥。
- LimiterPolicyPort：宿主实现 limiter Management 的策略写入、启停和按完整键回读。License 只提交 `serviceCode`、`resourceCode` 与窗口；客户维度固定为当前授权的 `customerId`，`CUSTOMER + EXACT` 及 `namespace` 由适配器按 Management 服务声明补全，且返回结果必须带非空 `namespace`。同一客户的同一服务/资源只能由一份授权占用，冲突启用直接拒绝，不合并不同授权的商业窗口；远端结果未知时保留本地 UNCERTAIN 台账，不重复创建。停用使用远端 `ruleId` 与 `rowVersion` 的 CAS 回显校验，任何规则切换或版本倒退都失败关闭。只有整份 GRANT 吊销、暂停或到期才停用客户级策略，单个凭据、激活或人员资格吊销不影响整份授权的限频；服务截止后的受控扫描会重试停用，并补偿远端成功但本地提交未完成的绑定，也会修复本地标为 `DISABLED` 但授权仍为 `ENABLED` 的异常绑定。

CRM履约由 License 保存自有履约号→授权去重台账。建立草稿不等于完成；只有启用授权后completed回报确认才算完成。认领、保存或回报未明时保留进度等待回读，不能用failed冒充已确认业务失败。

IAM受委托角色适配消费官方客户端，If-Match原样使用双引号强ETag `"open-role:<id>:<revision>"`。角色投影创建请求中的 `name` 只是远端展示名（可使用 `lic_<externalId>` 这类中性值）；远端创建响应或回读返回的 `roleCode` 才是实际角色编码，必须保存并用于后续查询和确认。默认MANUAL只刷新清单并登记人工回执；显式AUTO通过宿主的`RoleProjectionPort`创建专用角色、更新规则与部门挂载，每次写入后回读修订，只有真实核验完成才标为VERIFIED。结果未明时保留UNCERTAIN停止写入，缺协作端口时相关入口明确失败；不启动隐式后台执行。

## 最佳实践

1. 先登记签名档案和真实对象绑定，再签发；冻结模型、权益、期限与档案版本。KMS只签原文，不承担License业务判断。
2. SIGN/READ_PUBLIC_KEY授予自己的最小机器身份；人员凭据只用于浏览器，不转发给其他服务。入站机器判定与出站成功分别验收。
3. UNCERTAIN先对账，再带当前行版本受控恢复同一冻结任务；执行代数使旧执行者失效。READY不可退回，新幂等键不能用于绕过未明结果。
4. 旧kid、公钥快照与历史凭据保留。退役钥销毁查询中retainIndefinitely=true表示永久或坏载荷无截止保留，不能伪造日期；单档案SAFE不等于同一KMS钥全局无引用。
5. 独立Verifier必须从外部可信渠道安装公钥，预定验证目标；READY内部验签不能替代消费端协议核验。
6. 业务状态、外部调用和错误分类通过日志/领域事件关联；不输出Secret、人员令牌、签名原文、完整凭据或权益明文。消费审计异常不得反向破坏主流程。

## 能力边界与升级

在线额度通过 `license.quota.use` 与 `license-grant/use` DATA 准入，再校验真实认证对象、授权修订、模型动作及指标。并发预占和累计消费均在数据库事务内持久去重；固定消费窗口保留首次商业开始锚，不能通过重试或修订重置已用额度。并发释放和续期采用余额先锁、操作后读的统一顺序，释放后不会重复回补，累计消费不支持释放。回执包含状态、业务号、动作、商业修订、围栏和有效截止；只有 ACTIVE 表示仍可使用，重放返回原终态并不重新授权。

吊销包的分片与清单分别经登记公钥本地验签，全部完成后才保存 READY 并推进导出水位。签名失败保留 FAILED 与已分配序号，事务提交后发布终态事件。信任资料接口提供登记公钥和最新完整吊销快照；消费方仍须从可信渠道建立初始信任、验证完整包，再原子安装单调序号。元数据、签名文件和独立验签属于不同步骤。

服务不提供混合租约签发/续租、商业修订历史、维护期限治理、对象级 STATE_RECOVERY 签名恢复和额度治理页面。Core 可解析或验证相关类型不表示上述 HTTP 业务已实现。在线额度现已提供受控元数据查询，但不提供治理状态写入；LOCAL 额度现已支持部署激活创建时的真实分配台账、固定窗口锚固化、换机继承和普通离线签发透传；仍不提供独立的额度扩容/减配治理端点、LOCAL 消费台账或治理报表。可信代理人员证明及设备/离线身份适配仍需完整业务实现，不能使用常量成功或普通签发任务恢复替代。

旧租户模型及旧协议没有自动降级，也不提供原地转换为当前商业授权模型。新部署使用独立库；当前模型旧库升级按增量脚本恢复activationId、商业期限与履约去重事实，不能只增列而漏回填。

License只读写自己的事实。聚形态在宿主组合官方认证与传输件；散形态保留自己的领域能力，不共用IAM/CRM/KMS数据库，不以临时测试HTTP面代替正式业务接口。测试、候选制品消费、容器启动及真实门户业务各自验证，任何一项不代表其他项已通过。
