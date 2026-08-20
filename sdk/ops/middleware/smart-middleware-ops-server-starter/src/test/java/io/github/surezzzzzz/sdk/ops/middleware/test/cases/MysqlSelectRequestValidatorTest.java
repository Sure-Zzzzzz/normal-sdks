package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlSelectRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.query.MysqlSelectRequestValidator;
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
    void shouldDescribeInputAndStatementRejections() {
        log.info("受控 SELECT 为输入与语句类型返回固定分类错误");
        assertRejected(null, "SQL 不能为空");
        assertRejected("SELECT id FROM orders;", "SQL 不允许包含注释或语句分隔符");
        assertRejected("SELECT id FROM orders -- comment", "SQL 不允许包含注释或语句分隔符");
        assertRejected("SELECT FROM", "SQL 语法无法解析");
        assertRejected("SHOW TABLES", "仅支持单条受控 SELECT 查询");
        assertRejected("EXPLAIN SELECT id FROM orders", "仅支持单条受控 SELECT 查询");
        assertRejected("CALL maintenance()", "仅支持单条受控 SELECT 查询");
    }

    @Test
    void shouldDescribeUnsupportedStructures() {
        log.info("受控 SELECT 为复杂结构返回固定分类错误");
        assertRejected("WITH source AS (SELECT id FROM orders) SELECT id FROM source",
                "仅支持无 schema 的单表 SELECT，不支持 CTE、JOIN、分页、分组、锁定、INTO 或其他复杂查询结构");
        assertRejected("SELECT id FROM orders JOIN customers ON orders.customer_id = customers.id",
                "仅支持无 schema 的单表 SELECT，不支持 CTE、JOIN、分页、分组、锁定、INTO 或其他复杂查询结构");
        assertRejected("SELECT id FROM orders LIMIT 1",
                "仅支持无 schema 的单表 SELECT，不支持 CTE、JOIN、分页、分组、锁定、INTO 或其他复杂查询结构");
        assertRejected("SELECT id FROM orders FOR UPDATE",
                "仅支持无 schema 的单表 SELECT，不支持 CTE、JOIN、分页、分组、锁定、INTO 或其他复杂查询结构");
        assertRejected("SELECT id FROM orders INTO OUTFILE 'blocked.txt'", "SQL 语法无法解析");
        assertRejected("SELECT id FROM information_schema.tables",
                "仅支持无 schema 的单表 SELECT，不支持 CTE、JOIN、分页、分组、锁定、INTO 或其他复杂查询结构");
    }

    @Test
    void shouldDescribeProjectionConditionAndOrderRejections() {
        log.info("受控 SELECT 为投影、条件和排序返回固定分类错误");
        assertRejected("SELECT * FROM orders", "SELECT 投影仅支持显式的当前表字段");
        assertRejected("SELECT id, name, state, created_at FROM orders", "SELECT 投影不能为空且列数不能超过允许范围");
        assertRejected("SELECT SLEEP(1) FROM orders", "SELECT 投影仅支持显式的当前表字段");
        assertRejected("SELECT id FROM orders WHERE id IN (SELECT id FROM archived_orders)",
                "WHERE 仅支持当前表字段与字面量的受限条件");
        assertRejected("SELECT id FROM orders WHERE id = ?", "WHERE 仅支持当前表字段与字面量的受限条件");
        assertRejected("SELECT id FROM orders WHERE id = other_id", "WHERE 仅支持当前表字段与字面量的受限条件");
        assertRejected("SELECT id FROM orders WHERE id + 1 = 2", "WHERE 仅支持当前表字段与字面量的受限条件");
        assertRejected("SELECT id FROM orders WHERE name REGEXP '.*'", "WHERE 仅支持当前表字段与字面量的受限条件");
        assertRejected("SELECT id FROM orders ORDER BY SLEEP(1)", "ORDER BY 仅支持当前表字段");
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
                + "'", "SQL 长度超出允许范围");
    }

    private void assertRejected(String sql, String message) {
        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> validator.validate(request(sql, 20)), sql);
        assertEquals(400, exception.getStatus().value());
        assertEquals(message, exception.getMessage());
    }

    private MysqlSelectRequest request(String sql, int size) {
        return MysqlSelectRequest.builder().datasourceKey("orders").sql(sql).size(size).build();
    }
}
