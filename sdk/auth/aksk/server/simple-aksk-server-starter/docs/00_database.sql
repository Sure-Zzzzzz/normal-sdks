-- =====================================================
-- Simple AKSK Server 数据库创建脚本
-- 版本：1.0.0
-- 数据库：MySQL 5.7+ / MySQL 8.0+
-- =====================================================

-- 设置字符集
SET NAMES utf8mb4;

-- =====================================================
-- 创建数据库
-- =====================================================

CREATE DATABASE IF NOT EXISTS sure_auth_aksk
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- 验证数据库创建
SELECT 'Database created successfully!' AS status;

-- =====================================================
-- 使用说明
-- =====================================================

-- 1. 如果需要使用其他数据库名，请修改上面的 sure_auth_aksk
-- 2. 创建数据库后，请执行 01_schema.sql 创建表结构
-- 3. 执行命令示例：
--    mysql -u root -p < 00_database.sql
