# 1.1.1

## 版本性质

1.1.1 是 1.1.0 的边界修复版本。Route 不再根据 datasource username 的字符串名称推断 MySQL 权限或拒绝启动。

## 修复内容

1.1.0 曾将 `root`、`admin` 及其大小写变体视为高风险连接账号并拒绝配置。账号名称不能可靠反映 MySQL 实际授权，也不属于 Route 的路由、生命周期或事务职责。

1.1.1 移除该用户名黑名单。Route 继续校验 datasource 的 `url`、`username`、`password`、`driver-class-name` 是否完整，以及 primary datasource、路由规则和 Hikari 配置等自身约束；实际账号能否连接及拥有何种权限，仍由 MySQL 授权、主机限制、认证策略和部署安全流程决定。

## 向后兼容性与升级

- 原有合法配置无需修改。
- 此前仅因 username 名称为 `root`、`admin` 或其大小写变体而被 Route 阻断的配置，升级后可正常通过 Route 配置校验。
- 独立最小权限账号仍是生产部署建议，但不再是 Starter 的运行时限制。

## 验证范围

- 配置校验覆盖空白 username 仍失败，以及 `root`、`ROOT`、`admin`、`ADMIN` 在其他字段完整时均通过。
- 保持 Route-owned datasource、Hikari、路由规则、Spring JDBC、Named JDBC、MyBatis 和单事务 datasource 边界的既有验证范围。
