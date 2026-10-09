# simple-iam-server-core

IAM Server 契约层。承载跨模块共享的纯契约：审计与令牌事件、错误码、常量和异常，不含任何技术设施实现。

当前版本为 `1.3.3`。版本沿革见各 `CHANGELOG.*.md`。

`1.3.3` 新增内置权限编码常量 `iam:portal:api`（页面准入查询，端点随 simple-iam-server-starter 1.3.5 发布）；既有常量、身份协议、公开方法、异常类型、事件与枚举不变。

`1.3.2` 在三个既有常量类中补齐受委托角色契约：API（接口操作权限）、DATA（数据范围权限）、固定执行上限、标识格式、内部错误码与安全文案。身份协议、既有公开方法、异常类型、事件及枚举不变；本模块不实现角色治理接口或商业授权业务。

本模块是 IAM 契约链路的中间层：`simple-iam-core`（身份协议契约基座）→ **`simple-iam-server-core`（本模块，IAM Server 域契约）** → `simple-iam-server-starter`（应用层：Web API、服务、装配、实体与仓储）。

## 最小接入

部署 IAM 服务时接入 `simple-iam-server-starter`，由其引入本模块。直接消费事件、常量或异常的扩展模块可单独依赖：

```gradle
dependencies {
    implementation "io.github.sure-zzzzzz:simple-iam-server-core:1.3.3"
}
```

引入后可引用常量或订阅事件；它不会自行启动 HTTP 服务、注册权限、创建数据库表或保存审计记录。

## 契约内容

### 事件（event 包，供审计监听等消费方订阅）

四族事件统一继承 `AbstractIamEvent`：

- **认证事件**：`AuthenticationEvent`（登录成功/失败、账号锁定等，类型见 `AuthenticationEventType`）；
- **会话事件**：`SessionLifecycleEvent`（建立/刷新/撤销/过期，`SessionEventType` + `SessionEventCause`）；
- **令牌事件**：`TokenIssuedEvent`、`TokenVerifiedEvent`、`TokenRevokedEvent`、`TokenRemovedEvent`、`RefreshTokenReuseDetectedEvent`（`TokenEventType` + `TokenEventCause`）；
- **管理事件**：`AdminActionEvent`（管理端写操作，`AdminActionType` + `AdminSubjectType`）。

另有 `MessageRecipientsResolvedEvent`（站内信收件人解析完成，供通知链路消费）。

### 受委托角色（1.3.2）

受委托角色是外部服务在批准范围内创建和维护的普通 IAM 角色，归属绑定到已验证服务主体及固定的目标应用、部门根。角色治理的实际执行由 `simple-iam-server-starter` 负责；产品资格、期限、离线机器绑定等授权业务不进入本 Core。

`SimpleIamServerConstant` 定义八个接口权限码和五个数据资源：

| 数据资源 | 接口权限码 | 用途 |
|---|---|---|
| `iam:open-role` | `iam:open-role:create:api`、`iam:open-role:read:api` | 创建及查询受委托角色 |
| `iam:open-role-rule` | `iam:open-role-rule:read:api`、`iam:open-role-rule:write:api` | 读取、设置及清除固定应用规则 |
| `iam:open-department-role` | `iam:open-department-role:read:api`、`iam:open-department-role:write:api` | 查询、挂载及撤销部门关系 |
| `iam:open-directory` | `iam:open-directory:read:api` | 读取批准的组织子树及指定成员事实 |
| `iam:open-application` | `iam:open-application:read:api` | 读取目标应用及权限清单 |

新增数据动作 `create` 和维度 `applicationId`、`rootDepartmentId`、`openRoleId`，复用既有 `read`、`write` 动作及 `departmentId` 维度。接口权限与完整数据范围权限必须同时满足，声明常量本身不授予权限。

共享标识包括服务身份来源 `aksk`、UUID（标准唯一标识）语法、`ACTIVE` 有效状态、`DELETED` 删除墓碑状态、派生角色编码、资源定位模板及初始修改版本 `1`。角色条件标记格式为 `"open-role:<UUID>:<revision>"`，供 `If-Match`（修改前核对版本的请求头）使用；它不是组织目录或权限投影的版本。

固定执行上限由常量集中声明，不增加业务启动参数：完整目录最多 4096 个节点、最多 64 层；每个规则权限数组最多 4096 项；JSON 请求体解码前最多 1048576 字节（1 MiB）。

`ErrorCode` 新增 `OPEN_ROLE_001` 至 `OPEN_ROLE_010`，表达请求非法、认证缺失、越权、资源不存在、状态冲突、缺少或失效的修改条件、目录超限、服务不可用及请求体过大。`ServerErrorMessage` 提供对应安全文案，以及内部错误、方法不支持和媒体类型不支持的通用文案。新接口由 HTTP 状态码表达拒绝，不向响应写入内部错误码。

### 受委托角色审计兼容性

IAM Server 1.3.2 的角色创建、修改、删除、规则设置与清除、部门挂载与撤销复用 `AdminActionEvent`，不增加事件类型或修改事件构造方法。新增信息放在既有 `detail` 字段中，以 JSON 携带操作、请求标识、角色、应用、部门、修改版本和实际操作者身份；不携带权限集合、人员展示资料或认证材料。

`simple-iam-server-audit-listener-starter:1.0.0` 按事件类型订阅，可继续将这些事件转换成 `ServerIamAuditRecord` 并原样传递 `detail`，不因本批事件要求而升级监听器。宿主须保证运行时实际使用 `simple-iam-server-core:1.3.2`，不能将旧监听器的依赖声明当成运行时版本证明。

监听器在事务提交后消费成功变更，回滚和无变化不产生成功变更审计。业务处理器 `ServerIamAuditHandler` 可读取 `detail` 实现落库或其他审计去向；默认日志处理器只输出摘要，不打印 `detail`，也不等于持久化存储。该监听链路是提交后的尽力处理，不提供可靠投递或失败重试。

这里的兼容性不代表监听器自动检测或升级 SDK 版本。以后新增独立事件类型或需要转换新的事件字段时，应重新核对消费契约。

### 错误码与常量（constant 包）

`ErrorCode`（内部业务错误码，含主体 ID 生成/分配契约 `SUBJECT_ID_001/002`）、`ServerErrorMessage`（脱敏错误文案，含主体 ID、手机号挑战 HMAC 配置及用户导入校验文案）、`SimpleIamServerConstant`（配置前缀与域常量，含主体 ID 默认策略、手机号挑战 / 冷却 / 三维频控默认值、`PHONE_CHALLENGE_PURPOSE_*` 和用户导入模板契约——取值单一来源于 `simple-iam-core` 的 `SMS_PURPOSE_*`）、`IamAuthorizeContextStatus`、`PermissionType`、`RoleSource`（角色来源：内置 / 应用申报）、`TrustedApplicationClientType`、`TrustedApplicationIcon`、`PortalMenuNodeType`、`PortalPresentationMode`。

### 用户导入模板（constant 包）

用户导入的跨层输入契约由 `SimpleIamServerConstant` 和 `ServerErrorMessage` 统一声明：上传字段名、模板文件名与媒体类型、单工作表约束、表头行、六个字段的固定顺序（`username`、`displayName`、`initialPassword`、`departmentCode`、`phone`、`email`）以及单次最多 500 条非空数据行。解析失败、表头不匹配、部门不可用和单行创建结果等调用方可见文案由 `ServerErrorMessage` 集中维护。

本模块不包含 Excel 解析、HTTP 接口、数据库写入或导入专用审计事件。它们属于 `simple-iam-server-starter`：每一条成功创建复用既有用户创建流程及 `CREATED/USER` 审计事件，不新增并行事件类型。

### 异常（exception 包）

`SimpleIamServerException`（业务异常基类，携带 `ErrorCode`）、`ConfigurationException`（启动配置非法）、`ValidationException`（请求校验失败）。

## 依赖形态

- 业务契约依赖保持 `simple-iam-core:1.1.0`，以 `api` 传递；Spring 能力为 `compileOnly`，不传递给使用方；
- 配套 IAM Server 的固定运行时基线为 Spring Boot `2.7.9`，源码兼容 Java 8；不支持 Spring Boot 3.x；
- 四族审计事件继承 Spring `ApplicationEvent`，消费宿主须提供 Spring Context 运行环境；引入 Core 不会自动注册发布器或监听器。

## 典型使用方

- `simple-iam-server-audit-listener-starter`：订阅四族事件，交由 Handler 决定日志或持久化去向；
- `simple-iam-server-starter`：事件的发布方与错误码/异常的定义使用方。

## 版本记录

- [CHANGELOG.1.3.2.md](CHANGELOG.1.3.2.md)：受委托角色权限、标识、执行上限与错误契约，复用既有审计事件。
- [CHANGELOG.1.3.1.md](CHANGELOG.1.3.1.md)：用户导入模板、字段和校验文案的稳定契约。
- [CHANGELOG.1.3.0.md](CHANGELOG.1.3.0.md)：仪表盘页面权限契约，以及所属人授权内部协作路径的中性化迁移。
- [CHANGELOG.1.2.0.md](CHANGELOG.1.2.0.md)：稳定主体 ID 与手机号挑战相关的错误码、错误文案和默认策略契约。
- [CHANGELOG.1.1.3.md](CHANGELOG.1.1.3.md)：可信应用安全生命周期与 AKSK 所属人授权读取的错误、常量契约。
- [CHANGELOG.1.1.2.md](CHANGELOG.1.1.2.md)：Portal 应用根节点排序错误契约，以及菜单节点和展示模式枚举。
- [CHANGELOG.1.1.1.md](CHANGELOG.1.1.1.md)：Portal 默认入口、全局登录首页与配置并发错误契约。
- [CHANGELOG.1.1.0.md](CHANGELOG.1.1.0.md)：可信应用 Portal 菜单树错误契约与 `folder` 内置图标编码。
