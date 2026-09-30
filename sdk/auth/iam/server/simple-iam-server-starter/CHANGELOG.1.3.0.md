# CHANGELOG - simple-iam-server-starter 1.3.0

## 发布信息

- 版本：`1.3.0`
- 类型：Minor / 公开主体 ID、手机号与内置应用管理
- 基线版本：`1.2.0`

## 版本定位

对外身份口径从数据库自增 `userId` 全面切换为稳定公开主体 `subjectId`（SPI 生成、写后不改、软删占位不复用），并补齐手机号绑定 / 短信登录 / 忘记密码三件套、Excel 用户导入与 B2M 投递适配器；可信应用新增管理面可调的内置应用标记。

## 主要变更

### 身份主体（subjectId）

- `iam_user` 新增 `subject_id`（唯一索引）与 `phone_bound_at`；存量回填走应用启动幂等回填（`IamSubjectIdBackfillService`），不依赖 SQL 脚本。
- 管理面、开放 API（`/iam/api/users/**`）、用户应用授权、AKSK 所属人授权投影（resolve / 候选目录 / 变更流 payload 与聚合键）统一以 `subjectId` 表达；数字 `userId` 不再出现在任何对外 URI、claim 或事件载荷中。
- token 定制与 verify 响应的 `sub` 切换为 `subjectId`；所属人授权协作调用方以稳定主体 ID 关联所属人。

### 手机号与短信

- 手机号唯一化（`uk_phone`，解绑双置 NULL 即释放）；账户安全支持绑定 / 换绑（验证码校验），管理台展示完整号码，用户侧自我视图与日志脱敏。
- 短信验证码三链路：门户手机号登录、忘记密码（匿名验证手机号后改密）、绑定 / 换绑挑战；服务端同号冷却（默认 60s，挑战响应携带 `resendAfterSeconds` 供前端倒计时单源消费）与有效期（默认 300s）解耦，冷却 ≤ 有效期启动校验兜底；重发即废旧挑战。
- 频控默认阈值可配：号 1h×10 / IP 1h×20 / 全局 1m×100。
- B2M 投递适配器独立模块（`simple-iam-sms-b2m-adapter-starter`）：SPI 插件式装配，未配置不拖依赖；短信文案 `{minutes}` 占位换算由 SPI TTL 单源驱动。

### 用户 Excel 导入

- 管理端新增模板下载与 `multipart/form-data` 导入端点；工作簿只在请求期解析，不保存原始文件。
- 模板固定为 `username`、`displayName`、`initialPassword`、`departmentCode`、`phone`、`email` 六列；单次最多 500 条非空数据行。
- `departmentCode` 留空表示不挂部门；填写时只接受既有且启用的部门，不创建部门。角色不在模板中维护，部门角色继承与既有用户创建规则保持一致。
- 按行调用既有建用户服务；有效行不会因其他行失败回滚，响应返回每行的创建结果与可展示原因。初始密码按既有首登改密规则处理，不在服务端保存上传文件或明文密码。

### 内置应用标记

- `iam_trusted_application` 新增 `built_in` 列（管理面可调）：内置应用禁删除 / 禁停用，下线走关闭门户集成；其 OAuth2 客户端在创建与更新时强制 `require-authorization-consent=false`（免授权确认页）。
- 判定唯一口径 `TrustedApplicationBuiltInResolver`：引导配置清单优先保护指定编码；其余应用以数据库 `built_in` 列为准。清单内应用无论列值一律视为内置，管理面不可摘除（置 false 返回 409）。
- 创建（`POST /iam/admin/trusted-applications`）与更新（`PUT /iam/admin/trusted-applications/{id}`）请求体新增可选 `builtIn`；`1.2.0 -> 1.3.0` 升级脚本补列并将存量 `iam` / `aksk` 置 1。

### 协作边界

- 内部 owner 授权协作全面中性化：路径 `/iam/internal/aksk/**` → `/iam/internal/owner-authorization/**`；变更日志服务族更名 `IamOwnerAuthorizationChangeLog*`；IAM 库表 `iam_aksk_authorization_change` → `iam_owner_authorization_change_log`（`RENAME TABLE` 保留序列，消费方游标无感）；内部 reader 客户端与 scope 同步中性化（`owner-authorization-reader` / `iam:internal:owner-authorization:read|stream`）。
- 引入中立协作协议包 `simple-owner-authorization-collaboration-core`（首发 1.0.0）：reader 出入口的响应模型（`OwnerAuthorizationReadResult` / `OwnerAuthorizationCandidate` / `OwnerAuthorizationChange(Page)`）以协议为单一事实源，删除 IAM 侧手写 DTO；`ownerUsername` 纠名（必填，缺失失败关闭）、`authorization` 统一为 claim 形态（键契约锚定 application-authorization-core ClaimMapper）。
- 修复 OIDC userinfo `sub` 与 id_token 不一致（OpenID Connect Core 5.3.2）：userinfo 主体切 subjectId。

### 其他

- 仪表盘 / 管理面 / Portal 联动适配 `subjectId`；站内信、审计事件与日志埋点同步切换。
- 修复存量测试债：可信应用验收辅助仍以数字 `userId` 拼用户授权 URI（controller 已切 `getBySubjectId` 口径），改回 `subjectId`。

## 依赖变更

| 依赖 | 旧版本 | 新版本 |
| --- | --- | --- |
| `simple-iam-server-core` | `1.2.0` | `1.3.1` |
| `simple-owner-authorization-collaboration-core` | - | `1.0.0`（新增） |
| `org.apache.poi:poi-ooxml` | - | `5.2.2`（新增，仅服务端解析上传工作簿） |
| `simple-iam-sms-b2m-adapter-starter` | - | `1.0.0`（新增，可选） |

## 新增或扩展测试

- subjectId 生成 / 回填 / 全链路口径测试；手机号绑定、短信登录、忘记密码状态机与频控测试；重发即废旧与冷却 / TTL 单源测试。
- Excel 模板、表头校验、空行跳过、行数上限、既有部门解析、逐行部分成功与文件格式拒绝测试。
- 内置应用契约测试：管理面调整标记与摘除保护（配置兜底编码置 false 返回 409）、内置应用客户端强制免授权确认。

## 向后兼容性

- 对外 URI 与令牌 claim 的主体字段由数字 ID 切换为 `subjectId` 字符串：**破坏性变更**。所有调用管理 API、开放 API、用户应用授权或所属人授权协作契约的调用方必须改用 `subjectId`；升级前必须完成 `1.2.0 -> 1.3.0` 迁移并等待回填完成。
- `builtIn` 请求字段可选，缺省行为与 1.2.0 一致（不标记内置）。

## 升级指南

1. 执行 `docs/migration/V1.2.0__to__V1.3.0__subject_and_phone.sql`（subject_id / 手机号唯一化 / built_in 列与存量标记）。
2. 启动应用等待 subjectId 幂等回填完成（日志确认）。
3. AKSK 侧升级至 3.2.1 并按其升级指南执行授权投影切换。
