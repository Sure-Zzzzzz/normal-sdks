package io.github.surezzzzzz.sdk.mysql.route.test.cases;

import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.context.MySqlRouteContextHolder;
import io.github.surezzzzzz.sdk.mysql.route.exception.SimpleMysqlRouteException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MySQL Route 上下文测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class MySqlRouteContextHolderTest {

    @BeforeEach
    public void logTestStart() {
        log.info("开始执行 MySQL Route 上下文测试");
    }

    @AfterEach
    public void clearContext() {
        MySqlRouteContextHolder.clear();
    }

    @Test
    public void shouldRestoreNestedContextAndClearThread() {
        assertNull(MySqlRouteContextHolder.current());
        try (MySqlRouteContextHolder.Scope outer = MySqlRouteContextHolder.push("test-ops-a")) {
            assertEquals("test-ops-a", MySqlRouteContextHolder.current());
            try (MySqlRouteContextHolder.Scope inner = MySqlRouteContextHolder.push("test-audit-a")) {
                assertEquals("test-audit-a", MySqlRouteContextHolder.current());
            }
            assertEquals("test-ops-a", MySqlRouteContextHolder.current());
        }
        assertNull(MySqlRouteContextHolder.current());
    }

    @Test
    public void shouldRejectBlankDatasourceKey() {
        SimpleMysqlRouteException exception = assertThrows(SimpleMysqlRouteException.class,
                () -> MySqlRouteContextHolder.push(" "));
        assertEquals(ErrorCode.CONTEXT_INVALID, exception.getCode());
    }
}
