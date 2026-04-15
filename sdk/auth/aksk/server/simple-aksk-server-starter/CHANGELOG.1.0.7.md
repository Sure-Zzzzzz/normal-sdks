# Changelog - v1.0.7

## 发布日期

2026-04-15

## 版本类型

**Patch Release** - Bug 修复

## 变更概述

修复 Admin Token 管理页面"清理过期Token"功能调用错误接口的问题。

---

## Bug 修复

### Admin 清理过期 Token 接口路径错误

**问题描述**：
Admin Token 管理页面（`/admin/token`）的"清理过期Token"按钮调用的是 `/api/token/expired` 接口，该接口需要 `/api/token` scope 权限，但 admin 页面使用 session 认证，没有该 scope，导致调用失败。

**修复内容**：
1. 在 `AdminController` 中新增 admin 专用的清理过期 Token 接口：
   - 路径：`DELETE /admin/token/expired`
   - 使用 session 认证，与其他 admin 接口保持一致
   
2. 修改 `token.html` 中的 `cleanupExpiredTokens()` 函数：
   - 调用路径从 `/api/token/expired` 改为 `/admin/token/expired`

**影响范围**：
仅影响 Admin Token 管理页面的"清理过期Token"按钮功能。

---

## 向后兼容性

✅ **完全向后兼容**，现有配置无需修改。

---

## 相关提交

- 修复 Admin 清理过期 Token 接口路径 ([#token-html-fix](https://github.com/Sure-Zzzzzz/normal-sdks/commit/xxx))
