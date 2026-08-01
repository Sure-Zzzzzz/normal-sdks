package io.github.surezzzzzz.sdk.mysql.route.test.cases;

import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.context.MySqlRouteContextHolder;
import io.github.surezzzzzz.sdk.mysql.route.datasource.MySqlRoutingDataSource;
import io.github.surezzzzzz.sdk.mysql.route.exception.SimpleMysqlRouteException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * MySQL Route 路由 DataSource 测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class MySqlRoutingDataSourceTest {

    @BeforeEach
    public void logTestStart() {
        log.info("开始执行 MySQL Route 路由数据源测试");
    }

    @AfterEach
    public void clearContext() {
        MySqlRouteContextHolder.clear();
    }

    @Test
    public void shouldRejectConnectionWithoutContext() throws Exception {
        DataSource target = mock(DataSource.class);
        MySqlRoutingDataSource routing = new MySqlRoutingDataSource(
                Collections.<Object, Object>singletonMap("test-ops-a", target));

        SimpleMysqlRouteException exception = assertThrows(SimpleMysqlRouteException.class,
                routing::getConnection);
        assertEquals(ErrorCode.CONTEXT_INVALID, exception.getCode());
    }

    @Test
    public void shouldRejectCallerProvidedConnectionCredential() throws Exception {
        MySqlRoutingDataSource routing = new MySqlRoutingDataSource(
                Collections.<Object, Object>singletonMap("test-ops-a", mock(DataSource.class)));

        SimpleMysqlRouteException exception = assertThrows(SimpleMysqlRouteException.class,
                () -> routing.getConnection("test-user", "test-password"));
        assertEquals(ErrorCode.USER_CREDENTIAL_CONNECTION_UNSUPPORTED, exception.getCode());
    }

    @Test
    public void shouldRouteConnectionWithContext() throws Exception {
        DataSource target = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(target.getConnection()).thenReturn(connection);
        MySqlRoutingDataSource routing = new MySqlRoutingDataSource(
                Collections.<Object, Object>singletonMap("test-ops-a", target));

        try (MySqlRouteContextHolder.Scope ignored = MySqlRouteContextHolder.push("test-ops-a");
             Connection ignoredConnection = routing.getConnection()) {
            verify(target).getConnection();
        }
    }
}
