-- IAM 与 AKSK 协作 Demo 业务库
-- 首次运行前手工执行：mysql -uroot -p < docs/schema.sql
CREATE DATABASE IF NOT EXISTS iam_aksk_resource
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE iam_aksk_resource;

-- 订单表：tenant_id 与 department_id 是 DATA 授权维度列
CREATE TABLE IF NOT EXISTS orders (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id     VARCHAR(64)  NOT NULL,
    department_id VARCHAR(64)  NOT NULL,
    order_no      VARCHAR(64)  NOT NULL,
    amount        DECIMAL(18, 2) NOT NULL,
    status        VARCHAR(32)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_no (order_no),
    KEY idx_orders_tenant (tenant_id),
    KEY idx_orders_department (department_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
