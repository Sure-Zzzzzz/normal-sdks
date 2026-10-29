-- 在已确认的目标数据库、新连接中执行；只修复显式指定订单的履约派生展示态。
-- 执行前按 README 完成只读核对、备份和写入协调。
CREATE TEMPORARY TABLE crm_order_display_repair_scope (
    order_id VARCHAR(64) NOT NULL PRIMARY KEY
) ENGINE = InnoDB;

-- 与只读核对使用完全相同的订单范围；不得用全表 SELECT 构造修复范围。
INSERT INTO crm_order_display_repair_scope (order_id)
VALUES ('order-example-001'), ('order-example-002');

SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
START TRANSACTION;

-- 与回写和重置入口一致：先锁订单，再按稳定顺序锁该单交付项。
SELECT o.order_id
FROM crm_order o
JOIN crm_order_display_repair_scope s ON s.order_id = o.order_id
ORDER BY o.order_id
FOR UPDATE;

SELECT f.fulfillment_id
FROM crm_fulfillment_item f
JOIN crm_order_display_repair_scope s ON s.order_id = f.order_id
ORDER BY f.order_id, f.id
FOR UPDATE;

-- 如范围中有不存在的订单，整批不更新，避免部分目标被悄悄略过。
SELECT COUNT(*) INTO @crm_display_repair_expected
FROM crm_order_display_repair_scope;
SELECT COUNT(*) INTO @crm_display_repair_found
FROM crm_order o
JOIN crm_order_display_repair_scope s ON s.order_id = o.order_id;

-- 锁后冻结本次计算结果；临时表不参与业务存储，也避免一条语句重复引用范围临时表。
CREATE TEMPORARY TABLE crm_order_display_repair_facts AS
SELECT s.order_id,
       COUNT(f.id) AS total_items,
       COALESCE(SUM(f.state = 'completed'), 0) AS completed_items
FROM crm_order_display_repair_scope s
LEFT JOIN crm_fulfillment_item f ON f.order_id = s.order_id
GROUP BY s.order_id;

UPDATE crm_order o
JOIN crm_order_display_repair_facts facts ON facts.order_id = o.order_id
SET o.display_state = CASE
        WHEN facts.total_items > 0 AND facts.completed_items = facts.total_items THEN 'fulfilled'
        WHEN facts.completed_items > 0 THEN 'partially_fulfilled'
        ELSE 'pending_fulfillment'
    END,
    o.aggregate_version = o.aggregate_version + 1,
    o.updated_at = CURRENT_TIMESTAMP(3)
WHERE @crm_display_repair_expected = @crm_display_repair_found
  AND o.display_state IN ('pending_fulfillment', 'partially_fulfilled', 'fulfilled')
  AND o.display_state <> CASE
        WHEN facts.total_items > 0 AND facts.completed_items = facts.total_items THEN 'fulfilled'
        WHEN facts.completed_items > 0 THEN 'partially_fulfilled'
        ELSE 'pending_fulfillment'
    END;

SET @crm_display_repaired_orders = ROW_COUNT();
SELECT @crm_display_repaired_orders AS repaired_orders,
       @crm_display_repair_expected AS expected_orders,
       @crm_display_repair_found AS found_orders;

COMMIT;

DROP TEMPORARY TABLE crm_order_display_repair_facts;
DROP TEMPORARY TABLE crm_order_display_repair_scope;
