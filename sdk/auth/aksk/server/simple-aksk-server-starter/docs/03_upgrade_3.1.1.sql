-- =====================================================
-- Simple AKSK Server 3.0.0+ -> 3.1.1 数据库升级脚本
-- 适用前提：已使用 simple-aksk-server-starter 3.0.0 / 3.0.1 / 3.1.0，
-- 存量库缺少 oauth2_authorization.access_token_expires_at 索引。
--
-- 变更内容：为过期 Token 清理（定时任务与手动清理共用的
-- DELETE ... WHERE access_token_expires_at < now）补充索引，
-- 避免清理语句全表扫描。
--
-- 执行要求：
-- 1. 先完成完整数据库备份；
-- 2. 对同一数据库仅执行一次。本脚本不支持重复执行；
-- 3. 大表加索引建议在维护窗口执行（或使用 ONLINE DDL）。
-- =====================================================

SET NAMES utf8mb4;

ALTER TABLE oauth2_authorization
    ADD KEY idx_oauth2_authorization_access_token_expires_at (access_token_expires_at);

SELECT 'AKSK Server 3.0.0+ to 3.1.1 database schema upgrade completed.' AS status;
