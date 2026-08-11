package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.MysqlSelectRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.MysqlSelectRequestValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MySQL 受控 SELECT AST 白名单测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class MysqlSelectRequestValidatorTest {

    private final MysqlSelectRequestValidator validator = new MysqlSelectRequestValidator(128, 20, 3);

    @Test
    void shouldAllowSingleLocalTableWithLiteralConditions() {
        String sql = "SELECT id, name FROM orders WHERE id BETWEEN 1 AND 10 AND state IN ('NEW', 'DONE') ORDER BY name";
        log.info("受控 SELECT 正向校验通过");
        assertDoesNotThrow(() -> validator.validate(request(sql, 20)));
        assertDoesNotThrow(() -> validator.validate(request("SELECT id FROM orders WHERE id = -1", 20)));
        assertDoesNotThrow(() -> validator.validate(
                request("SELECT cluster_id, database_name FROM test_route_marker", 1)));
    }

    @Test
    void shouldRejectUnsupportedSqlStructures() {
        log.info("受控 SELECT 反向校验：结构、注释、锁、函数和非显式谓词必须拒绝");
        assertRejected("SELECT * FROM orders");
        assertRejected("SELECT id FROM orders; SELECT id FROM other_orders");
        assertRejected("SELECT id FROM orders -- comment");
        assertRejected("WITH source AS (SELECT id FROM orders) SELECT id FROM source");
        assertRejected("SELECT id FROM orders UNION SELECT id FROM archived_orders");
        assertRejected("SELECT orders.id FROM orders JOIN customers ON orders.customer_id = customers.id");
        assertRejected("SELECT id FROM orders WHERE id IN (SELECT id FROM archived_orders)");
        assertRejected("SELECT SLEEP(1) FROM orders");
        assertRejected("SELECT id FROM orders FOR UPDATE");
        assertRejected("SELECT id FROM orders INTO OUTFILE 'blocked.txt'");
        assertRejected("SELECT id FROM information_schema.tables");
        assertRejected("SHOW TABLES");
        assertRejected("EXPLAIN SELECT id FROM orders");
        assertRejected("CALL maintenance()");
        assertRejected("SELECT id FROM orders WHERE 1");
        assertRejected("SELECT id FROM orders WHERE enabled");
        assertRejected("SELECT id FROM orders WHERE id = ?");
        assertRejected("SELECT id FROM orders WHERE id = other_id");
        assertRejected("SELECT id FROM orders WHERE id + 1 = 2");
        assertRejected("SELECT id FROM orders WHERE name REGEXP '.*'");
    }

    @Test
    void shouldRejectExcessiveSizeAndSqlLength() {
        log.info("受控 SELECT 边界校验：size=21，SQL 长度超过 128");
        MiddlewareOpsException sizeException = assertThrows(MiddlewareOpsException.class,
                () -> validator.validate(request("SELECT id FROM orders", 21)));
        assertEquals(400, sizeException.getStatus().value());
        assertEquals("结果数量超出允许范围", sizeException.getMessage());

        assertRejected("SELECT id FROM orders WHERE name = '"
                + "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
                + "'");
    }

    private void assertRejected(String sql) {
        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> validator.validate(request(sql, 20)), sql);
        assertEquals(400, exception.getStatus().value());
        assertEquals("SQL 不符合受控查询规范", exception.getMessage());
    }

    private MysqlSelectRequest request(String sql, int size) {
        return MysqlSelectRequest.builder().datasourceKey("orders").sql(sql).size(size).build();
    }
}
