# Smart KMS Server 升级脚本

| 脚本 | 适用版本 | 说明 |
| --- | --- | --- |
| `V1.0.1__to__V2.0.0__owner_principal_id_rebuild.sql` | 1.0.1 -> 2.0.0 | 归属模型 tenantId -> ownerPrincipalId，语义不可映射，**全量重建**（等价 schema.sql）：升级前导出并放弃存量 smart_kms_* 数据；全新环境直接执行 `docs/schema.sql` |

- 每个脚本同一数据库仅执行一次，执行前完成备份。
- 跨多版本升级按版本顺序逐个执行，不跳版本。
