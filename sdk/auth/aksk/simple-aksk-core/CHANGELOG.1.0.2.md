# Changelog - v1.0.2

## 发布日期

2026-04-09

## 版本类型

**Patch Release** - 功能增强

## 变更概述

`TokenStatus` 枚举新增 `REVOKED` 状态，用于表示被主动撤销的 token。

## 变更内容

### TokenInfo.TokenStatus 新增 REVOKED

| 枚举值 | 说明 |
|--------|------|
| `ACTIVE` | 有效（原有） |
| `EXPIRED` | 已过期（原有） |
| `REVOKED` | 已撤销，主动调用 `/oauth2/revoke` 后的状态（新增） |

## 向后兼容性

✅ **完全向后兼容**，新增枚举值不影响现有逻辑。
