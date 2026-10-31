# 存量授权期限补齐

新建授权未指定期限时，在创建事务内一次固化固定期限：开始为创建时刻，服务期限为开始后 365 天，商业宽限为服务期限后 7 天。显式固定或永久期限覆盖默认值。在线读取和离线签发只读取已保存的期限，不因请求时间或再次签发而延长。

存量行缺期限、只保存部分期限、固定期限先后矛盾或永久期限携带截止时，在线装载与离线签发返回 HTTP `422`，并说明需要补齐或修正。禁止从迁移执行时刻重新计算一年期限。

## 核验已有数据

先停止 License 写入并备份 `license_grant`。下面的查询只定位需要核实的授权，不改变数据：

```sql
SELECT grant_id, row_version
FROM license_grant
WHERE validity_kind IS NULL
   OR starts_at IS NULL
   OR starts_at <= 0
   OR validity_kind NOT IN ('FIXED', 'PERPETUAL')
   OR (validity_kind = 'FIXED'
       AND (service_until IS NULL OR grace_until IS NULL
            OR service_until <= starts_at OR grace_until < service_until))
   OR (validity_kind = 'PERPETUAL'
       AND (service_until IS NOT NULL OR grace_until IS NOT NULL));
```

逐条根据已确认的商业合同、订单期限或授权审批记录恢复事实；历史凭据只可作为核对证据，不自动决定新的商业期限。无法确认期限的行保持拒绝状态，由管理员确认后补齐。不得按本次读取或迁移时间重新起算，也不得批量宣称缺期限意味着永久授权。

## 固定期限补齐示例

以下编号、时间和版本都是示例。将经过确认的开始、服务截止、宽限截止以及回读的版本号代入，更新必须恰好影响一行；影响零行表示版本已变化，应重新核实，不能去掉版本条件重试。三个时间都是 UTC 秒数，须满足 `starts_at < service_until <= grace_until`。

```sql
UPDATE license_grant
SET validity_kind = 'FIXED',
    starts_at = 1800000000,
    service_until = 1831536000,
    grace_until = 1832140800,
    grant_revision = grant_revision + 1,
    row_version = row_version + 1
WHERE grant_id = 'example-grant'
  AND row_version = 3;
```

## 永久期限补齐示例

仅对已有商业依据明确为永久使用权的授权执行。永久授权只保存开始时刻，服务截止和商业宽限都为 SQL `NULL`。历史接口曾以两个 `0` 表达不存在的截止；核实该授权确为永久后，可将它们规范为 `NULL`。

```sql
UPDATE license_grant
SET validity_kind = 'PERPETUAL',
    starts_at = 1800000000,
    service_until = NULL,
    grace_until = NULL,
    grant_revision = grant_revision + 1,
    row_version = row_version + 1
WHERE grant_id = 'example-perpetual-grant'
  AND row_version = 3;
```

补齐后重新执行核验查询，逐条记录商业依据、操作者和结果，再恢复 License 写入。迁移不修改历史凭据、冻结签名输入、签名结果或吊销状态；既有文件仍保留原期限。新的固定期限凭据以保存的商业宽限截止作为 `exp`（有效截止），永久凭据省略 `exp`，与协议 2 的校验规则一致。
