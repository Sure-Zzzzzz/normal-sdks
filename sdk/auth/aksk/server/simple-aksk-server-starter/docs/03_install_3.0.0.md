# Simple AKSK Server Starter 3.0.0 新装手册

本手册仅适用于新部署或确认可重建 AKSK 数据的环境。保留历史 Client 与 Token 的环境必须使用 [2.x 升级手册](04_upgrade_2.x_to_3.0.0.md)。

## 准备基础设施

- Spring Boot 2.7.x 应用，使用 Java 11。
- 可用的 MySQL 5.7+ 或 MySQL 8.0+ 数据库。
- 可用的 Redis；3.0.0 不支持无 Redis 运行模式。
- 独立保存的 JWE 密钥材料和管理员口令。不要把私钥、加密密钥、Client Secret 或管理员口令提交到仓库或写入日志。

## 添加依赖

在 Core `3.0.0` 与 Server Core `3.0.1` 坐标均已由 Central 实证可解析后使用精确版本：

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-server-starter:3.0.0'
    implementation 'io.github.sure-zzzzzz:simple-aksk-core:3.0.0'
    implementation 'io.github.sure-zzzzzz:simple-aksk-server-core:3.0.1'
    implementation 'io.github.sure-zzzzzz:simple-application-authorization-core:1.0.0'
    implementation 'io.github.sure-zzzzzz:simple-data-permission-core:1.1.0'

    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'mysql:mysql-connector-java:8.0.33'
}
```

发布收口前的源码联调可保留工程内的两个 `project(...)` 上游依赖；不要在同一依赖图中混用 project 依赖和已发布坐标。

## 初始化数据

1. 创建目标数据库并选择它。
2. 执行 `01_schema_3.0.0.sql`。
3. 确认三个表存在，且 `aksk_application_authorization` 含 `authorization_version` 与 `lock_version`。

`01_schema_3.0.0.sql` 含 `DROP TABLE IF EXISTS`，不得用于保留现有 AKSK 数据的环境。

## 最小运行配置

配置数据源、Redis、JWE 签名与加密材料，并确保以下三个 `me` 使用同一集群标识：

- `io.github.surezzzzzz.sdk.auth.aksk.server.redis.token.me`
- `io.github.surezzzzzz.sdk.limiter.redis.smart.me`
- `io.github.surezzzzzz.sdk.cache.me`

跨实例标识不一致会隔离 Token 缓存、失效广播和 OAuth2 限流命名空间。完整配置项见根 [README](../README.md)。

## 建立第一个可签发 Client

1. 通过 Admin 创建平台级或用户级 Client，并在部署侧安全交付一次性 Client Secret。
2. 在“应用授权管理”中为该 Client 完整配置应用编码、角色、页面权限、精确 API permission、`DataGrantDocument`、清单版本和摘要。
3. 显式准入 Client。
4. 通过 `client_credentials` 请求 Token，并以受保护的 `/oauth2/introspect` 验证其 active 状态。
5. 资源服务只以 `aksk_authorization` 执行 API 与 DATA 校验；`scope` 和 `security_context` 不能扩大授权。

没有启用、已准入且未撤销的应用授权投影时，Server 必须拒绝签发有效 Token。

## 新装验收

执行 [发布验收清单](06_release_acceptance_3.0.0.md) 的“新装自闭环”项目后再接入业务资源服务。
