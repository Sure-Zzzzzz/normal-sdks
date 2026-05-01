# 1.1.3 版本变更 (2026-05-01)

## 新增功能

### 1. AKU 归属信息可修改

**背景**：用户级 AKSK（AKU）创建后，`ownerUserId` 和 `ownerUsername` 不可修改，无法应对用户改名或 ID 迁移场景。

**新增**：

- `ClientManagementService.updateOwnerInfo(clientId, ownerUserId, ownerUsername)` — 修改 AKU 归属信息，平台级 AKSK 调用时抛 `VALIDATION_FAILED`
- Admin 详情页新增"编辑归属信息"按钮（仅 AKU 显示），点击弹出 Modal 修改
- `PATCH /api/client/{clientId}` — 新增内网 API，支持更新 enabled / scopes / name / ownerUserId

## Bug 修复

### 1. Admin 详情页编辑操作无响应

**问题**：`updateName()` 和 `updateScopes()` 的 JavaScript 通过 `data.success` 判断成功，但 `ApiResponse` 只有 `message` 字段，导致操作永远被判定为失败，Modal 不关闭、页面不刷新。

**修复**：改用 `response.ok`（HTTP 状态码）判断成功，失败时从响应体取 `message` 展示给用户。同时 `updateOwnerInfo()` 也采用相同模式。

## 改动文件

| 文件 | 说明 |
|------|------|
| `ErrorCode.java` | 新增 `VALIDATION_FAILED = "VALIDATION_001"` |
| `ServerErrorMessage.java` | 新增 `ADMIN_OWNER_INFO_UPDATE_SUCCESS`、`ADMIN_UPDATE_FIELD_REQUIRED` |
| `ClientManagementService.java` | 新增 `updateOwnerInfo()` 接口 |
| `ClientManagementServiceImpl.java` | 实现 `updateOwnerInfo()`，含平台级校验 |
| `UpdateClientRequest.java` | 新增 `ownerUserId`、`ownerUsername` 字段 |
| `AdminController.java` | PATCH 新增 ownerUserId 分支；badRequest 提示改用常量 |
| `ClientManagementController.java` | 新增 `PATCH /api/client/{clientId}` |
| `detail.html` | 新增"编辑归属信息"Modal（AKU only）；修复 JS 成功判断逻辑 |
| `UpdateOwnerInfoTest.java` | 新增 4 个测试：正常修改、置空 username、平台级拒绝、不存在 clientId |

## 版本兼容性

- 向下兼容所有 1.x 版本
- `UpdateClientRequest` 新增字段默认 null，不影响现有调用
- `PATCH /api/client/{clientId}` 为新增端点，不影响已有端点
- 数据库结构无变化
