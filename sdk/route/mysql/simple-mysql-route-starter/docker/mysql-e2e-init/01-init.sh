#!/bin/sh
set -eu

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS test_audit;

CREATE TABLE IF NOT EXISTS test_ops.test_route_marker (
    cluster_id VARCHAR(32) NOT NULL,
    database_name VARCHAR(64) NOT NULL,
    PRIMARY KEY (cluster_id, database_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS test_audit.test_route_marker (
    cluster_id VARCHAR(32) NOT NULL,
    database_name VARCHAR(64) NOT NULL,
    PRIMARY KEY (cluster_id, database_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS test_ops.test_route_crud (
    record_id VARCHAR(64) NOT NULL,
    content VARCHAR(128) NOT NULL,
    PRIMARY KEY (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS test_audit.test_route_crud (
    record_id VARCHAR(64) NOT NULL,
    content VARCHAR(128) NOT NULL,
    PRIMARY KEY (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO test_ops.test_route_marker (cluster_id, database_name)
VALUES ('${E2E_CLUSTER_ID}', 'test_ops');

INSERT INTO test_audit.test_route_marker (cluster_id, database_name)
VALUES ('${E2E_CLUSTER_ID}', 'test_audit');

CREATE USER IF NOT EXISTS '${E2E_OPS_USERNAME}'@'%' IDENTIFIED BY '${E2E_OPS_PASSWORD}';
CREATE USER IF NOT EXISTS '${E2E_AUDIT_USERNAME}'@'%' IDENTIFIED BY '${E2E_AUDIT_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON test_ops.test_route_crud TO '${E2E_OPS_USERNAME}'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON test_audit.test_route_crud TO '${E2E_AUDIT_USERNAME}'@'%';
GRANT SELECT ON test_ops.test_route_marker TO '${E2E_OPS_USERNAME}'@'%';
GRANT SELECT ON test_audit.test_route_marker TO '${E2E_AUDIT_USERNAME}'@'%';
FLUSH PRIVILEGES;
SQL
