# CHANGELOG - simple-iam-server-core 1.3.0

## 发布信息

- 版本：`1.3.0`
- 类型：Minor / 权限契约补全与协作路径中性化
- 基线版本：`1.2.0`

## 版本定位

配合 IAM Server 1.3.0 完成两项契约收口：补全 IAM 仪表盘页面权限编码，并将所属人授权内部协作路径去除具体协作产品名称。

## 主要变更

- 新增 `BUILT_IN_PERMISSION_DASHBOARD_PAGE`（`iam:dashboard:page`），并加入 `BUILT_IN_PAGE_PERMISSION_CODES`。本模块只声明权限契约；IAM Server 1.3.0 的启动引导负责为存量 IAM 管理员回填该权限，并将仪表盘菜单节点绑定到该权限。
- `PATH_INTERNAL_AKSK_API`（`/iam/internal/aksk/**`）更名为 `PATH_INTERNAL_OWNER_AUTHORIZATION_API`（`/iam/internal/owner-authorization/**`）。IAM 提供的是中立的所属人授权协作能力，路径不再携带具体协作方名称。

## 依赖变更

无。

## 新增或扩展测试

本模块的常量仅承载契约，无运行时逻辑；IAM Server 1.3.0 的内部 reader 契约测试覆盖新路径、权限引导与菜单授权链路。

## 向后兼容性

- `PATH_INTERNAL_AKSK_API` 已移除。直接引用该公开常量的调用方重新编译会失败；已编译调用方会继续使用旧路径。旧路径不再由 IAM Server 1.3.0 提供，因此内部 HTTP 调用方必须与服务端同批切换。
- 仪表盘页面权限由 IAM Server 1.3.0 负责补种和回填；单独升级本 core 坐标不会改变运行中服务的权限结果。

## 升级指南

- 与 `simple-iam-server-starter:1.3.0` 及对应协作适配器同批升级和部署；不要仅升级本模块或只部署单侧。
