# MySQL 工具链

MySQL 接入底座：数据源路由与事务边界治理。被 [KMS](../business/密钥管理-KMS.md)、[AKSK Server](../business/认证与授权-AKSK.md)、limiter-management 等业务模块广泛依赖。

## 连接与路由（底层）

| SDK | javax | jakarta | 说明 | 文档 |
|-----|-------|---------|------|------|
| [simple-mysql-route-starter](../../sdk/route/mysql/simple-mysql-route-starter) | 1.1.1 | 1.0.0 | 数据源路由（Route-owned datasource、显式 primary、单事务 datasource 边界） | [README](../../sdk/route/mysql/simple-mysql-route-starter/README.md) |
| [simple-mysql-route-jakarta-starter](../../sdk/route/mysql/jakarta/simple-mysql-route-jakarta-starter) | — | 1.0.0 | Jakarta 对等件 | [README](../../sdk/route/mysql/jakarta/simple-mysql-route-jakarta-starter/README.md) |

## 依赖关系

- KMS Server（mysql-route 1.1.1）、AKSK Server 3.x（mysql+redis 双路由）、Kafka outbox 落库、limiter-management 持久化均经本路由接入
- 提供方自证：本模块自己的测试配置也经 `mysql.route.enable=true` 接管模式接入，而不是绕开路由直连（组件自身先当使用方，验证真实接入形态）

## 兼容矩阵（jakarta 线）

| Spring Boot | JDK 17 | JDK 21 |
|-------------|--------|--------|
| 3.4.2（默认基线） | 通过（61 tests） | 通过 |
| 3.3.13 | 通过 | 通过 |
| 3.2.12 | 通过 | 通过 |


## 版本映射（jakarta 线）

| mysql-route-jakarta | 内部依赖 | 说明 |
|---------------------|----------|------|
| 1.0.0 | 无（底层） | 与 javax 线 1.1.1 平行，坐标带 `-jakarta-` 段 |
