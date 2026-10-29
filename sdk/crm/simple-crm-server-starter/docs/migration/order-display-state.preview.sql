-- 只读核对指定订单的展示态与交付事实。
-- 将下方示例替换为已核验的明确订单标识；禁止删除 WHERE 或扩大为全库范围。
SELECT o.order_id,
       o.display_state AS stored_display_state,
       o.aggregate_version,
       COUNT(f.id) AS total_items,
       COALESCE(SUM(f.state = 'completed'), 0) AS completed_items,
       CASE
           WHEN COUNT(f.id) > 0 AND SUM(f.state = 'completed') = COUNT(f.id) THEN 'fulfilled'
           WHEN COALESCE(SUM(f.state = 'completed'), 0) > 0 THEN 'partially_fulfilled'
           ELSE 'pending_fulfillment'
       END AS derived_display_state
FROM crm_order o
LEFT JOIN crm_fulfillment_item f ON f.order_id = o.order_id
WHERE o.order_id IN ('order-example-001', 'order-example-002')
GROUP BY o.order_id, o.display_state, o.aggregate_version
ORDER BY o.order_id;
