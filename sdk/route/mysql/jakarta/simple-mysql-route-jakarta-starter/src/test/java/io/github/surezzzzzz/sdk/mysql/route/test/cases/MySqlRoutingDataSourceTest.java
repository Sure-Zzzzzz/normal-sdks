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
import java.util.LinkedHashMap;
import java.util.Map;

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
                singleTarget(target));

        SimpleMysqlRouteException exception = assertThrows(SimpleMysqlRouteException.class,
                routing::getConnection);
        assertEquals(ErrorCode.CONTEXT_INVALID, exception.getCode());
    }

    @Test
    public void shouldRejectCallerProvidedConnectionCredential() throws Exception {
        MySqlRoutingDataSource routing = new MySqlRoutingDataSource(
                singleTarget(mock(DataSource.class)));

        SimpleMysqlRouteException exception = assertThrows(SimpleMysqlRouteException.class,
                () -> routing.getConnection("test-user", "test-password"));
        assertEquals(ErrorCode.USER_CREDENTIAL_CONNECTION_UNSUPPORTED, exception.getCode());
    }

    @Test
    public void shouldRouteConnectionWithContext() throws Exception {
        DataSource target = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(target.getConnection()).thenReturn(connection);
        MySqlRoutingDataSource routing = new MySqlRoutingDataSource(singleTarget(target));

        try (MySqlRouteContextHolder.Scope ignored = MySqlRouteContextHolder.push("test-ops-a");
             Connection ignoredConnection = routing.getConnection()) {
            verify(target).getConnection();
        }
    }

    @Test
    public void shouldRejectInvalidConfiguredPrimaryAtConstruction() {
        DataSource target = mock(DataSource.class);

        SimpleMysqlRouteException blankException = assertThrows(SimpleMysqlRouteException.class,
                () -> new MySqlRoutingDataSource(singleTarget(target), " "));
        assertEquals(ErrorCode.PRIMARY_DATASOURCE_INVALID, blankException.getCode());
        SimpleMysqlRouteException unknownException = assertThrows(SimpleMysqlRouteException.class,
                () -> new MySqlRoutingDataSource(singleTarget(target), "test-missing"));
        assertEquals(ErrorCode.PRIMARY_DATASOURCE_INVALID, unknownException.getCode());
    }

    @Test
    public void shouldRouteWithoutContextToConfiguredPrimaryInsteadOfFirstTarget() throws Exception {
        DataSource firstTarget = mock(DataSource.class);
        DataSource defaultTarget = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(defaultTarget.getConnection()).thenReturn(connection);
        Map<Object, Object> targets = new LinkedHashMap<>();
        targets.put("test-ops-a", firstTarget);
        targets.put("test-audit-a", defaultTarget);
        MySqlRoutingDataSource routing = new MySqlRoutingDataSource(targets, "test-audit-a");

        try (Connection ignored = routing.getConnection()) {
            verify(defaultTarget).getConnection();
            verify(firstTarget, never()).getConnection();
        }
    }

    @Test
    public void shouldPreferExplicitContextAndRejectUnknownContextWithoutDefaultFallback() throws Exception {
        DataSource defaultTarget = mock(DataSource.class);
        DataSource scopedTarget = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(scopedTarget.getConnection()).thenReturn(connection);
        Map<Object, Object> targets = new LinkedHashMap<>();
        targets.put("test-ops-a", defaultTarget);
        targets.put("test-audit-a", scopedTarget);
        MySqlRoutingDataSource routing = new MySqlRoutingDataSource(targets, "test-ops-a");

        try (MySqlRouteContextHolder.Scope ignored = MySqlRouteContextHolder.push("test-audit-a");
             Connection ignoredConnection = routing.getConnection()) {
            verify(scopedTarget).getConnection();
            verify(defaultTarget, never()).getConnection();
        }

        try (MySqlRouteContextHolder.Scope ignored = MySqlRouteContextHolder.push("test-missing")) {
            SimpleMysqlRouteException exception = assertThrows(SimpleMysqlRouteException.class, routing::getConnection);
            assertEquals(ErrorCode.CONTEXT_INVALID, exception.getCode());
        }
    }

    private Map<Object, Object> singleTarget(DataSource target) {
        Map<Object, Object> targets = new LinkedHashMap<>();
        targets.put("test-ops-a", target);
        return targets;
    }
}
