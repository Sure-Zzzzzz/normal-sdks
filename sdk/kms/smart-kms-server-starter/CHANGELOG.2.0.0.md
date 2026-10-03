# CHANGELOG - smart-kms-server-starter 2.0.0

## 发布信息

- 版本：`2.0.0`
- 类型：Major / 协作三权接入 + ownerPrincipalId 归属 + 管理面
- 基线版本：`1.0.0`

## 版本定位

KMS 从独立租户形态转为 **IAM Portal 的业务资源服务**（协作手册定位）：组合公共 Resource Server 接受 IAM 人员令牌与 AKSK 服务令牌，PAGE/API/DATA 三权由 IAM 投影驱动，KMS 只保留密钥、策略、密码学与销毁领域。注册形态为**非内置应用**（平台管理员零默认可见，治理归 kms-admin——见 DESIGN 2.0 第 1/6 节）。

## 主要变更

### 归属模型（破坏性）
- `ownerPrincipalId = sourceId:subjectId` 全面替换 tenantId；`/me/**` 固定当前主体 owner，管理面按 DataPlan 裁剪。
- **owner 两类定稿（2026-10-02 拍板）**：人 `iam:{subjectId}`（门户与 inherited AKU 同 owner）；
  服务凭证 `aksk:{clientId}`（AKP/静态 AKU，密钥仅 kms-admin 治理面可见；例行轮换=resetSecret 不改
  clientId，归属稳定）。业务身份不进底座，aksk/IAM 零改动。

### 协作接入
- 组合 `simple-resource-server-starter:1.1.1` + `simple-iam-resource-server-starter:1.0.0`（IAM 人员令牌）+ `simple-aksk-resource-server-starter:3.1.0`（AKSK 令牌，含 inherited AKU 接收：`strict-online` + `target-application-id`）+ `simple-data-permission-spring-mvc-starter:1.0.1`。
- `KmsResourceServerBridge` 将 `VerifiedResourceContext` 翻译为 `KmsPrincipal`，不重新认证；规则配置法条：keys 族零规则（拦截器回退控制器注解=精确规则），其余粗族 12 条通配（`/api/kms/**` 为 protected path）；keys 族配通配粗码会错拦最小权限主体。

### 端点（契约见 contract/openapi/smart-kms-admin-web.openapi.yaml 2.0.0，22 条路径）
- 自助：`GET /api/kms/me`、`/me/keys[/{keyRef}]`
- 管理：`GET /api/kms/admin/keys[/{keyRef}]`（DataPlan 范围）
- 销毁：`GET /destruction-jobs`（DataPlan 投影）、`GET /destruction-worker/health`
- **owner 级销毁窗口政策**：`GET/PUT /me/destruction-policy`（当前 owner 自助，幂等 upsert + 审计）、
  `GET /admin/owners/{ownerPrincipalId}/destruction-policy`（治理只读，DataPlan 范围内）。无政策行 = 不限制
  （组件能力态）；有行时 `scheduleDestruction` 服务端强校验 `[now+min, now+max]`。政策表
  `smart_kms_owner_destruction_policy`（见 docs/schema.sql）。
- 既有密钥/策略/密码学/销毁端点基路径 `/api/v1/kms` → **`/api/kms`**（无 v1 兼容层）

## 升级说明（重要）

- **1.x client 升级**：`simple-kms-client-starter` 必须 1.0.x → **2.0.0**（`/api/v1/kms` 已移除，1.x client 对 2.0 server 全部 404）。
- **数据**：SQL 文档仿 IAM 范式（`docs/schema.sql` 全量初始化 + `docs/migration/V1.0.1__to__V2.0.0__owner_principal_id_rebuild.sql`）；tenant 模型与 ownerPrincipalId 语义不同，升级为全量重建、存量数据不可迁移（详见 `docs/migration/README.md`）。
- **部署**：宿主必须显式开启 `io.github.surezzzzzz.sdk.limiter.redis.smart.enable=true`（默认关）；IAM 适配器坐标为 `simple-iam-resource-server-starter`（与公共层是两个坐标，漏装即 IAM 令牌恒 401）。
- **IAM 注册**（非内置）：manifest 申报 4 PAGE + 11 API + DATA 资源 `kms-key`（dimension=ownerPrincipalId）；kms-admin/kms-self-service/kms-crypto-user 三角色挂 kms 应用；inherited AKU 的 crypto 通道挂 aksk 应用（双通道见 DESIGN 6.3）。**kms-self-service = me.read/key.read/key.manage（自助建钥）**：`POST /keys` 豁免 DataPlan（归属服务端固定为本人、无跨 owner 面）；启停/轮换/销毁仍属管理面。
- **业务应用接入**（如 license 以 KMS 为底座）：创建 AKP → 授权调 kms → 凭证创建密钥 owner 即 `aksk:{clientId}`，仅 kms-admin 治理面可见；完整验收链见 DESIGN 2.0 第 8 节。
- **聚散原则（依赖形态）**：协作装配件（resource/iam/aksk 三 resource-starter）不随 POM 发布，宿主按需自引——引即全链协作，不引则自备 `KmsPrincipalResolver`；协议层 core、DATA MVC 注解与 mysql-route 路由（部署契约）随 POM 发布。

## 测试

- 模块测试全绿：Bridge/Resolver/OwnerAccessScope/Collaboration Http/既有密码学与生命周期族。
- 新增：销毁窗口政策 HTTP 集成（无行不限、越下限 400、窗口内通过、min>max 拒绝）。
