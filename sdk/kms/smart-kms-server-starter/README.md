# smart-kms-server-starter

`2.0.2` 提供 KMS 服务端：逻辑密钥与版本生命周期、精确 key policy、签名验签、AES-GCM
加解密、公钥分发、延迟销毁和审计事件。KMS 专属 MySQL 与服务进程构成密钥材料可信边界；调用方只能经
HTTP 使用能力，不能直接读 `smart_kms_*` 表，也不会得到私钥或对称密钥材料。

适用于 License 等业务服务，运行在 Spring Boot `2.7.9` 与 Java 8 字节码兼容环境。

`2.0.2` 补齐本人公钥与密钥级销毁进度：人员具备读取公钥 API 权限时，可直接读取本人签名密钥公钥，
无需额外使用策略；详情可回读各版本计划时间、进度和取消资格。本人启停、轮换、安排与取消继续按对应
API 权限执行，无需 DATA（跨归属治理的数据范围授权）；跨归属治理继续要求 DATA。服务端仍复用
`smart-kms-core:2.0.0`，与 KMS Web `1.0.0`、Contract `2.0.2` 配套，无数据库迁移。
版本变更见 [CHANGELOG.2.0.2.md](CHANGELOG.2.0.2.md)。

## 最小接入

```groovy
implementation 'io.github.sure-zzzzzz:smart-kms-server-starter:2.0.2'
implementation 'io.github.sure-zzzzzz:simple-mysql-route-starter:1.1.1'
```

执行 [docs/schema.sql](docs/schema.sql) 后，为 KMS 配置唯一 MySQL Route 主数据源。模块不自动建表、
不创建专用 DataSource 或事务管理器，也不进行跨数据源事务、SQL 改写或动态路由。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        mysql:
          route:
            enable: true
            primary-datasource: kms
            datasources:
              kms:
                url: jdbc:mysql://mysql.example.internal:3306/smart_kms?useUnicode=true&characterEncoding=UTF-8&useSSL=true&serverTimezone=UTC
                username: kms_runtime
                password: ${KMS_MYSQL_PASSWORD}
                driver-class-name: com.mysql.cj.jdbc.Driver
```

## 资源归属和认证

KMS 不保存 IAM 用户、不读取 LDAP/OIDC、不解析 token，也不访问 IAM 或 AKSK 数据库。认证由已发布的
Resource Server starter 完成；认证桥只读取 `VerifiedResourceContext`：

```text
principalId = ownerPrincipalId = sourceId + ":" + subjectId
```

这是 KMS 的稳定主体标识，不是 IAM `userId`、用户名或显示名。IAM 人员与 inherited AKU
（继承人员身份的访问凭证）沿用认证桥归一为 `iam:{subjectId}`；独立服务主体保留来源前缀。
创建与本人接口固定使用认证主体归属，HTTP 请求不能指定 owner。KMS 不存在租户字段或租户授权维度。

**协作装配件宿主自引（聚散原则，不随本 POM 强制传递）**：`simple-resource-server-starter:1.1.1` +
`simple-iam-resource-server-starter:1.0.0`（IAM 人员令牌）+ `simple-aksk-resource-server-starter:3.1.0`
（AKSK 令牌，含 inherited AKU 接收）。三件引入后内置 `KmsResourceServerPrincipalResolver` 自动装配，任何主体
或权限无法安全派生时返回 `401` 不回退；不引入则宿主自备 `KmsPrincipalResolver` Bean（桥让位），KMS 领域
（密钥/策略/密码学/销毁）照常可用。协议层（`simple-application-authorization-core` + `simple-data-permission-spring-mvc-starter`，其 api 链
顺带导出 data-permission/resource-server core）与 mysql-route 数据源路由（部署契约，配置即 `simple.mysql.route` 形态）
随 POM 发布。

默认桥仅在认证结果为 `HUMAN`（已验证的人员身份）时，为请求上下文保存人员证明；`SERVICE`
（独立服务身份）继续调用要求使用策略的原公钥接口。自定义解析器的原两参数
`KmsRequestContext(principal, requestId)` 保持可用，默认没有人员证明，因此不能访问新本人公钥接口。
宿主须在自己的可信认证器已证明人员身份后使用 `KmsRequestContext.forVerifiedHuman(principal, requestId)`；
该工厂要求 `principalId == ownerPrincipalId`，不能根据标识前缀或 HTTP 请求字段推断身份。

## 三权契约

PAGE 只控制 Portal 菜单与路由，API 按端点拦截，DATA 决定管理范围；三者由 IAM 投影分别提供，不能相互推导。

| 类别 | 权限/资源 | 语义 |
| --- | --- | --- |
| PAGE | `kms.page.my-keys` | 我的密钥工作区 |
| PAGE | `kms.page.keys` | 密钥治理工作区 |
| PAGE | `kms.page.policies` | 策略治理工作区 |
| PAGE | `kms.page.destruction` | 销毁任务工作区 |
| API | `kms.me.read` | 读取门户所需的最小主体与 PAGE 权限摘要 |
| API | `kms.key.read` | 读取个人或管理范围内的密钥和密钥级销毁明细 |
| API | `kms.key.manage` | 创建、状态变更与轮换 |
| API | `kms.key.policy` | 创建、读取和撤销精确 key policy |
| API | `kms.key.destroy` | 安排/取消销毁、查询任务和 worker 健康 |
| API | `kms.sign`、`kms.verify`、`kms.encrypt`、`kms.decrypt`、`kms.read-public-key` | 对应密码学或公钥读取操作 |
| DATA | `kms-key` | actions 为 `read`、`manage`、`policy`、`destroy`；唯一维度为 `ownerPrincipalId` |

管理 API 对每次请求消费相应 DATA action 的 `DataAccessPlan`：`ALLOW_ALL` 可访问全部 owner；受限计划只能由
`ownerPrincipalId IN (...)` 组成；未知维度、空约束、DENY 或无法完整转换的约束均为 `403`。管理列表、总数、
详情及写操作使用同一范围。对单个未授权资源返回 `404`，不泄露存在性。

`/me/**` 不消费动态 DATA 模板：它固定使用当前认证主体自身 owner，因此普通用户只能看到自己的密钥。管理员要
看到或治理全部既有数据，必须同时有 API permission 和对应 action 的 `kms-key` `all=true` DataPlan。管理员
创建的密钥仍固定归属管理员自己，不能代表任意 owner 创建。

密码学调用额外要求未过期的 allow-only 精确 key policy 与正确的密钥/版本状态：

```text
API permission + key policy + key/version state
```

DATA、PAGE 或 owner 都不能替代原公钥与密码学接口的 key policy。新增人员本人公钥查询按
API、可信人员身份、本人归属和状态独立授权，不要求额外使用策略。

## HTTP 契约

基础路径为 `/api/kms`。响应不使用业务 `code` 包装；错误响应使用 HTTP status、中文安全消息与 requestId。
所有管理写操作必须带 `Idempotency-Key`；状态变更、轮换、销毁和策略撤销还须带当前 `expectedRowVersion`。

| 方法 | 路径 | API / DATA | 行为 |
| --- | --- | --- | --- |
| `GET` | `/me` | `kms.me.read` | 最小主体、KMS API 权限和 PAGE 权限，不返回 DATA 原文或裸 subjectId |
| `GET` | `/me/keys` | `kms.key.read` | 当前 owner 的分页密钥 |
| `GET` | `/me/keys/{keyRef}` | `kms.key.read` | 当前 owner 的单个密钥 |
| `GET` | `/me/keys/{keyRef}/public-keys` | `kms.read-public-key` + HUMAN + 本人归属 | 全部合法可分发 ES256 公钥，无需使用策略，响应 no-store |
| `GET` | `/me/keys/{keyRef}/destruction` | `kms.key.read` | 本人密钥的销毁进度与业务取消资格，无 DATA，响应 no-store |
| `PATCH` | `/me/keys/{keyRef}/state` | `kms.key.manage` | 本人 ACTIVE 与 DISABLED 互相迁移，不要求 DATA |
| `POST` | `/me/keys/{keyRef}/versions` | `kms.key.manage` | 轮换本人活动密钥，不要求 DATA |
| `PUT` | `/me/keys/{keyRef}/destruction` | `kms.key.destroy` | 按本人销毁窗口安排，不要求 DATA |
| `DELETE` | `/me/keys/{keyRef}/destruction` | `kms.key.destroy` | 取消本人从未被领取的销毁任务，不要求 DATA |
| `GET` | `/admin/policies` | `kms.key.policy` / `kms-key` DATA | DataPlan 范围内跨钥策略分页，支持别名/主体/操作筛选，返回密钥别名与双方主体显示名 |
| `GET` | `/admin/keys` | `kms.key.read` / `kms-key:read` | DataPlan 范围内分页密钥，返回 ownerPrincipalId、ownerDisplayName，支持可选 ownerPrincipalId 精确筛选（只在范围内收窄） |
| `GET` | `/admin/keys/{keyRef}` | `kms.key.read` / `kms-key:read` | DataPlan 范围内单个密钥，返回 ownerDisplayName |
| `GET` | `/admin/keys/{keyRef}/destruction` | `kms.key.read` / `kms-key:read` | 授权范围内密钥的销毁进度与业务取消资格，响应 no-store |
| `POST` | `/keys` | `kms.key.manage` | 创建当前 owner 的 ES256 或 AES-256-GCM 密钥，不要求 DATA |
| `GET` | `/keys`、`/keys/{keyRef}` | `kms.key.read` | 兼容读取当前 owner 的密钥 |
| `PATCH` | `/keys/{keyRef}/state` | `kms.key.manage` / `kms-key:manage` | `ACTIVE` 与 `DISABLED` 合法迁移 |
| `POST` | `/keys/{keyRef}/versions` | `kms.key.manage` / `kms-key:manage` | 轮换并退役旧活动版本 |
| `PUT` | `/keys/{keyRef}/destruction` | `kms.key.destroy` / `kms-key:destroy` | 安排整个逻辑密钥销毁 |
| `DELETE` | `/keys/{keyRef}/destruction` | `kms.key.destroy` / `kms-key:destroy` | worker 首次领取前取消销毁 |
| `GET` | `/keys/{keyRef}/public-key`、`/public-keys` | `kms.read-public-key` + key policy | 读取可分发 ES256 公钥，固定 `Cache-Control: no-store` |
| `POST` | `/keys/{keyRef}/policies` | `kms.key.policy` / `kms-key:policy` | 创建 allow-only 精确策略 |
| `GET` | `/keys/{keyRef}/policies` | `kms.key.policy` / `kms-key:policy` | 查询策略 |
| `DELETE` | `/keys/{keyRef}/policies/{policyId}` | `kms.key.policy` / `kms-key:policy` | 撤销策略 |
| `POST` | `/crypto/signatures`、`/crypto/verifications` | `kms.sign` / `kms.verify` + key policy | ES256 签名或验签 |
| `POST` | `/crypto/envelopes`、`/crypto/decryptions` | `kms.encrypt` / `kms.decrypt` + key policy | AES-256-GCM 加密或解密 |
| `GET` | `/destruction-jobs` | `kms.key.destroy` / `kms-key:destroy` | DataPlan 范围内销毁任务分页，无领取令牌，条目含归属主体，支持可选 ownerPrincipalId 精确筛选 |
| `GET` | `/destruction-worker/health` | `kms.key.destroy` | 当前实例 worker 健康事实 |
| `GET` | `/me/destruction-policy` | `kms.key.destroy` | 当前 owner 的销毁窗口政策（无行 = 不限制） |
| `PUT` | `/me/destruction-policy` | `kms.key.destroy` | 写当前 owner 的销毁窗口政策（幂等 upsert + 审计） |
| `GET` | `/admin/owners/{ownerPrincipalId}/destruction-policy` | `kms.key.destroy` / `kms-key:destroy` | 治理面只读 DataPlan 范围内 owner 政策 |

本人四项写接口严格拒绝未知字段、数字字符串、小数版本和非法状态；`expectedRowVersion` 为非负整数。销毁时间的输入年份及换算后的 UTC 年份均须在 1–9999 范围内，越界返回 400。
状态、轮换和安排成功返回密钥元数据，取消成功为 `204`。销毁时间必须带时区，进入命令和请求摘要前
统一为 UTC 毫秒。本人响应不包含 owner、材料或任务内部字段，他人 keyRef 为 `404`。

本人四项写接口均发送 `Content-Type: application/json` 和 `Idempotency-Key`，请求正文如下：

| 操作 | 请求正文示例 |
| --- | --- |
| 停用 | `{ "state": "DISABLED", "expectedRowVersion": 0 }`；启用改为 `ACTIVE` |
| 轮换 | `{ "expectedRowVersion": 0 }` |
| 安排销毁 | `{ "dueAt": "2027-01-01T00:00:00.000Z", "expectedRowVersion": 0 }` |
| 取消销毁 | `{ "expectedRowVersion": 0 }`；`DELETE` 同样须发送 JSON 正文 |

示例的 `expectedRowVersion` 必须替换为当前详情返回的 `rowVersion`（资源版本，用于防止覆盖并发修改），
销毁时间必须晚于服务端当前时间且符合归属人的销毁窗口。每个新动作使用新的幂等键；网络结果未知时，
在保留期内使用原路径、原幂等键和相同规范化正文重试，不修改资源版本或切换入口。

新旧写路径各自保存幂等作用域，历史记录保持不变；同路径、同规范化请求在保留期内重放首次成功响应。
同一作用域内相同幂等键对应不同正文为 `409`；首次执行时资源版本不匹配也为 `409`，成功请求的合法重放
仍返回首次响应。缺 API 权限为 `403`。切换入口须回读资源并发起新动作，
不能根据 `403/404` 回退到另一入口。启停只允许 ACTIVE 与 DISABLED 互相迁移；待销毁密钥必须通过
取消任务恢复，不能用 PATCH 绕过领取校验或标记为已销毁。

本人公钥接口返回 `keyRef/version/algorithm/state/publicKey` 的数组，按版本升序；公钥是无填充
Base64url 编码的 X.509 SPKI DER。活动或停用的 SIGN/ES256 密钥可读取活动、退役版本；AES、待销毁、
已销毁返回 `409`。缺版本、材料或不可能的存储状态返回 `503`，不返回部分列表。接口不额外要求
`kms.key.read`；Web 打开密钥详情仍要求该读取权限。缺公钥 API 权限、SERVICE 或缺人员证明为 `403`，
本人归属内不存在及他人目标统一 `404`。原公钥和密码学接口的使用策略校验保持。

密钥级销毁明细返回 `keyRef/keyState/rowVersion/cancelEligible/items`，`items` 按 `keyVersion` 升序，
每版只有 `keyVersion/state/dueAt/completedAt`。`dueAt` 是最早允许开始时间，未完成的 `completedAt` 为
null；两者使用 UTC 三位毫秒时间。未排程或取消后 items 为空，完成记录仍保留。查询失败为 `503`。
`cancelEligible` 表示读取时全部任务从未领取且能恢复的业务资格，不包含调用者的销毁授权。
任一任务历史领取过，即使释放回等待态或租约过期也不能取消；取消命令仍在提交时最终复核。
Web 按密钥标识、状态和 rowVersion 配对元数据与明细，回读失败不能伪装为无任务或沿用取消资格。

## 销毁政策与密钥使用策略

销毁窗口政策按归属人生效，自动约束其名下全部密钥，无需逐把关联。归属人通过
`GET/PUT /api/kms/me/destruction-policy` 读取或保存允许的最短、最长提前时间；管理员只能读取 DATA
范围内归属人的政策，不能代其修改。没有政策行或未设置某侧边界时，该侧不作限制。
安排销毁时，服务端按数据库当前时间校验窗口；保存政策不会自动安排销毁，也不会改排已有任务。
只有后台从未领取过的任务才可取消，取消后恢复安排前状态。

密钥使用策略用于授权某个调用主体访问指定密钥、版本和密码学操作，与销毁窗口政策分开配置。
人员本人公钥查询只需 `kms.read-public-key`、可信 HUMAN、本人归属与合法状态，既有密钥、新建密钥和
轮换版本均无需补 `READ_PUBLIC_KEY` 使用策略。原公钥接口和密码学调用仍要求精确策略；管理员的
治理 DATA 不能从本人路径读取他人公钥。私钥与 AES 对称密钥材料不提供查看或导出。

销毁完成会清空私钥和对称材料，不可恢复。当前明确不提供密钥材料导入/导出、归属转移、审计查询、KEK、HSM、TPM
或外部 KMS 集成；审计查询将在具备独立服务端 API 后再定义页面、API 与 DATA 投影。

## 数据库

- 全新初始化：`docs/schema.sql`（MySQL 5.7+，删除同名既有 `smart_kms_*` 表）。
- 版本升级：`docs/migration/`（`V{from}__to__V{to}__{name}.sql`，同库仅执行一次，执行前备份）。
- 1.x -> 2.0.0 为**全量重建**（tenantId -> ownerPrincipalId 语义不可映射），详见 `docs/migration/README.md`。
- 2.0.0/2.0.1 -> 2.0.2 复用全部既有表和历史记录，无迁移；已有数据库不得重新执行初始化脚本。


## Resource Server 路由权限

部署的 `protected-paths` 必须覆盖 `/api/kms/**`，每个方法使用其精确 API 权限。
默认公共资源拦截器对未匹配的路由规则读取控制器 `@RequireApiPermission`，没有规则也没有注解则拒绝。
密钥族和新增本人写接口可沿用此注解回退；不要配置 `/keys/**` 或所有方法的 `/me/**` 粗权限，
显式规则不能用读取权限覆盖公钥、策略或生命周期动作。配置方法级规则时须逐项对应 HTTP 契约。

## IAM 投影

KMS 不为 IAM 之外写身份或授权业务代码。IAM 恢复材料应投影以下最小角色：

| 角色 | PAGE/API | DATA |
| --- | --- | --- |
| `kms-admin` | 四个页面；`kms.me.read`、`kms.key.read`、`kms.key.manage`、`kms.key.policy`、`kms.key.destroy`、`kms.read-public-key` | `kms-key` 的 read/manage/policy/destroy 四动作 `all=true` |
| `kms-self-service` | `kms.page.my-keys`；`kms.me.read`、`kms.key.read`、`kms.key.manage`、`kms.key.destroy`、`kms.read-public-key` | 无；范围固定为认证主体，不授予 all=true |
| `kms-crypto-user` | 无页面；`kms.sign`、`kms.verify`、`kms.encrypt`、`kms.decrypt`、`kms.read-public-key` | 无；仍需精确 key policy |

IAM 数据库被清理后，重放 trusted application、权限清单、角色规则和应用准入。恢复 IAM 投影不会改变
KMS 已持久化的 owner 或密钥材料。

注册、菜单、角色、人员准入与资源验证统一见
[KMS Web 可信应用接入手册](https://github.com/Sure-Zzzzzz/smart-kms-admin-web/blob/main/docs/TRUSTED_APPLICATION_ONBOARDING.md)。
角色 API 不自动创建密钥使用策略；标准自助角色已能通过新本人公钥路径读取本人合法公钥。

## 从 2.0.1 升级

1. 升级 Server 到 `2.0.2`，复用原数据库。标准自助角色已有五项 API，无需增权、重新注册或补历史策略。
2. 自定义主体解析器按前述方法提供可信人员证明；旧构造器只影响新本人公钥入口，原调用保持兼容。
   使用自定义密钥、版本或任务存储的宿主须提供 `KmsKeyDestructionQueryRepository`，保证同一已提交
   快照中的状态、任务与历史取消资格。默认 JDBC 明细仅对内置同源存储装配，不读取影子数据；
   缺该新端口时保留原路由，不装配两个销毁明细路由。
3. 核验零使用策略的人员仍可读本人合法公钥、旧接口仍拒绝、销毁进度可回读，再部署 Web `1.0.0`。
   当前 Web 要求 Server/Contract `2.0.2`，不能先部署，也不根据失败回退旧接口。
4. 治理列表归属筛选与显示名：`/admin/keys`、`/keys` 的 `ownerPrincipalId` 精确筛选只在 DataPlan 范围内收窄；
   显示名来自可选 `KmsPrincipalDisplayNameResolver`，宿主可用自有目录实现替换，默认未解析时响应字段为空、
   前端回退原始主体标识。自定义 `KmsKeyQueryRepository` 未覆盖归属筛选重载时，携带筛选返回 400。

回滚先恢复与 Server `2.0.1` 兼容的旧 Web 制品与配置，再回退 Server。没有旧制品时先撤下门户入口；
不需要数据库回滚、补策略或任务重建。

## 从 2.0.0 升级

升级前保存与 Server `2.0.0` 兼容的 Web 制品及配置，按以下顺序部署：

1. 升级 Server 到 `2.0.2`，复用原有数据库，不重新执行初始化脚本。
2. 按可信应用接入手册更新 IAM 角色规则并核验用户投影：管理员增加 `kms.read-public-key`，
   自助用户增加 `kms.key.destroy` 和 `kms.read-public-key`；自助 DATA 保持为空，已有治理数据范围按原授权保留。
   原完整权限清单已声明这些权限码，标准升级无需新增权限码。
3. 部署 Web `1.0.0`，分别验收本人路径和治理路径。Web 本人模式使用新增接口，不能先于 Server 部署，
   当前 Web 要求 Server/Contract `2.0.2`。只调整角色无法在旧 Server 上补齐本人写接口和新增查询。

旧治理写继续校验 DATA，合法调用保持兼容。新旧启停接口只接受 `ACTIVE`、`DISABLED` 目标：非法目标
返回 `400`，待销毁、已销毁密钥或不发生状态迁移的启停请求返回 `409`；取消与后台销毁继续使用专属流程。
Core `2.0.0`、现有 Client、IAM 和 AKSK 无需为本次增量升级，也不新增生产依赖。

回滚时先恢复保存的兼容 Web 制品及配置，再回退 Server。Web `1.0.0` 没有旧接口切换开关，
首次接入且没有兼容旧制品时须先撤下门户入口，不能依赖接口失败后的自动回退。

## 安全与运行边界

- 私钥与对称密钥材料不会进入 HTTP 响应、日志、审计事件或幂等响应快照；公钥只通过授权公钥接口分发。
- 数据库 UTC 时间是策略到期、状态迁移、销毁调度和租约判断的权威时间。
- 验签不匹配返回 `200` 与 `{ "valid": false }`；密码学请求不使用幂等键，网络结果未知时调用方不能盲目重试。
- 审计事件在成功事务提交后发布；listener 失败不改变 KMS 业务结果。
- Portal 是独立 qiankun 子应用，最终验收从 IAM Portal 的正式 Nginx 入口进行；KMS Server 不依赖 Portal 或 fake SSO。
