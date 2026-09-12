# simple-iam-server-core

IAM Server 契约层。承载跨模块共享的纯契约：审计与令牌事件、错误码、常量和异常，不含任何技术设施实现。

当前版本为 `1.1.0`。版本沿革见各 `CHANGELOG.*.md`。

本模块是 IAM 契约链路的中间层：`simple-iam-core`（身份协议契约基座）→ **`simple-iam-server-core`（本模块，IAM Server 域契约）** → `simple-iam-server-starter`（应用层：Web API、服务、装配、实体与仓储）。

## 契约内容

### 事件（event 包，供审计监听等消费方订阅）

四族事件统一继承 `AbstractIamEvent`：

- **认证事件**：`AuthenticationEvent`（登录成功/失败、账号锁定等，类型见 `AuthenticationEventType`）；
- **会话事件**：`SessionLifecycleEvent`（建立/刷新/撤销/过期，`SessionEventType` + `SessionEventCause`）；
- **令牌事件**：`TokenIssuedEvent`、`TokenVerifiedEvent`、`TokenRevokedEvent`、`TokenRemovedEvent`、`RefreshTokenReuseDetectedEvent`（`TokenEventType` + `TokenEventCause`）；
- **管理事件**：`AdminActionEvent`（管理端写操作，`AdminActionType` + `AdminSubjectType`）。

另有 `MessageRecipientsResolvedEvent`（站内信收件人解析完成，供通知链路消费）。

### 错误码与常量（constant 包）

`ErrorCode`（对外 API 错误码）、`ServerErrorMessage`（脱敏错误文案）、`SimpleIamServerConstant`（配置前缀与域常量）、`IamAuthorizeContextStatus`、`PermissionType`、`RoleSource`（角色来源：内置 / 应用申报）、`TrustedApplicationClientType`、`TrustedApplicationIcon`。

### 异常（exception 包）

`SimpleIamServerException`（业务异常基类，携带 `ErrorCode`）、`ConfigurationException`（启动配置非法）、`ValidationException`（请求校验失败）。

## 依赖形态

```gradle
dependencies {
    implementation "io.github.sure-zzzzzz:simple-iam-server-core:1.1.0"
}
```

- 编译期仅依赖 `simple-iam-core`，其余 Spring 能力为 `compileOnly`，不传递给使用方；
- 兼容 Spring Boot 2.7.x，源码兼容 Java 8；不支持 Spring Boot 3.x；
- 事件为普通 POJO，消费方自行选择 Spring 事件机制或其他方式接入。

## 典型使用方

- `simple-iam-server-audit-listener-starter`：订阅四族事件并落地审计存储；
- `simple-iam-server-starter`：事件的发布方与错误码/异常的定义使用方。

## 版本记录

- [CHANGELOG.1.1.0.md](CHANGELOG.1.1.0.md)：可信应用 Portal 菜单树错误契约与 `folder` 内置图标编码。
