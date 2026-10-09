# simple-iam-client-core

IAM openapi 的契约层：定义与 IAM Server（`/iam/api/**`）交互的全部公开接口、结果模型、常量与
异常族。SB2 与 jakarta 四个传输装配件（resttemplate / feign 及对应 jakarta 版）共用本模块。

## 定位

提供与 `simple-iam-server-starter:1.3.5+` openapi 契约对齐的 Java 接口，使调用方与 HTTP 传输
细节、JSON 编解码、If-Match 乐观并发头管理解耦。

## 适用场景

- **业务调用方**：不直接引入本模块，而是选择一个传输装配件（
  `simple-iam-aksk-resttemplate-client-starter` 或 `simple-iam-aksk-feign-client-starter`），
  由其传递依赖本 core。
- **自定义传输实现方**：需要 core 契约但自行装配传输。

## 依赖

```groovy
implementation 'io.github.sure-zzzzzz:simple-iam-client-core:1.0.1'
```

- 发布 POM 零依赖；Java 8 字节码，SB2（Java 8+）与 jakarta（Java 17+）宿主均可消费
- 公开包 `io.github.surezzzzzz.sdk.iam.client`（与传输 starter 分包）

## 公开 API

| 类别 | 内容 |
| --- | --- |
| 接口 | `IamUserClient`（12 方法）、`IamDepartmentClient`（5 方法）、`IamOpenRoleClient`（13 方法）——共 30 方法，逐端点对位 server 三组 rest controller |
| 分页模型 | `IamSpringPage<T>`（users/departments 族，Spring Page wire 形态：content/totalElements，页码 **0 起**）与 `IamOpenRolePage`/`IamOpenRoleDepartmentPage`（open-roles 族，server 自持形态：items/total，页码 **1 起**）——两种形态是 server 契约事实，不可混用 |
| 领域模型 | `IamUser` / `IamDepartment` / `IamOpenRole`（含 `revision`——乐观并发 If-Match 值来源）/ `IamOpenRoleRule` / `IamOrganizationDirectory` / `IamOrganizationMember` / `IamTargetApplication` / `IamPermissionManifest` 等 14 个 |
| 常量 | `SimpleIamClientConstant`：路径段/协议头（`If-Match`/`ETag`）/wire 字段名/查询参数名，值逐字对位 server 契约 |
| 异常族 | `SimpleIamClientException`（基类）+ `IamClientProtocolException`（2xx 形状不符）/ `IamClientConfigurationException`（配置不合法）。非 2xx 不进本族——由传输层透传 Spring/Feign 标准异常 |

## 乐观并发（If-Match）

open-roles 族的写操作（putOpenRoleRule / deleteOpenRoleRule / mount / unmount）携带
`String ifMatch` 参数：值为 `open-role:<openRoleId>:<revision>`，`revision` 直接取自上次响应
模型的 `getRevision()`；`null` = 首写（不携带条件头）。缺条件 428、版本旧 412 由传输层透传，
调用方冲突后应重读重算，不盲重发。

## 授权面（消费方 AKP 申领）

调用各接口族的 AKP 需在 IAM 侧申领对应 API 码与 DATA 范围：

| 接口族 | API 码 | DATA 资源 |
| --- | --- | --- |
| IamUserClient（除页面准入查询） | `iam:user:api` | `iam:user`（部门范围求交） |
| IamUserClient#listPageAdmittedApplications | `iam:portal:api`（单码即够，勿授 `iam:user:api`） | —（应用码聚合非用户档案，无 DATA 面） |
| IamDepartmentClient | `iam:department:api` | — |
| IamOpenRoleClient 全部方法 | `iam:open-role:create:api` / `iam:open-role:read:api` / `iam:open-role-rule:read:api` / `iam:open-role-rule:write:api` / `iam:open-department-role:read:api` / `iam:open-department-role:write:api` / `iam:open-directory:read:api` / `iam:open-application:read:api`（按方法族分码，值=server 常量逐字） | `iam:open-role` 系列（读/写动作分码） |

## 安全边界

- 模块零 HTTP、零 Spring、零认证接触；凭据与传输由传输装配件与 aksk 底座管理
- 模型不承载密钥材料；`phone` 为 E.164 规范化形态（null=未登记）

## 兼容性

- 对应 server 版本：`simple-iam-server-starter:1.3.5+`（`/iam/api` 基路径；open-roles 族
  契约 1.3.2 引入，phone 三态语义 1.3.3 收紧对 client 透明；users 投影 `subjectId`
  与页面准入清单方法 1.3.5 引入，旧 server 下这两个新增能力不可用，其余方法不受影响）
- 契约演进 = server 先行、client 跟版走号；本模块无前版
