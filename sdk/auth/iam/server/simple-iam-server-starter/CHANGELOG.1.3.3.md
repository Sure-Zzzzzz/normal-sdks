# Changelog - simple-iam-server-starter 1.3.3

## 变更概述

修复 admin 手机号登记路径空串撞 `uk_phone` 唯一索引缺陷：管理台"创建成员"表单默认提交空串
手机号，第二个空串用户创建时报 `Duplicate entry '' for key uk_phone`。修复后 admin 手机号按
资料面三态语义处理：`null`=不动/不登记、trim 空串=清空（置 NULL，对齐解绑语义）、非空=规范化
（非法格式 400 拒绝）。属 Bug Fix。

## 依赖升级

无外部依赖变化；本版本单模块发布（helper 在 starter 内，core 无改动）。

## 变更内容

- 问题背景：`iam_user.phone` 为 `VARCHAR(32) DEFAULT NULL` + 唯一索引 `uk_phone`，MySQL 唯一
  索引允许多个 NULL 但空串是实际值。admin web 创建成员表单初始值即为空串（OrganizationsView
  创建抽屉），管理员不填手机号直接提交时，第一个用户落 `''` 成功、第二个必撞唯一索引 500。
- 根因：`IamUserService.createUser` 与 `updateUser` 两处对 phone 原样落库；Excel 批量导入路径
  早有 `emptyToNull` 防护，仅 admin 手工路径漏防。
- 修复方案：`PhoneNormalizationHelper` 新增 `normalizeForProfile`（null 透传、trim 空→NULL、
  非空 normalize），create/update 两处统一调用；"清空手机号"显式映射为置 NULL（与本人解绑的
  双置 NULL 语义一致）。
- 合并规则：更新请求传 null 时手机号保持原值不动（既有语义不变）；非空输入一律走 E.164
  规范化（国内 11 位补 +86），格式非法返回 400——此前非法格式会落库成脏资料，一并收紧。
- 审计：更新请求实际改变手机号时，UPDATED 事件 detail 携带 `phoneChanged=true`（最小定位
  事实，不记录号码本身，与既有 phoneHash 脱敏口径一致）；无变化维持 null。

## 新增测试

- `IamAdminPhoneProfileNormalizationTest`（6）：空串创建落 NULL 且第二个空串用户可创建（防回归
  主断言）、空白串更新置 NULL、带空格裸号规范化、非法格式拒绝、null 更新不动原号、更新裸号
  规范化落库。
- `IamAdminPhoneHttpWalkthroughTest`（1）：HTTP 面走查——admin 登录会话经 `POST /iam/admin/users`
  连建两个空手机号成员（管理台表单默认形态复刻），第二个必须 201，落库核验均为 NULL。

## 验证记录

- 新增 7 用例全部通过；既有全量 72 个测试类（排除依赖外部 AKP 测试资产的
  `IamOpenRoleAkpHttpTest`）分批执行零失败。

## 向后兼容性

- API 形状零变化（无新字段、无路由变更）；行为收紧两处：空串从落库改为置 NULL、非法格式从
  落库改为 400。调用方按原语义提交非空合法号码不受影响。
- 本人对本人手机号的绑定/解绑/登录挑战路径不经过本次改动入口，行为不变。

## 升级指南

- 存量脏数据（修复前落库的空串/纯空白手机号行）需手工清理一次。`phone` 与 `phone_bound_at`
  必须双置 NULL——与解绑语义一致，避免残留"号码已空但绑定时间仍在"的不一致态：

```sql
UPDATE iam_user SET phone = NULL, phone_bound_at = NULL
 WHERE phone IS NOT NULL AND TRIM(phone) = '';
```

- 清理后核查：不应再存在空串/纯空白手机号，也不应存在 `phone IS NULL` 但 `phone_bound_at`
  非空的矛盾行：

```sql
SELECT id, username FROM iam_user
 WHERE (phone IS NOT NULL AND TRIM(phone) = '')
    OR (phone IS NULL AND phone_bound_at IS NOT NULL);
```

- 存量库可能存在的未规范化手机号（裸号/非法格式，修复前经 admin 路径原样落库的资料值）
  不影响 `uk_phone` 正确性，登记方按需人工修正；本次不提供批量改写（避免误改真实号码）。
- 投影表 `iam_application_authorization` 无手机号字段，无需投影清理。
