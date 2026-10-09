# CHANGELOG - simple-iam-server-starter 1.3.6

## 发布信息

- 版本：`1.3.6`
- 类型：Feature / 口令最长生存期（等保基线"口令最长生存期"应用层实现，默认关闭）
- 基线版本：`1.3.5`
- 关联制品：`simple-iam-server-core:1.3.4`（两条错误消息常量，消息无硬编码规范）
- 数据库：`iam_user` 加两列（见升级指引）

## 变更明细

### 口令最长生存期策略（默认关闭）

- 新配置 `iam.server.password.max-age-days`（默认 0=关闭）与 `warn-before-days`（默认 7）。
  开启后仅本地账号在登录密码验证通过时判定：`elapsed >= maxAge` 即过期——置须改密标记并复用
  既有受限会话通道（登录成功但只能改密）；临期（剩余 ≤ 提醒窗口）登录响应与 /me 携带剩余天数
  供前端提示；满额零打扰（字段 null）。第 maxAge 天当天即过期，不存在"剩 0 天"态；时间戳在
  未来（时钟回拨/手工改库）按刚设置处理。策略与既有 `must-change-enforcement` 开关同进退。
- 存量行 `password_updated_at` 为 null：策略开启后首次登录回填为登录时刻（激活基线），
  不做迁移脚本、不一刀切全员判过期。改密/管理员重置/忘记密码重置刷新时间戳重新起算。
- 配置热变化全按"下次登录新值判定"：收紧立即生效（超新阈值下次登录强制改密）；放宽/关闭不
  自动解锁已置位用户（置位与配置解耦，防"调大配置=旧密码复活"歧义）。
- `warn-before-days > max-age-days`（仅开启时）启动校验拒绝。

### 须改密原因下传

- `iam_user` 加 `must_change_password_reason` 持久列（FIRST_LOGIN / PASSWORD_RESET /
  PASSWORD_EXPIRED，与须改密标记同写同清）：登录响应顶层与 /me（强制改密 Filter 白名单内，
  受限期可读，改密页刷新恢复文案）携带 `mustChangePasswordReason`；建号=FIRST_LOGIN、
  管理员重置=PASSWORD_RESET（既有置位行为显性化）、生存期过期=PASSWORD_EXPIRED。
- 管理面用户响应（列表与详情共用 DTO）加只读 `passwordUpdatedAt` 与可空 `passwordExpiresInDays`，
  正常/临期/过期由前端按两字段渲染（不设状态枚举）。

### 行为收紧（存量部署须知）

- **三处改密/重置点（自助改密、管理员重置、忘记密码重置）拒绝"新密码=当前密码"**
  （与密码策略校验同位 400 拒绝，时间戳不刷新）——否则生存期策略可被"重复提交旧密码"假换密
  绕过。策略关闭时同样校验（换密码就该换）。升级前请做好用户教育。

## 契约

- `simple-iam-login-web.openapi.yaml`（info.version 1.3.6）：登录响应与 AuthUser 加
  `mustChangePasswordReason` / `passwordExpiresInDays`。
- `simple-iam-admin-web.openapi.yaml`（info.version 1.3.6）：AdminUserResponse 加
  `passwordUpdatedAt` / `passwordExpiresInDays`。
- openapi（`/iam/api/**`）零变化；web 端横幅/文案实装由 login-web/admin-web 后续版本承接。

## 升级指引

```sql
-- docs/schema.sql 已含新列（新初始化库直接建表）；存量库执行一次：
ALTER TABLE iam_user ADD COLUMN must_change_password_reason VARCHAR(32) NULL;
ALTER TABLE iam_user ADD COLUMN password_updated_at TIMESTAMP NULL;
```

- 默认策略关闭，升级后行为与 1.3.5 完全一致；需要等保口径时设 `max-age-days=90` 开启。
- `must-change-enforcement=false` 的部署（如测试环境批量建号）注意：过期强制与首登强制一并不拦。

## 测试

- 单元 7 用例（关闭零行为/激活回填/过期置位/边界当天/临期窗口/未来时间戳/配置校验）+
  集成 3 用例（MockMvc 全链：过期置位→受限 /me 读原因→假换密 400→改密恢复；
  临期携带天数+admin 重置同/异密码语义；未激活首登闭环）。
