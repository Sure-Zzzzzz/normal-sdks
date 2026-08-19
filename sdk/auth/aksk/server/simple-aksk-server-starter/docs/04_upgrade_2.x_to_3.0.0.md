# Simple AKSK Server Starter 2.x 升级至 3.0.0

3.0.0 新增可信应用授权投影。OAuth `scope` 和调用方 `security_context` 不会自动转换为 API permission 或 DATA grant；升级后必须由管理员显式完成授权配置与准入。

## 升级前门禁

- 备份整个目标数据库，并验证备份可恢复。
- 停止所有 2.x Server 写入，包含 Token 签发、撤销、Client 管理和管理 REST。
- 确认生产环境没有 `aksk_application_authorization` 表。
- 准备 3.0.0 的 Redis、JWE 密钥材料与部署配置；不从本机测试配置复制认证材料。

## 数据库迁移

对同一数据库仅执行一次 `02_upgrade_3.0.0.sql`。该脚本保留 `oauth2_registered_client` 与 `oauth2_authorization`，仅创建应用授权投影和查询索引；不支持重复执行或回滚。

不要在保留数据的数据库执行 `01_schema_3.0.0.sql`。

## 启动后的 Client 迁移

对每个既存 Client 顺序执行：

1. 撤销其全部 2.x 存量活跃 Token。
2. 配置完整应用授权：应用编码、角色、页面权限、精确 API permission、`DataGrantDocument`、清单版本和摘要。
3. 显式准入。
4. 只为该 Client 新签发 Token，并检查内省 active 状态以及授权快照。
5. 在资源服务验证 API 与 DATA 权限都符合预期。

在首次 3.0 准入前处理历史活跃 Token 是升级操作的必要步骤，防止旧 Token 在当前投影被重建授权。进入 3.0 后，服务端对**完整替换**和**撤销**应用授权会在同一事务中撤销该 Client 的活跃 Token：旧 Token 必须保持 inactive，只有后续新签发的 Token 才能携带新授权版本。

## 运行时行为变化

- 新 Client 不自动拥有应用授权。
- 投影缺失、未准入、禁用或已撤销时，签发和内省 fail-close。
- 机器管理替换或撤销除应用授权资源 permission/DATA 范围外，还需 `akskToken:update` 和受影响 Token 的 DATA 范围。
- 完整替换不允许变更既有 Client 的应用编码。
- 并发替换发生 JPA 乐观锁冲突时，机器 REST 返回 HTTP 409。

## 回退边界

数据库脚本不提供自动下行迁移。只有在维护窗口、恢复升级前已验证的数据库备份，并将应用二进制与配置一并恢复到兼容的 2.x 状态时，才能回退。已按 3.0 运行产生的授权投影和撤销状态不得被当作 2.x 可识别权限数据。

## 完成标准

完成 [发布验收清单](06_release_acceptance_3.0.0.md) 中的升级、Token A/B、并发、管理权限和资源 DATA 验证后，才允许恢复写入流量。
