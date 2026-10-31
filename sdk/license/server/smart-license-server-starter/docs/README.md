# License 数据库初始化与迁移

License 当前使用自己的 `license_*` MySQL 事实表，领域为签发方、客户、产品、商业授权、对象资格、签发、吊销及协作台账。它不再按 tenant 组织事实，不读取或共享 IAM、CRM、KMS 的内部表。

## 新库初始化

`01_schema.sql` 定义23张InnoDB、utf8mb4表。脚本开头含DROP，会删除目标表及其数据；只用于明确可丢弃的新库或测试库，不用于已有正式或共享数据的原地升级。

| 表 | 自有事实 |
| --- | --- |
| license_issuer | 签发方编号、业务code、展示资料与版本 |
| license_customer | License客户编号与code；外部CRM引用显式映射 |
| license_product | 签发方归属、产品动作及支持能力 |
| license_grant | 客户×产品×对象×模式的授权、商业期限及模型权益快照 |
| license_limiter_policy_binding | 客户 OpenAPI 限频授权投影到 limiter Management 的本地绑定台账 |
| license_fulfillment_grant | 履约号与授权一对一关联，和草稿同事务保存以去重 |
| license_signing_profile | kid、精确KMS钥版本、公钥快照及ACTIVE/RETIRED历史 |
| license_signing_operation | 冻结输入、签名、幂等关系、执行代数及任务状态 |
| license_subject_assignment | 人员/服务固定资格、分配代数与实名指标 |
| license_activation | 部署对象、设备分量摘要、callerBinding（登记的调用来源）及激活代数；当前设备政策仅支持 `NONE`，非空分量摘要不能单独证明可信设备 |
| license_activation_quota_allocation | 部署激活的 LOCAL 指标分配、数量、授权修订、保留截止和固定窗口锚；签发时只回读持久快照 |
| license_revocation | 不可撤回的业务吊销事实 |
| license_model_version | 条件模型发布快照 |
| license_plan_version | 已发布模型的方案权益预填 |
| license_organization_binding | 客户的IAM组织根引用及本地绑定代数 |
| license_quota_balance | 授权×指标的限额与余额，数据库行锁执行原子扣减 |
| license_quota_reservation | 额度预占/累计扣减的幂等操作台账与有效期围栏 |
| license_integration_plan | 期望规则、人工清单及确认回执，不表示远端AUTO执行 |
| license_role_projection | License 角色投影计划、远端 roleCode 快照与期望修订 |
| license_integration_request | 外部联动请求的幂等、状态与确认记录 |
| license_revocation_scope | issuer/product 吊销导出序号与信任版本水位 |
| license_revocation_package | 吊销包签名任务、序号及 manifest 元数据 |
| license_revocation_package_part | 吊销包的分片 JWS 与摘要 |

执行前核对数据库归属及备份；初始化账号与运行账号分离，运行账号仅具业务需要的DML（读写数据）权限。本目录不保存连接凭据。

## 已有数据的增量迁移

已有 `license_*` 库先停止自己的写入并备份，按实际缺失结构和数据执行增量迁移，不运行全量初始化的DROP段。迁移不能改写已交付文件、冻结header/payload、签名结果或不可撤回吊销。

| 材料 | 用途与边界 |
| --- | --- |
| 02_migrate_from_1_0.md | 旧租户/旧协议退役归档与独立新库部署，没有自动原地转换 |
| 02_signing_operation_activation.sql | 增加activation_id与索引，并从冻结Base64URL载荷回填部署归属；已有列不重复ALTER |
| 03_grant_validity_migration.md | 按实际商业依据修复缺失或矛盾期限，不按迁移当天新造一年 |
| 04_add_fulfillment_grant.sql | 新增自有履约关联去重表；存量歧义或断裂关联须人工核对 |
| 05_add_signing_idempotency_unique.sql | 先回读幂等列定义、重复组和等价完整唯一索引，再人工逐条追加；重复组必须先核对，不删除历史任务 |
| 06_add_role_projection.sql | 先回读计划列、表和索引，再人工逐条追加角色投影事实；创建请求中的 `name` 只是远端展示名，远端回读的 `roleCode` 才是实际编码 |
| 07_add_activation_caller_binding.sql | 先回读两列定义，再按缺失列人工追加部署激活调用来源绑定；存量未绑定的在线判定必须失败关闭 |
| 08_add_signing_tracking_and_legacy_uniques.sql | 先完成列、NULL、重复组、前缀及等价索引只读预检，再逐条人工追加 KMS 调用追踪列、签发凭据唯一键和吊销目标唯一键 |
| 09_add_revocation_and_quota.sql | 补齐 2.0 吊销包水位/分片与额度预占台账；先确认表不存在，再逐表人工执行建表并回读结构 |
| 10_add_activation_quota_allocation.sql | 为已有库补部署激活的 LOCAL 分配事实；存量含 LOCAL 指标的激活必须按真实部署分配、修订和窗口锚逐行补录，不能按权益限额猜造 |
| 11_add_limiter_policy_binding.sql | 补客户 OpenAPI 限频投影台账；执行前核对同客户同资源的历史绑定与远端策略归属，不能把重复目标直接合并 |

JPA的update只能改变部分结构，不能证明这些历史事实已正确回填。固定期限须满足starts_at&lt;service_until≤grace_until；永久期限只保存开始，两个截止为NULL。

## 初始化或迁移后核验

- 23张表及必要列、索引、字符集与实体一致，时间列精度符合DATETIME(6)。
- 签名档案的active_marker唯一键保证每个product/purpose至多一个ACTIVE，RETIRED历史仍保留；kid不得改绑钥资料。
- 签发任务的credential_id和幂等摘要唯一，冻结载荷与activation_id归属一致；状态、执行代数与行版本围栏有效，并保留最近一次KMS调用引用和时间。
- 组织根与外部客户/商品映射只存引用，组织清库/重绑后重新核验；本地binding_generation不冒充远端可信纪元。
- 履约号和grant_id的唯一关系不存在歧义；授权草稿不能被当作已启用或已确认completed。
- 05–08 迁移均为“只读预检 + 人工逐条追加”材料；已有列、表或等价索引不能重复执行，DDL 成功后必须重新回读结构和业务事实。
- 当前DATA按issuerId/customerId/productId/grantId/profileId执行，不遗留tenant范围假设；任务、凭据、资格和激活经grantId归属。
- 完整compact JWS只在独立content交付返回，不用于签发元数据、列表、日志、缓存或事件；历史公钥信任由消费端受控安装，不由文件自授权。
- 在线部署激活必须有已登记且可验证的callerBinding；设备政策为`NONE`时不把componentDigests当作设备证明；未绑定存量激活按失败关闭处理。
- 含 `LOCAL` 指标的部署激活必须持久化覆盖模型声明的全部指标；分配总量不得超过授权权益，固定窗口分配沿用授权 `startsAt` 作为持久锚。普通离线签发从激活回读分配编号、数量、修订、保留截止和窗口锚，不接受请求体临时拼造。
- `license_revocation`的`(target_kind,target_id)`唯一约束只允许一个不可撤回吊销事实；重复组不得通过删除数据解决。

结构核验、业务回读与故障恢复分别验证，不能只看DDL成功或服务健康就称迁移闭环。
