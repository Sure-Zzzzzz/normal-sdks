# Simple Ticket Server Starter

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

统一工单平台的宿主装配：三面 Controller（portal/manage/openapi）、MySQL 持久化、S3 附件、SLA 扫描、通知分发与 IAM/AKSK 双 Provider 组合。Spring Boot 2.7.9 / Java 8，portal 形态（无 console）。

## 快速上手

```gradle
implementation 'io.github.sure-zzzzzz:simple-ticket-server-starter:1.0.0'
```

宿主义务件（starter 的 Spring 依赖为 compileOnly，README 部署方义务与 feedback 同构）：

- `spring-boot-starter-web/jdbc/security/validation`
- 公共资源层三件：`simple-resource-server-starter` + `simple-iam-resource-server-starter` + `simple-aksk-resource-server-starter`（openapi 面需要；页面对面只需前两者）
- 可选聚件：`s3-client-starter`（附件，宿主提供的 `compileOnly` 依赖）、`smart-redis-limiter-starter`（突发限流）

`s3-client-starter` 由宿主按需提供，starter 不会把 S3 传输实现传递给业务方；只有在宿主同时提供该依赖并设置 `attachment.enabled=true`、`attachment.bucket` 后，附件预签名能力才会启用。

## HTTP 面（基路径 `/api/ticket`）

| 面 | 认证 | 端点概要 |
| --- | --- | --- |
| portal `/portal/**` | 人员 PKCE 令牌 | my-tickets / types / tickets CRUD / timeline / comments / supply / confirm / reject / withdraw / evaluation / attachments presign+download |
| manage `/manage/**` | 人员令牌+处理组 | queue / all-tickets / detail / timeline / claim / assign / transfer / request-info / resolve / comments(internal) / attachment download / groups / types / report |
| openapi `/openapi/**` | AKSK Bearer | tickets（`Idempotency-Key` 必填）/ list / get / append / close（均限本凭据创建的单） |

三权：PAGE 由 IAM 裁菜单（五页码 `ticket.page.*`）；API 码逐端点 `@RequireApiPermission`（13 枚 `ticket:*`，openapi 复用 `create/read/comment/close`）；DATA 进 SQL（本人 `ownerSubjectId` EQ / 组内 `assigneeGroup` IN / `ticket` 资源 `read + all=true` 全量读及分派/转派数据身份）。PAGE 只控制页面可见性，不放大 DATA 范围；类型/组/报表写操作仍要求各自精确 API 码。越权访问已存在单返回 404（掩蔽）；可见但角色不符 403；状态冲突 409；校验 400。

机器开单幂等域为 `(source, creator_credential_id, idempotency_key)`，请求正文规范化后只保存 `request_fingerprint`（SHA-256，不保存请求原文）。同凭据同键同体重放返回 200，首次创建返回 201；同键异体返回 409。不同凭据可以复用同一幂等键。

页面开单同样要求 `Idempotency-Key` 请求头，幂等域为 `(owner_subject_id, idempotency_key)`，记录保存在 `ticket_portal_idempotency`。同一提单人同键同体重放返回 200，首次创建返回 201，同键异体返回 409；不同提单人可以使用同一键。页面幂等与 OpenAPI 幂等相互隔离。

写操作支持可选 `If-Match` 请求头作为乐观并发版本屏障，允许 `3`、`"3"` 或 `W/"3"` 形式；与当前工单 `version` 不一致返回 409，省略时保持兼容的服务层语义。

## 配置键（前缀 `io.github.surezzzzzz.sdk.ticket.server`，默认值见 `SimpleTicketServerProperties`）

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `enable` | true | 总开关 |
| `api-base-path` | /api/ticket | REST 根路径 |
| `attachment.enabled` | false | 附件能力（S3 聚件开关） |
| `attachment.bucket` | — | 附件桶（S3 适配必填） |
| `attachment.max-size-mb` / `max-count-per-ticket` / `allowed-types` | 20 / 10 / 白名单 | 附件限制 |
| `notification.enabled` | false | 站内信出口（IAM adaptor 聚形态） |
| `sla.scan-fixed-rate-seconds` | 60 | SLA 扫描周期 |
| `ticket.confirm-timeout-hours` | 168 | 确认超时（全局统一） |
| `ticket.subject-max-length` / `description-max-length` | 120 / 8000 | 内容上限 |

凭据与外部地址 env 注入不进仓（机密永不入库）。

## 底座装配（宿主自引，聚散边界）

- **散形态（默认）**：纯 MySQL 单件可跑——无附件（Noop FileProvider 明确报错）、无站内信（Noop 日志降级）、无限流、显示 subjectId
- **聚形态**：宿主加引 `s3-client-starter`（附件）、`smart-redis-limiter-starter`（限流，`enable` 显式 true）、`simple-ticket-iam-adaptor-starter`（站内信+姓名解析）；全部 `@ConditionalOn*` 让位，缺席即回退 Noop
- 资源提供方三件（公共 Resource + IAM/AKSK Provider）由宿主自引，`protected-paths` 含 `/api/ticket/**`

## 存储与运维

- MySQL 九表（七张业务表 + `ticket_no_sequence` 序列表 + `ticket_portal_idempotency` 页面幂等表），DDL 交付：`docs/schema.sql`（家族同构）；序列原子递增发单号，页面幂等按提单人隔离。`schema.sql` 只适用于全新空库；现有正式库不会被 `CREATE TABLE IF NOT EXISTS` 自动升级，OpenAPI 的 `request_fingerprint`/三列唯一键和 Portal 幂等表必须按迁移脚本执行并先核对存量冲突。
- SLA 扫描：原子 UPDATE 置位（无行锁预选）；确认超时是唯一系统转移（自动 CLOSED，不邀请评价）
- 附件：S3 预签名直传；孤儿 24h 清、终态 180 天清（配置）；工单实体永不物理删

## 测试

`gradle test`（日常排除 `collaboration` 标签）；DB 真件集成测试（`TicketCommandServiceIntegrationTest`）从测试资源的 `application.yml` 读取隔离库地址和用户名，从被忽略的 `application-local.yml` 只读取密码；缺私有配置时明确失败。测试配置模板为 `application-local.yml.example`。覆盖 core 状态机、SLA、模板校验、DB 六步闭环、越权、幂等、冲突和队列排序。

## 可信应用接入

IAM 登记、三权清单、宿主网关与验收见工单 Web 仓 `docs/TRUSTED_APPLICATION_ONBOARDING.md`（simple-ticket-admin-web）。
