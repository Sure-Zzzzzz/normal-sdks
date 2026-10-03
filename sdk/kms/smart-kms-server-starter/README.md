# smart-kms-server-starter

`2.0.0` 提供 KMS 服务端：逻辑密钥与版本生命周期、精确 key policy、签名验签、AES-GCM
加解密、公钥分发、延迟销毁和审计事件。KMS 专属 MySQL 与服务进程构成密钥材料可信边界；调用方只能经
HTTP 使用能力，不能直接读 `smart_kms_*` 表，也不会得到私钥或对称密钥材料。

适用于 License 等业务服务，运行在 Spring Boot `2.7.9` 与 Java 8 字节码兼容环境。

## 最小接入

```groovy
implementation 'io.github.sure-zzzzzz:smart-kms-server-starter:2.0.0'
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

这是 KMS 的稳定主体标识，不是 IAM `userId`、用户名或显示名。IAM 与 AKSK 即使有同名 subject，也必然形成
不同 owner。创建密钥固定归属当前认证主体，HTTP 请求不能指定 owner。KMS 不存在租户字段或租户授权维度。

**协作装配件宿主自引（聚散原则，不随本 POM 强制传递）**：`simple-resource-server-starter:1.1.1` +
`simple-iam-resource-server-starter:1.0.0`（IAM 人员令牌）+ `simple-aksk-resource-server-starter:3.1.0`
（AKSK 令牌，含 inherited AKU 接收）。三件引入后内置 `KmsResourceServerPrincipalResolver` 自动装配，任何主体
或权限无法安全派生时返回 `401` 不回退；不引入则宿主自备 `KmsPrincipalResolver` Bean（桥让位），KMS 领域
（密钥/策略/密码学/销毁）照常可用。协议层（`simple-application-authorization-core` + `simple-data-permission-spring-mvc-starter`，其 api 链
顺带导出 data-permission/resource-server core）与 mysql-route 数据源路由（部署契约，配置即 `simple.mysql.route` 形态）
随 POM 发布。

## 三权契约

PAGE 只控制 Portal 菜单与路由，API 按端点拦截，DATA 决定管理范围；三者由 IAM 投影分别提供，不能相互推导。

| 类别 | 权限/资源 | 语义 |
| --- | --- | --- |
| PAGE | `kms.page.my-keys` | 我的密钥工作区 |
| PAGE | `kms.page.keys` | 密钥治理工作区 |
| PAGE | `kms.page.policies` | 策略治理工作区 |
| PAGE | `kms.page.destruction` | 销毁任务工作区 |
| API | `kms.me.read` | 读取门户所需的最小主体与 PAGE 权限摘要 |
| API | `kms.key.read` | 读取个人或管理范围内的密钥 |
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

DATA、PAGE 或 owner 都不能替代 key policy。

## HTTP 契约

基础路径为 `/api/kms`。响应不使用业务 `code` 包装；错误响应使用 HTTP status、中文安全消息与 requestId。
所有管理写操作必须带 `Idempotency-Key`；状态变更、轮换、销毁和策略撤销还须带当前 `expectedRowVersion`。

| 方法 | 路径 | API / DATA | 行为 |
| --- | --- | --- | --- |
| `GET` | `/me` | `kms.me.read` | 最小主体、KMS API 权限和 PAGE 权限，不返回 DATA 原文或裸 subjectId |
| `GET` | `/me/keys` | `kms.key.read` | 当前 owner 的分页密钥 |
| `GET` | `/me/keys/{keyRef}` | `kms.key.read` | 当前 owner 的单个密钥 |
| `GET` | `/admin/keys` | `kms.key.read` / `kms-key:read` | DataPlan 范围内分页密钥，返回 ownerPrincipalId |
| `GET` | `/admin/keys/{keyRef}` | `kms.key.read` / `kms-key:read` | DataPlan 范围内单个密钥 |
| `POST` | `/keys` | `kms.key.manage` / `kms-key:manage` | 创建当前 owner 的 ES256 或 AES-256-GCM 密钥 |
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
| `GET` | `/destruction-jobs` | `kms.key.destroy` / `kms-key:destroy` | DataPlan 范围内销毁任务分页，无领取令牌 |
| `GET` | `/destruction-worker/health` | `kms.key.destroy` | 当前实例 worker 健康事实 |
| `GET` | `/me/destruction-policy` | `kms.key.destroy` | 当前 owner 的销毁窗口政策（无行 = 不限制） |
| `PUT` | `/me/destruction-policy` | `kms.key.destroy` | 写当前 owner 的销毁窗口政策（幂等 upsert + 审计） |
| `GET` | `/admin/owners/{ownerPrincipalId}/destruction-policy` | `kms.key.destroy` / `kms-key:destroy` | 治理面只读 DataPlan 范围内 owner 政策 |

销毁完成会清空私钥和对称材料，不可恢复。当前明确不提供密钥材料导入/导出、归属转移、审计查询、KEK、HSM、TPM
或外部 KMS 集成；审计查询将在具备独立服务端 API 后再定义页面、API 与 DATA 投影。

## 数据库

- 全新初始化：`docs/schema.sql`（MySQL 5.7+，删除同名既有 `smart_kms_*` 表）。
- 版本升级：`docs/migration/`（`V{from}__to__V{to}__{name}.sql`，同库仅执行一次，执行前备份）。
- 1.x -> 2.0.0 为**全量重建**（tenantId -> ownerPrincipalId 语义不可映射），详见 `docs/migration/README.md`。


## Resource Server 路由权限

部署必须为 `/api/kms/**` 同时配置 `protected-paths` 与精确 `api-permission-rules`。缺少映射时，受保护请求会在
Resource Server 层失败为 `403`。部署规则法条：**keys 族配零规则**——拦截器对未匹配路径回退控制器 `@RequireApiPermission`（注解即精确规则）；其余粗族共 12 条通配。禁止 keys 族配通配粗码：粗码覆盖注解精码，会把只有 `kms.read-public-key`/`kms.key.policy` 的最小权限主体错拦在规则层（真实案例：crypto-only 调用方取公钥被 `/keys/**`→`kms.key.read` 错拦 403）。

## IAM 投影

KMS 不为 IAM 之外写身份或授权业务代码。IAM 恢复材料应投影以下最小角色：

| 角色 | PAGE/API | DATA |
| --- | --- | --- |
| `kms-admin` | 管理页面及所需 key API | `kms-key` 对应 action `all=true` |
| `kms-self-service` | `kms.page.my-keys`、`kms.me.read`、`kms.key.read` | 无；范围固定为认证主体 |
| `kms-crypto-user` | 仅实际需要的 crypto/public-key API | 无；仍需精确 key policy |

IAM 数据库被清理后，重放 trusted application、权限清单、角色规则和应用准入。恢复 IAM 投影不会改变
KMS 已持久化的 owner 或密钥材料。

## 安全与运行边界

- 密钥材料不会进入 HTTP 响应、日志、审计事件或幂等响应快照。
- 数据库 UTC 时间是策略到期、状态迁移、销毁调度和租约判断的权威时间。
- 验签不匹配返回 `200` 与 `{ "valid": false }`；密码学请求不使用幂等键，网络结果未知时调用方不能盲目重试。
- 审计事件在成功事务提交后发布；listener 失败不改变 KMS 业务结果。
- Portal 是独立 qiankun 子应用，最终验收从 IAM Portal 的正式 Nginx 入口进行；KMS Server 不依赖 Portal 或 fake SSO。
