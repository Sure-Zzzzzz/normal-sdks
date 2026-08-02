package io.github.surezzzzzz.sdk.mysql.route.test.cases;

import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.context.MySqlRouteContextHolder;
import io.github.surezzzzzz.sdk.mysql.route.datasource.MySqlRoutingDataSource;
import io.github.surezzzzzz.sdk.mysql.route.exception.SimpleMysqlRouteException;
import io.github.surezzzzzz.sdk.mysql.route.registry.SimpleMysqlRouteRegistry;
import io.github.surezzzzzz.sdk.mysql.route.template.MySqlRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Slf4j
public class MySqlRouteTransactionTest {

    @BeforeEach
    public void logTestStart() {
        log.info("开始执行 MySQL Route 事务边界测试");
    }

    @AfterEach
    public void clearTransactionState() {
        MySqlRouteContextHolder.clear();
        TransactionSynchronizationManager.clear();
    }

    @Test
    public void shouldAllowSameDatasourceWithinTransaction() {
        MySqlRoutingDataSource routingDataSource = routingDataSource(mock(DataSource.class), mock(DataSource.class));
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertDoesNotThrow(() -> routingDataSource.bindTransactionDatasource("test-ops-a"));
        assertDoesNotThrow(() -> routingDataSource.bindTransactionDatasource("test-ops-a"));
    }

    @Test
    public void shouldReleaseTransactionTargetAfterCompletion() {
        MySqlRoutingDataSource routingDataSource = routingDataSource(mock(DataSource.class), mock(DataSource.class));
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        routingDataSource.bindTransactionDatasource("test-ops-a");
        for (org.springframework.transaction.support.TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(0);
        }
        TransactionSynchronizationManager.clear();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertDoesNotThrow(() -> routingDataSource.bindTransactionDatasource("test-audit-a"));
    }

    @Test
    public void shouldRejectOuterTransactionTargetSwitchBeforeCallbackConnection() {
        DataSource ops = mock(DataSource.class);
        DataSource audit = mock(DataSource.class);
        MySqlRoutingDataSource routingDataSource = routingDataSource(ops, audit);
        SimpleMysqlRouteRegistry registry = registryWith(ops, audit);
        MySqlRouteTemplate template = template(registry, routingDataSource);
        AtomicBoolean secondCallbackInvoked = new AtomicBoolean(false);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        template.executeOn("test-ops-a", () -> null);
        SimpleMysqlRouteException exception = assertThrows(SimpleMysqlRouteException.class,
                () -> template.executeOn("test-audit-a", () -> {
                    secondCallbackInvoked.set(true);
                    return null;
                }));

        assertEquals(ErrorCode.TRANSACTION_CROSS_DATASOURCE, exception.getCode());
        assertFalse(secondCallbackInvoked.get());
    }

    @Test
    public void shouldRejectTargetSwitchWhenTransactionStartsInsideRouteScope() throws Exception {
        DataSource ops = mock(DataSource.class);
        DataSource audit = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(ops.getConnection()).thenReturn(connection);
        MySqlRoutingDataSource routingDataSource = routingDataSource(ops, audit);

        try (MySqlRouteContextHolder.Scope ignored = MySqlRouteContextHolder.push("test-ops-a");
             Connection ignoredConnection = routingDataSource.getConnection()) {
            TransactionSynchronizationManager.bindResource(routingDataSource,
                    new ConnectionHolder(ignoredConnection));
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.initSynchronization();

            try (MySqlRouteContextHolder.Scope nested = MySqlRouteContextHolder.push("test-audit-a")) {
                SimpleMysqlRouteException exception = assertThrows(SimpleMysqlRouteException.class,
                        () -> routingDataSource.bindTransactionDatasource("test-audit-a"));
                assertEquals(ErrorCode.TRANSACTION_CROSS_DATASOURCE, exception.getCode());
            }
        }
    }

    private MySqlRouteTemplate template(SimpleMysqlRouteRegistry registry, MySqlRoutingDataSource routingDataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(routingDataSource);
        return new MySqlRouteTemplate(registry, routeKey -> routeKey, routingDataSource, jdbcTemplate,
                new NamedParameterJdbcTemplate(jdbcTemplate), null);
    }

    private SimpleMysqlRouteRegistry registryWith(DataSource ops, DataSource audit) {
        SimpleMysqlRouteRegistry registry = mock(SimpleMysqlRouteRegistry.class);
        when(registry.getDataSource("test-ops-a")).thenReturn(ops);
        when(registry.getDataSource("test-audit-a")).thenReturn(audit);
        return registry;
    }

    private MySqlRoutingDataSource routingDataSource(DataSource ops, DataSource audit) {
        Map<Object, Object> targets = new LinkedHashMap<>();
        targets.put("test-ops-a", ops);
        targets.put("test-audit-a", audit);
        return new MySqlRoutingDataSource(targets);
    }
}
