# CHANGELOG - simple-iam-server-core 1.3.2

## 发布信息

- 版本：`1.3.2`
- 类型：Patch / 受委托角色契约补充
- 基线版本：`1.3.1`

## 版本定位

为 IAM Server 的服务主体受委托角色治理补齐共享常量。受委托角色是外部服务在批准范围内创建和维护的普通 IAM 角色，绑定已验证主体、固定应用和部门根。本版本只在 `SimpleIamServerConstant`、`ErrorCode`、`ServerErrorMessage` 三个既有类增加常量，不修改身份协议、既有公开方法、异常类型、事件或枚举。

本 Core 不实现 HTTP 接口、权限注册、数据库迁移、权限投影重算或商业授权业务；这些常量由配套 `simple-iam-server-starter:1.3.2` 消费。

## 主要变更

- **接口权限**：新增八个 API（接口操作权限）码，覆盖角色创建与查询、固定应用规则读取与写入、部门关系读取与写入、组织目录读取和目标应用读取。
- **数据范围**：新增五个 DATA（数据范围权限）资源：`iam:open-role`、`iam:open-role-rule`、`iam:open-department-role`、`iam:open-directory`、`iam:open-application`；新增 `create` 动作及 `applicationId`、`rootDepartmentId`、`openRoleId` 维度，复用既有 `read`、`write` 动作和 `departmentId` 维度。声明常量不授予权限。
- **身份与标识**：新增已验证服务来源 `aksk`、主体归属字段名、UUID（标准唯一标识）语法、派生角色编码、资源定位模板、摘要语法、`ACTIVE` 有效状态及 `DELETED` 删除墓碑状态。
- **修改条件**：新增初始修改版本 `1`、角色条件标记 `"open-role:<UUID>:<revision>"` 的格式及语法，供 `If-Match`（修改前核对版本的请求头）使用；该标记不代表组织目录或权限投影版本。
- **固定执行上限**：完整目录最多 4096 个节点、最多 64 层，每个规则权限数组最多 4096 项，JSON 请求体解码前最多 1048576 字节（1 MiB）；同时集中声明字段长度及读取缓冲区常量，不新增业务启动参数。
- **错误与安全文案**：新增 `OPEN_ROLE_001` 至 `OPEN_ROLE_010` 内部错误码及对应文案，覆盖参数非法、认证缺失、越权、资源不存在、状态冲突、修改条件缺失或失效、目录超限、服务不可用和请求体过大；补充内部错误、方法不支持和媒体类型不支持的安全文案。新接口以 HTTP 状态码表达拒绝，不向响应输出内部错误码。

## 依赖变更

无。`simple-iam-core` 保持 `1.1.0`，不要求身份协议基座或 AKSK 模块升级，不新增生产依赖。

## 审计兼容性

受委托角色的创建、修改、删除、规则设置与清除、部门挂载与撤销复用既有 `AdminActionEvent`。事件新增业务信息放在原有 `detail` 字段中，以 JSON 记录操作、请求标识、角色、应用、部门、修改版本和实际操作者身份，不携带权限集合、人员展示资料或认证材料。

`simple-iam-server-audit-listener-starter:1.0.0` 可继续按事件类型消费，并将 `detail` 原样传给 `ServerIamAuditHandler`；本批不要求监听模块发新版本。宿主运行时须实际解析到 `simple-iam-server-core:1.3.2`，旧监听器不会自动检测或升级依赖版本。

监听器在事务提交后消费，回滚与无变化不留下成功变更审计。默认处理器只输出日志摘要，不打印 `detail`；持久化由业务 Handler 负责。提交后消费不等于可靠投递，不提供失败重试。以后新增独立事件类型或需要转换新字段时，必须重新核对消费契约。

## 测试边界

本 Core 未新增测试类或修改既有测试，`IamEventContractTest` 继续覆盖原有事件字段及历史常量契约，不将其视为新增开放角色常量的专项覆盖。

配套 Starter 的 `IamOpenRoleProtocolTest`、`IamOpenRoleContractTest`、`IamOpenRoleHttpTest` 覆盖开放角色协议、权限声明及 HTTP 行为；其中旧发布审计监听器组合测试断言提交后接收、操作者信息、敏感字段排除、回滚不消费及幂等操作不重复审计。监听器兼容验证不等于部署环境的持久化审计验收。

## 向后兼容性

相对 `1.3.1` 仅增加常量，未删除或修改既有公开类型、方法、常量和值。原有事件消费者不因本批事件模型而要求修改；需要利用受委托角色新增审计信息的业务 Handler 应读取 `detail`。

## 升级指南

- 直接消费新常量的扩展模块使用 `simple-iam-server-core:1.3.2`；部署角色治理接口使用配套 `simple-iam-server-starter:1.3.2`。仅升级 Core 不会自动暴露接口或执行数据库迁移。
- 宿主同时引入旧审计监听器时，核验最终运行时依赖为 Core `1.3.2`，排除强制版本或依赖管理将其降回旧版本的情况。
- 沿用 `simple-iam-server-audit-listener-starter:1.0.0`；需要保存新增审计信息时，在业务 `ServerIamAuditHandler` 中消费并保存 `detail`，不要将默认摘要日志视为持久化审计记录。
