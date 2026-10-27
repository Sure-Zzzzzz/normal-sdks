-- 已有库增量迁移：附件对象键必须唯一，避免同一对象被重复挂靠。
-- 执行前请先清理历史重复 object_key；生产变更由数据库变更流程执行。
ALTER TABLE ticket_attachment
    ADD UNIQUE KEY uk_attachment_object (object_key);
