package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MySQL 元数据目录和受控 Explain 请求校验测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class MysqlMetadataRequestValidatorTest {

    private final MysqlExplainRequestValidator explainValidator = new MysqlExplainRequestValidator(128, 3);
    private final MysqlTableListRequestValidator tableListValidator = new MysqlTableListRequestValidator(32, 20);
    private final MysqlTableColumnsRequestValidator columnsValidator = new MysqlTableColumnsRequestValidator(32);
    private final MysqlTableIndexesRequestValidator indexesValidator = new MysqlTableIndexesRequestValidator(32);

    @Test
    void shouldApplyControlledSelectPolicyToExplain() {
        assertDoesNotThrow(() -> explainValidator.validate(MysqlExplainRequest.builder().datasourceKey("orders")
                .sql("SELECT id, state FROM orders WHERE id = 1 ORDER BY state").build()));

        log.info("受控 Explain 必须与 Select 共用 AST 白名单和固定分类错误");
        assertSqlRejected("EXPLAIN SELECT id FROM orders", "仅支持单条受控 SELECT 查询");
        assertSqlRejected("EXPLAIN ANALYZE SELECT id FROM orders", "仅支持单条受控 SELECT 查询");
        assertSqlRejected("SELECT id FROM orders JOIN customers ON orders.customer_id = customers.id",
                "仅支持无 schema 的单表 SELECT，不支持 CTE、JOIN、分页、分组、锁定、INTO 或其他复杂查询结构");
        assertSqlRejected("SELECT id FROM orders WHERE id IN (SELECT id FROM archived_orders)",
                "WHERE 仅支持当前表字段与字面量的受限条件");
        assertSqlRejected("SELECT id FROM app.orders",
                "仅支持无 schema 的单表 SELECT，不支持 CTE、JOIN、分页、分组、锁定、INTO 或其他复杂查询结构");
        assertSqlRejected("SELECT id FROM orders FOR UPDATE",
                "仅支持无 schema 的单表 SELECT，不支持 CTE、JOIN、分页、分组、锁定、INTO 或其他复杂查询结构");
        assertSqlRejected("SELECT SLEEP(1) FROM orders", "SELECT 投影仅支持显式的当前表字段");
        assertSqlRejected("SELECT id FROM orders; SELECT id FROM archived_orders", "SQL 不允许包含注释或语句分隔符");
        assertSqlRejected("SELECT id FROM orders /* comment */", "SQL 不允许包含注释或语句分隔符");
    }

    @Test
    void shouldAcceptOnlyLiteralTablePrefixAndExactTableIdentifier() {
        assertDoesNotThrow(() -> tableListValidator.validate(MysqlTableListRequest.builder().datasourceKey("orders")
                .prefix("order-2026").size(20).build()));
        assertDoesNotThrow(() -> columnsValidator.validate(MysqlTableColumnsRequest.builder().datasourceKey("orders")
                .table("orders_2026$").build()));
        assertDoesNotThrow(() -> indexesValidator.validate(MysqlTableIndexesRequest.builder().datasourceKey("orders")
                .table("orders_2026$").build()));

        assertPrefixRejected("orders%");
        assertPrefixRejected("orders_");
        assertPrefixRejected("orders\\");
        assertPrefixRejected("orders\nnext");
        assertPrefixRejected("123456789012345678901234567890123");
        assertTableRejected("orders.name");
        assertTableRejected("orders-name");
        assertTableRejected("orders%name");
        assertTableRejected("orders/name");
        assertTableRejected("orders name");
    }

    @Test
    void shouldRejectTableListSizeOutsideServerBound() {
        MysqlTableListRequest request = MysqlTableListRequest.builder().datasourceKey("orders").prefix(null).size(21)
                .build();
        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> tableListValidator.validate(request));
        assertEquals(400, exception.getStatus().value());
        assertEquals("结果数量超出允许范围", exception.getMessage());
    }

    private void assertSqlRejected(String sql, String message) {
        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> explainValidator.validate(MysqlExplainRequest.builder().datasourceKey("orders").sql(sql).build()), sql);
        assertEquals(400, exception.getStatus().value());
        assertEquals(message, exception.getMessage());
    }

    private void assertPrefixRejected(String prefix) {
        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> tableListValidator.validate(MysqlTableListRequest.builder().datasourceKey("orders").prefix(prefix)
                        .size(1).build()), prefix);
        assertEquals(400, exception.getStatus().value());
        assertEquals("表名前缀不符合查询规范", exception.getMessage());
    }

    private void assertTableRejected(String table) {
        MiddlewareOpsException columnsException = assertThrows(MiddlewareOpsException.class,
                () -> columnsValidator.validate(MysqlTableColumnsRequest.builder().datasourceKey("orders").table(table)
                        .build()), table);
        assertEquals(400, columnsException.getStatus().value());
        assertEquals("表名不符合查询规范", columnsException.getMessage());
        MiddlewareOpsException indexesException = assertThrows(MiddlewareOpsException.class,
                () -> indexesValidator.validate(MysqlTableIndexesRequest.builder().datasourceKey("orders").table(table)
                        .build()), table);
        assertEquals(400, indexesException.getStatus().value());
        assertEquals("表名不符合查询规范", indexesException.getMessage());
    }
}
