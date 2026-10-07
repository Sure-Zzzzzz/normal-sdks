# smart-kms-server-starter 2.0.2

## 本人公钥

- 新增 `GET /api/kms/me/keys/{keyRef}/public-keys`：可信 HUMAN 人员具备 `kms.read-public-key`、本人归属及合法状态时，无需额外 `READ_PUBLIC_KEY` 使用策略即可读取本人公钥。
- 活动或停用的 SIGN/ES256 密钥返回活动、退役公钥版本，按版本升序；AES、待销毁与已销毁返回 409。缺失版本、材料或异常持久化状态返回 503，不返回部分结果。响应使用 `Cache-Control: no-store`，复用读取公钥审计。
- 默认认证桥从已验证身份记录人员证明，包含 inherited AKU 人员凭据。独立 SERVICE 不能使用新入口；原公钥与密码学接口继续校验精确使用策略，治理权限不能扩大本人归属。

## 销毁明细

- 新增 `GET /api/kms/me/keys/{keyRef}/destruction`，按 `kms.key.read` 固定本人归属，不要求销毁权限或治理 DATA。
- 新增 `GET /api/kms/admin/keys/{keyRef}/destruction`，按 `kms.key.read` 和 `kms-key:read` DATA 确定授权目标归属。
- 两个入口返回同一次已提交读取中的密钥状态、资源版本、业务取消资格，以及每个版本的任务状态、计划时间和实际完成时间；不暴露材料、领取令牌或其他任务内部信息，响应不缓存。
- 取消资格沿用历史首次领取事实：领取后即使释放回 PENDING 或租约过期也不能取消。资格不代替销毁授权，取消命令仍在事务内最终复核。未排程或取消后为空，部分完成保留真实进度，查询失败及不可能状态为 503。

## 治理列表归属

- `GET /api/kms/admin/keys` 与兼容入口 `GET /api/kms/keys` 新增可选 `ownerPrincipalId` 查询参数：精确归属主体只在已验证 DataPlan 范围内收窄，不扩大授权；空白参数视为未筛选。
- `GET /api/kms/destruction-jobs` 同步新增可选 `ownerPrincipalId` 查询参数；自定义销毁查询仓储未覆盖新重载时携带筛选返回 400，其余功能不受影响。
- 管理列表与详情条目新增可空 `ownerDisplayName`；销毁任务分页条目新增 `ownerPrincipalId` 与可空 `ownerDisplayName`；策略列表与创建响应新增可空 `principalDisplayName`。显示名解析失败或宿主未提供目录时字段为空，前端回退展示原始主体标识。
- 新增可选端口 `KmsPrincipalDisplayNameResolver`（含批量默认实现）：宿主可用自有目录实现替换，默认实现始终返回未解析。KMS 不反查身份目录、不解析未验证声明，解析失败不影响业务响应。
- 自定义 `KmsKeyQueryRepository` 未覆盖新增归属筛选重载时，携带 `ownerPrincipalId` 的请求返回 400，不做内存过滤以保分页与总数正确；列表其余功能不受影响。

- 新增 `GET /api/kms/admin/policies`：治理视角跨钥策略分页查询，按 `kms.key.policy` API 与 `kms-key` DATA 范围裁剪；支持密钥别名片段、被授权主体精确、操作精确三类筛选，条目附带密钥别名、归属与被授权主体的可选显示名、策略创建时间。默认实现只对内置同源 JDBC 存储装配，自定义存储须提供 `KmsAdminPolicyQueryRepository` 端口，缺端口时不装配该路由；策略写命令仍在既有按钥入口执行。

## 接入与升级

- 配套 Server Contract `2.0.2` 和 KMS Web `1.0.0`，先升级 Server，再部署 Web。标准 `2.0.1` 自助角色无需增权、重新注册应用或补历史使用策略，无数据库迁移。
- 新本人公钥服务与销毁查询端口可独立替换，保留完整 Engine 让位、无主体解析器不装配路由的边界。默认销毁明细只对内置同源 JDBC 存储装配；自定义存储须提供新增查询端口，缺端口时保留旧路由。
- `KmsRequestContext` 原两参数构造器保留，默认不提供人员证明。自定义解析器须在可信认证器已证明 HUMAN 后使用 `forVerifiedHuman`，不接受请求字段或标识前缀作为证明；旧接口保持兼容。
- 沿用 Core `2.0.0` 与既有 IAM、AKSK、Client，不新增生产依赖。回滚先恢复与旧 Server 兼容的 Web 制品和配置，再回退 Server；无旧 Web 制品时先撤下门户入口，不重建任务或数据库。
