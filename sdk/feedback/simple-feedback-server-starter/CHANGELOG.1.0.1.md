# simple-feedback-server-starter 1.0.1

## 发布信息

- 版本：`1.0.1`
- 类型：Feature / REST 契约与认证通道补齐
- 发布日期：2026-10-25

## 依赖升级

| 依赖 | 版本 |
|---|---:|
| `simple-feedback-server-core` | 1.0.1 |
| `simple-application-authorization-core` | 1.0.1 |
| `simple-data-permission-core` | 1.2.0 |
| `simple-resource-server-core` | 1.1.1（compileOnly） |
| `simple-mysql-route-starter` | 1.1.1 |
| `s3-client-starter` | 2.1.0 |

## REST 错误契约

- 附件运行配置现在贯穿 controller/service/core：`max-count`、`max-size-bytes`、`max-total-size-bytes` 与 `allowed-content-types` 不再被 core 默认策略二次拒绝；core 默认入口保持兼容。

- 按领域错误码映射 HTTP 状态：字段校验与缺失为 400，重复登记及版本冲突为 409，依赖故障为 503；未知内部码返回安全消息和 500，不再把依赖故障误报为客户端输入错误。
- 错误响应的 `X-Correlation-Id` 与响应体 `correlationId` 一致；反馈端点的 JSON、multipart、请求头和参数解析失败统一为安全的三字段 400 错误体，媒体类型不支持返回三字段 415，未预期的控制器故障返回脱敏 500；领域 4xx/503 映射优先于兜底处理。
- 请求关联 ID 仅接受 1–64 位字母、数字、点、下划线或连字符；非法值由服务端生成，单次请求复用同一 ID，500/503 日志可凭响应 ID 定位。列表页码小于 1 或分页偏移溢出返回 400；提交幂等键为空白或超过 64 字符也在写库前返回 400。
- 处理反馈与更新注册表的 `expectedRowVersion` 必须为非负整数；缺失或负数在进入状态迁移/CAS 前返回 400，避免把请求参数错误误报为并发冲突。
- 控制器无法解析人员身份时返回 401，已认证机器主体或无权限时返回 403，不再把身份缺失误报为依赖故障 503；两种响应均使用安全的三字段错误体。
- 默认资源认证桥未被有效资源认证链保护时，启动期抛反馈配置异常，包含资源服务缺席、关闭、反馈路径未受保护或链顺序不正确；宿主自备身份解析器不受影响。依赖 `simple-feedback-server-core:1.0.1`。
- 两个创建端点的 `Location` 保留实际 API 根路径与 servlet context path；反馈提交指向提交人可读的 `/me/submissions/{id}`，避免普通用户跳到仅管理员可读的详情。
- 创建成功与幂等重放响应现在带回已持久化的附件元数据，提交页可立即展示并下载本次提交的附件。

## 本人端点路径同构（含 1.0.0 端点路径变更）

- 本人反馈三端点路径变更：`GET /my-submissions` → `GET /me/submissions`（详情 `/me/submissions/{id}`、附件下载 `/me/submissions/{id}/attachments/{attachmentId}` 同步），与家族同形态端点（KMS `/api/kms/me/keys`）的 `/me/` 层级口径对齐；`my-` 前缀式不再使用。
- 本人反馈端点族自 1.0.0 首发即存在，本次将路径从 `/my-submissions` 统一为 `/me/submissions`，属于破坏性路径变更：当前已知消费方仅自家 runtime 与 web（均随本版一并切换），无第三方消费方；升级时消费方同步改路径即可，请求/响应结构、权限码（`API_SUBMISSION_MINE`）与归属语义（owner 等值，不走 DATA 范围）均不变。

## 管理列表与我的反馈关键词搜索

- `GET /submissions` 新增可选 `keyword` 参数：命中反馈内容（`content`）、提交人展示名（`submitter_display_name`）或提交人 subjectId（`submitter_subject_id`）三任一，与既有筛选、DATA 范围取交集；展示名为空的行不参与展示名命中，subjectId 维度保证人名改名后仍可按 ID 全量寻址（展示名为随行快照，改名只影响新行，历史行保留当时的名字）。
- `GET /me/submissions` 新增可选 `keyword` 参数：owner 等值内命中反馈内容（本人列表提交人恒为自身，不做名字/ID 命中）。
- 归一口径（两参数一致）：去首尾空白、空白视为未传、超 64 字符截断（与 page size 超限截断同口径，不设独立错误码）。
- LIKE 通配符（`%`/`_`/`!`）按字面匹配（`!` 转义符，避开反斜杠模式歧义）——用户搜「100%」不会命中全表。

## 列表查询优化

- 附件批量取：管理列表与我的反馈列表由逐行点查（一页 20 次 `submission_id` 点查）改为每页一次 `IN` 批量查询后内存分组，组内 `created_time` 升序与原单查口径一致。
- 列表精简投影：列表 SQL 不再拖 `first_response`（TEXT 重放快照，列表永不消费）；详情、重放等全列路径不变。

## SQL 边界声明

- 关键词搜索为 `LIKE '%…%'` 包含匹配（前后通配），当前不使用 B-tree 索引，属于受控全扫；本版本未执行大数据量性能基准。后续如需优化，再单独评估 FULLTEXT（中文需 ngram parser）索引方案。
- 频控双计数窗由 `idx_owner_created(owner, created_time)` 覆盖索引承载（索引内范围扫）；管理列表排序 `created_time DESC` 在无筛选时为 filesort，管理台分页量级可接受，不加写放大索引。

## 新增测试

- `FeedbackRestExceptionHandlerTest` 与 `FeedbackExceptionLogTest`：覆盖请求解析、身份拒绝、领域错误和未知故障的安全响应及日志边界。
- `ProjectionShapeTest`：覆盖管理形态与本人形态的响应字段投影。
- `SubmissionFlowIntegrationTest`、`AttachmentMinioIntegrationTest` 与 `HintIntegrationTest`：覆盖提交幂等、附件、提示降级和外部依赖组合路径。
- `FeedbackCollaborationThreeRightHttpTest`：覆盖人员与服务主体进入同一 API/DATA 三权链的 HTTP 契约。

## 机器主体通道

- 资源认证桥同时接受已验证 HUMAN 与 SERVICE 主体；人员 owner 归一为 `iam:{subjectId}`，服务 owner 归一为 `aksk:{clientId}`。
- 服务主体宿主必须显式组合 `simple-resource-server-starter`、`simple-iam-resource-server-starter` 与 `simple-aksk-resource-server-starter:3.1.0`，并配置已认证的 AKSK introspection（内省）客户端；这些 Provider 不由 Feedback starter 传递。
- SERVICE 请求仍执行 API 与 DATA 三权，不因使用 AKSK 而绕过权限校验。
- 提交与频控诊断日志只记录 `iam`/`aksk` 主体类型前缀，不记录主体标识；两种主体的日志口径保持一致。

## 向后兼容性

- 领域模型、公开错误码和附件默认校验入口保持兼容；1.0.1 新增错误码用于更精确区分请求错误、认证拒绝和依赖故障。
- `/my-submissions` 已统一为 `/me/submissions`，这是已知消费方需要同步的路径变更；其响应字段、权限码和 owner 语义不变。
- 数据库 1.0.0 正式基线已包含 `first_response`，按正式 schema 建库的实例无需结构迁移。

## 接入与升级

- 本版本没有新增 limiter 依赖或配置迁移；需要限流的宿主自行接入 limiter SDK 的策略契约。本人端点路径变更需消费方同步修改，见上文。

## 升级指南

将 `simple-feedback-server-core` 与 `simple-feedback-server-starter` 一并升级到 1.0.1；若使用 IAM 聚形态，可继续使用已发布的 `simple-feedback-iam-adaptor-starter:1.0.0`，并按适配器文档显式提供 IAM 客户端传输件。调用方把本人反馈路径切换为 `/me/submissions`，其余公开操作按 OpenAPI 1.0.1 对接。
