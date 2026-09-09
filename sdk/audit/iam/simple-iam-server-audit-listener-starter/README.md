# simple-iam-server-audit-listener-starter

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

IAM Server 的可选审计扩展。它监听已提交的 IAM 四族审计事件（Token / 认证 / 会话 / 管理面），归一化为不含 Token 原文的统一审计记录，并分发给业务处理器。

> **独立可选模块**：本模块不被 `simple-iam-server-starter` 反向依赖，需要审计时由应用显式引入。不建审计表、不引 JPA，持久化由业务处理器自行实现；不引入额外运行时依赖，也不共享 IAM 数据库。
>
> 资源侧访问审计（订阅公共资源层 `ResourceAccessEvent`、过滤 IAM 来源）是另一个模块：`simple-iam-resource-audit-listener-starter`。

## 依赖

Gradle：

```gradle
dependencies {
    // 前提：宿主已引入 simple-iam-server-starter，它负责发布四族审计事件
    implementation 'io.github.sure-zzzzzz:simple-iam-server-starter:1.0.0'
    implementation 'io.github.sure-zzzzzz:simple-iam-server-audit-listener-starter:1.0.0'
}
```

Maven：

```xml
<dependency>
    <groupId>io.github.sure-zzzzzz</groupId>
    <artifactId>simple-iam-server-audit-listener-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

本模块生产依赖仅 `simple-iam-server-core:1.0.0`（四族事件契约），Spring 能力全部 `compileOnly` 不传递。

## 快速接入

实现 `ServerIamAuditHandler`，这是唯一必须实现的接口。有了它，监听器才会自动注册：

```java
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MyServerIamAuditHandler implements ServerIamAuditHandler {

    @Override
    public void handle(ServerIamAuditRecord record) {
        log.info("IAM audit: family={}, eventType={}, cause={}, username={}",
                record.getFamily(), record.getEventType(), record.getCause(), record.getUsername());
    }
}
```

支持多个 Handler 同时工作，所有实现 `ServerIamAuditHandler` 的 Bean 都会被调用。

默认日志 Handler 已启用（打印族、动作与主体摘要）。应用自行落库、投递或对接审计系统后，可关闭默认 Handler：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        audit:
          iam:
            server:
              listener:
                handler:
                  log:
                    enabled: false
```

## 事件族与记录

监听器单入口订阅 `AbstractIamEvent` 统一根，按族归一化为 `ServerIamAuditRecord`；`family` 字段决定其余字段的取值范围，族专属字段在其余族上保持 `null`。

### TOKEN（OAuth2 Token 生命周期）

| eventType | 触发场景 |
|---|---|
| `issued` | Token 签发 |
| `revoked` | Token 撤销（OAuth2 撤销端点、应用授权替换 / 撤销、会话吊销连带等，`cause` 区分来源） |
| `removed` | 授权框架内部删除 |
| `verified` | 资源验证端点受控验证（追加 `active` 终审结论与 `verificationClientId`） |
| `reuse-detected` | Refresh Token 复用检测（追加 `familyId`，该族已整族吊销） |

`cause` 表达业务来源：`unspecified` / `resource-verification` / `application-authorization-replaced` / `application-authorization-revoked` / `session-revoked` / `refresh-token-reuse`。

### AUTHENTICATION（认证）

| eventType | 触发场景 |
|---|---|
| `login-succeeded` | 登录成功 |
| `login-failed` | 登录失败（`errorCode` 非空；无法定位用户时 `userId` 为 `null`） |
| `logout` | 登出 |
| `account-locked` | 账号锁定（`failureCount` 为触发锁定时的累计失败次数） |

### SESSION（IAM 会话生命周期）

| eventType | 触发场景 |
|---|---|
| `created` | 会话建立 |
| `revoked` | 会话撤销（`cause`：`logout` / `relogin-kick` / `user-lifecycle`；批量吊销带 `revokedCount`） |

### ADMIN_ACTION（管理面操作）

eventType 即管理动作编码：`created` / `updated` / `deleted` / `enabled` / `disabled` / `unlocked` / `secret-rotated` / `assigned` / `unassigned` / `bound` / `unbound` / `password-reset` / `password-changed` / `recovered` / `granted` / `replaced` / `revoked`；`subjectType` 标识目标实体（`user` / `role` / `permission` / `department` / `user-group` / `message` / `application` / `oauth-client` / `verification-client` / `application-authorization` / `external-binding` / `password-reset-token` / `bootstrap-admin`）；系统自动操作（无认证上下文）时 `operator` 为 `null`。

完整枚举以 `simple-iam-server-core` 的事件契约为准。

## 审计记录字段

| 字段 | 说明 |
|---|---|
| `family` | 事件族：TOKEN / AUTHENTICATION / SESSION / ADMIN_ACTION |
| `eventType` | 族内动作类型编码 |
| `cause` | 触发来源编码；无来源表达时为 `null` |
| `eventTime` | 事件对象创建时间 |
| `userId` / `username` | 用户标识（统一字符串化）与用户名 |
| `clientId` / `clientType` | OAuth 客户端标识与类型；仅 TOKEN 族 |
| `provider` | 登录方式编码（local-password / 外部 providerCode）；仅 AUTHENTICATION 族 |
| `ip` / `userAgent` | 发起端地址与 UA；仅 AUTHENTICATION 族 |
| `errorCode` | 登录失败错误码；仅 login-failed |
| `failureCount` | 触发锁定的累计失败次数；仅 account-locked |
| `sessionId` / `revokedCount` | 会话标识与批量吊销数量；仅 SESSION 族 |
| `subjectType` / `subjectId` / `subjectName` | 管理操作目标实体；仅 ADMIN_ACTION 族 |
| `operator` | 操作人；系统自动操作为 `null` |
| `detail` | 管理操作补充信息（已脱敏自由文本） |
| `scopes` / `issuedAt` / `expiresAt` | 授权范围与 Token 生命周期时间；仅 TOKEN 族 |
| `familyId` | 被整族吊销的 Refresh Token 族 ID；仅 reuse-detected |
| `active` / `verificationClientId` | 受控验证终审结论与发起方；仅 verified |

`ServerIamAuditRecord` 结构上没有 tokenValue 字段——事件内的 Token 原文在转换时即丢弃，Handler 不得推导、记录或重建该值。密码、Secret、外部凭据原文不进入事件（发布侧保证），自然不进入记录。

## 提交后语义与可靠性边界

监听器使用 `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`：

- 业务操作处在事务中时（管理面 service），仅在事务成功提交后调用 Handler；回滚的业务变更不产生审计记录。
- 非事务发布方（登录链路 controller、support 门面）经 fallbackExecution 兼容消费。
- Handler 按顺序独立调用；某个 Handler 失败只记错误日志，不阻断已提交的业务操作或后续 Handler。
- 监听器是提交后的尽力消费，不提供可靠消息投递或重试承诺。需要持久化重试、Outbox 或 MQ 保障时，由具体 Handler 自行实现。

## Spring Boot 兼容性

基于 Spring Boot 2.7.9 / javax 基线开发，与 `simple-iam-server-starter` 版本矩阵一致；源码兼容 Java 8。
