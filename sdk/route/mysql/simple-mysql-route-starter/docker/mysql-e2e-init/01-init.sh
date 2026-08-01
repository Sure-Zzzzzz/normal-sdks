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

INSERT INTO test_ops.test_route_marker (cluster_id, database_name)
VALUES ('${E2E_CLUSTER_ID}', 'test_ops');

INSERT INTO test_audit.test_route_marker (cluster_id, database_name)
VALUES ('${E2E_CLUSTER_ID}', 'test_audit');

CREATE USER IF NOT EXISTS '${E2E_MYSQL_USERNAME}'@'%' IDENTIFIED BY '${E2E_MYSQL_PASSWORD}';
GRANT SELECT ON test_ops.* TO '${E2E_MYSQL_USERNAME}'@'%';
GRANT SELECT ON test_audit.* TO '${E2E_MYSQL_USERNAME}'@'%';
FLUSH PRIVILEGES;
SQL
