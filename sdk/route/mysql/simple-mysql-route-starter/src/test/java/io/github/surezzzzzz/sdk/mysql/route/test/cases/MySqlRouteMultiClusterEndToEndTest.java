package io.github.surezzzzzz.sdk.mysql.route.test.cases;

import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.exception.SimpleMysqlRouteException;
import io.github.surezzzzzz.sdk.mysql.route.model.MySqlRouteTarget;
import io.github.surezzzzzz.sdk.mysql.route.registry.SimpleMysqlRouteRegistry;
import io.github.surezzzzzz.sdk.mysql.route.template.MySqlRouteTemplate;
import io.github.surezzzzzz.sdk.mysql.route.test.SimpleMysqlRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MySQL Route 真实双实例端到端测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleMysqlRouteTestApplication.class)
public class MySqlRouteMultiClusterEndToEndTest {

    private static final String MYSQL57_OPS = "test-mysql57-ops";
    private static final String MYSQL57_AUDIT = "test-mysql57-audit";
    private static final String MYSQL84_OPS = "test-mysql84-ops";
    private static final String MYSQL84_AUDIT = "test-mysql84-audit";
    private static final String TEST_OPS = "test_ops";
    private static final String TEST_AUDIT = "test_audit";

    @Autowired
    private MySqlRouteTemplate template;

    @Autowired
    private SimpleMysqlRouteRegistry registry;

    @BeforeEach
    public void logTestStart() {
        log.info("开始执行 MySQL Route 真实双实例端到端测试");
    }

    @Test
    public void shouldVerifyAllFixedTargetsAcrossPhysicalMysqlClusters() {
        Set<String> expectedKeys = new LinkedHashSet<>(Arrays.asList(
                MYSQL57_OPS, MYSQL57_AUDIT, MYSQL84_OPS, MYSQL84_AUDIT));
        assertEquals(expectedKeys, registry.getDatasourceKeys());

        assertTarget(MYSQL57_OPS, "test_order", "mysql57", TEST_OPS, "mysql57", "5.7.");
        assertTarget(MYSQL57_AUDIT, "test_user", "mysql57", TEST_AUDIT, "mysql57", "5.7.");
        assertTarget(MYSQL84_OPS, "test_wildcard", "mysql84", TEST_OPS, "mysql84", "8.4.");
        assertTarget(MYSQL84_AUDIT, "test_extra_field", "mysql84", TEST_AUDIT, "mysql84", "8.4.");
    }

    @Test
    public void shouldKeepFixedDatabaseAndPhysicalClusterIsolation() {
        TargetIdentity mysql57Ops = identity(MYSQL57_OPS);
        TargetIdentity mysql57Audit = identity(MYSQL57_AUDIT);
        TargetIdentity mysql84Ops = identity(MYSQL84_OPS);

        assertEquals(mysql57Ops.clusterId, mysql57Audit.clusterId);
        assertNotEquals(mysql57Ops.database, mysql57Audit.database);
        assertEquals(mysql57Ops.database, mysql84Ops.database);
        assertNotEquals(mysql57Ops.clusterId, mysql84Ops.clusterId);
        assertNotEquals(mysql57Ops.version, mysql84Ops.version);
    }

    @Test
    public void shouldRejectRouteFallbackAndUnknownTargetsBeforeCallback() {
        SimpleMysqlRouteException contextException = assertThrows(SimpleMysqlRouteException.class, () -> {
            try {
                template.routingDataSource().getConnection();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
        assertEquals(ErrorCode.CONTEXT_INVALID, contextException.getCode());

        AtomicBoolean unknownTargetCallbackInvoked = new AtomicBoolean(false);
        SimpleMysqlRouteException targetException =
                assertThrows(SimpleMysqlRouteException.class,
                        () -> template.executeOn("test-missing", () -> {
                            unknownTargetCallbackInvoked.set(true);
                            return null;
                        }));
        assertEquals(ErrorCode.DATASOURCE_NOT_FOUND, targetException.getCode());
        assertFalse(unknownTargetCallbackInvoked.get());

        AtomicBoolean unknownRouteCallbackInvoked = new AtomicBoolean(false);
        SimpleMysqlRouteException routeException =
                assertThrows(SimpleMysqlRouteException.class,
                        () -> template.execute("test_missing", () -> {
                            unknownRouteCallbackInvoked.set(true);
                            return null;
                        }));
        assertEquals(ErrorCode.ROUTE_NOT_FOUND, routeException.getCode());
        assertFalse(unknownRouteCallbackInvoked.get());
    }

    @Test
    public void shouldRejectSameInstanceAndCrossInstanceSwitchInsideTransaction() {
        TransactionTemplate transactionTemplate = transactionTemplate();

        assertTransactionSwitchRejected(transactionTemplate, MYSQL57_OPS, TEST_OPS, MYSQL57_AUDIT);
        assertTransactionSwitchRejected(transactionTemplate, MYSQL57_OPS, TEST_OPS, MYSQL84_OPS);
    }

    @Test
    public void shouldRejectSwitchAfterTransactionAcquiresFirstRouteConnection() {
        TransactionTemplate transactionTemplate = transactionTemplate();

        template.executeOn(MYSQL57_OPS, () -> transactionTemplate.execute(status -> {
            assertEquals(TEST_OPS, currentDatabase());
            SimpleMysqlRouteException exception = assertThrows(SimpleMysqlRouteException.class,
                    () -> template.executeOn(MYSQL84_OPS, () -> null));
            assertEquals(ErrorCode.TRANSACTION_CROSS_DATASOURCE, exception.getCode());
            return null;
        }));
    }

    @Test
    public void shouldReleaseTransactionBindingAfterCommitAndRollback() {
        TransactionTemplate transactionTemplate = transactionTemplate();

        template.executeOn(MYSQL57_OPS, () -> transactionTemplate.execute(status -> {
            assertEquals(TEST_OPS, currentDatabase());
            return null;
        }));
        template.executeOn(MYSQL84_AUDIT, () -> transactionTemplate.execute(status -> {
            assertEquals(TEST_AUDIT, currentDatabase());
            return null;
        }));

        template.executeOn(MYSQL57_AUDIT, () -> transactionTemplate.execute(status -> {
            assertEquals(TEST_AUDIT, currentDatabase());
            status.setRollbackOnly();
            return null;
        }));
        template.executeOn(MYSQL84_OPS, () -> transactionTemplate.execute(status -> {
            assertEquals(TEST_OPS, currentDatabase());
            return null;
        }));
    }

    @Test
    public void shouldRejectWritesWithReadOnlyE2eCredential() {
        DataAccessException exception = assertThrows(DataAccessException.class, () -> template.executeOn(MYSQL57_OPS,
                () -> template.routingJdbcTemplate().update(
                        "INSERT INTO test_route_marker (cluster_id, database_name) VALUES (?, ?)",
                        "test_write", "test_write")));
        assertTrue(exception.getCause() instanceof SQLException);
        assertEquals(1142, ((SQLException) exception.getCause()).getErrorCode());
    }

    private void assertTarget(String datasourceKey, String routeKey, String expectedClusterKey,
                              String expectedDatabase, String expectedClusterId, String expectedVersionPrefix) {
        MySqlRouteTarget target = registry.getTarget(datasourceKey);
        assertEquals(datasourceKey, target.getDatasourceKey());
        assertEquals(expectedClusterKey, target.getClusterKey());
        assertEquals(expectedDatabase, target.getDatabase());

        TargetIdentity explicitIdentity = identity(datasourceKey);
        assertEquals(expectedDatabase, explicitIdentity.database);
        assertEquals(expectedClusterId, explicitIdentity.clusterId);
        assertTrue(explicitIdentity.version.startsWith(expectedVersionPrefix));

        TargetIdentity routeIdentity = template.execute(routeKey, () -> new TargetIdentity(
                currentDatabase(), currentVersion(), currentClusterId()));
        assertEquals(explicitIdentity, routeIdentity);
        String namedParameterDatabase = template.executeOn(datasourceKey,
                () -> template.namedParameterJdbcTemplate().queryForObject("SELECT DATABASE()",
                        new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(), String.class));
        assertEquals(expectedDatabase, namedParameterDatabase);
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new DataSourceTransactionManager(template.routingDataSource()));
    }

    private void assertTransactionSwitchRejected(TransactionTemplate transactionTemplate,
                                                 String firstDatasourceKey, String expectedDatabase,
                                                 String secondDatasourceKey) {
        AtomicBoolean secondCallbackInvoked = new AtomicBoolean(false);
        template.executeOn(firstDatasourceKey, () -> transactionTemplate.execute(status -> {
            assertEquals(expectedDatabase, currentDatabase());
            SimpleMysqlRouteException exception =
                    assertThrows(SimpleMysqlRouteException.class,
                            () -> template.executeOn(secondDatasourceKey, () -> {
                                secondCallbackInvoked.set(true);
                                return null;
                            }));
            assertEquals(ErrorCode.TRANSACTION_CROSS_DATASOURCE, exception.getCode());
            return null;
        }));
        assertFalse(secondCallbackInvoked.get());
    }

    private TargetIdentity identity(String datasourceKey) {
        return template.executeOn(datasourceKey,
                () -> new TargetIdentity(currentDatabase(), currentVersion(), currentClusterId()));
    }

    private String currentDatabase() {
        return template.routingJdbcTemplate().queryForObject("SELECT DATABASE()", String.class);
    }

    private String currentVersion() {
        return template.routingJdbcTemplate().queryForObject("SELECT VERSION()", String.class);
    }

    private String currentClusterId() {
        return template.routingJdbcTemplate().queryForObject(
                "SELECT cluster_id FROM test_route_marker", String.class);
    }

    private static final class TargetIdentity {
        private final String database;
        private final String version;
        private final String clusterId;

        private TargetIdentity(String database, String version, String clusterId) {
            this.database = database;
            this.version = version;
            this.clusterId = clusterId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TargetIdentity)) {
                return false;
            }
            TargetIdentity that = (TargetIdentity) other;
            return database.equals(that.database) && version.equals(that.version) && clusterId.equals(that.clusterId);
        }

        @Override
        public int hashCode() {
            int result = database.hashCode();
            result = 31 * result + version.hashCode();
            result = 31 * result + clusterId.hashCode();
            return result;
        }
    }
}
