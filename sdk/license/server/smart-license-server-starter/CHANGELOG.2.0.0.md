# smart-license-server-starter 2.0.0

## 修复

- 将数据范围外的授权及其子资源与不存在对象统一返回 404，API 权限拒绝仍返回 403；列表筛选与已授权数据范围在仓储查询中求交。
- 按 Limiter StableCode 规则校验并规范化限频服务编码、资源编码和整数窗口，拒绝非法字符及超出 `long` 范围的数值。
- 补齐签名档案状态筛选和额度释放入口的 OpenAPI 错误响应契约。

## 依赖边界

- Server 编译依赖 License Core 和权限 Core 契约；Spring MVC DATA 装配与 MySQL Route 由宿主显式提供。
