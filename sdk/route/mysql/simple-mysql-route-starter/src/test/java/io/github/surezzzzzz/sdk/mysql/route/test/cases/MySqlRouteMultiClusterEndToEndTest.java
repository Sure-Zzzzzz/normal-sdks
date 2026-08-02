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

    private static final String MYSQL57_OPS = "mysql57.ops";
    private static final String MYSQL57_AUDIT = "mysql57.audit";
    private static final String MYSQL84_OPS = "mysql84.ops";
    private static final String MYSQL84_AUDIT = "mysql84.audit";
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

        assertTarget(MYSQL57_OPS, "test_order", "mysql57", TEST_OPS, "mysql57", "5.7.",
                "mysql57_ops_route");
        assertTarget(MYSQL57_AUDIT, "test_user", "mysql57", TEST_AUDIT, "mysql57", "5.7.",
                "mysql57_audit_route");
        assertTarget(MYSQL84_OPS, "test_wildcard", "mysql84", TEST_OPS, "mysql84", "8.4.",
                "mysql84_ops_route");
        assertTarget(MYSQL84_AUDIT, "test_extra_field", "mysql84", TEST_AUDIT, "mysql84", "8.4.",
                "mysql84_audit_route");
    }

    @Test
    public void shouldKeepFixedDatabaseAndPhysicalClusterIsolation() {
        TargetIdentity mysql57Ops = identity(MYSQL57_OPS);
        TargetIdentity mysql57Audit = identity(MYSQL57_AUDIT);
        TargetIdentity mysql84Ops = identity(MYSQL84_OPS);

        assertEquals(mysql57Ops.clusterId, mysql57Audit.clusterId);
        assertNotEquals(mysql57Ops.database, mysql57Audit.database);
        assertNotEquals(mysql57Ops.currentUser, mysql57Audit.currentUser);
        assertEquals(mysql57Ops.database, mysql84Ops.database);
        assertNotEquals(mysql57Ops.clusterId, mysql84Ops.clusterId);
        assertNotEquals(mysql57Ops.version, mysql84Ops.version);
    }

    @Test
    public void shouldRejectSameClusterCrossDatabaseAccessWithTargetCredential() {
        DataAccessException exception = assertThrows(DataAccessException.class,
                () -> template.executeOn(MYSQL57_AUDIT, () -> template.routingJdbcTemplate().queryForObject(
                        "SELECT cluster_id FROM test_ops.test_route_marker", String.class)));
        assertTrue(exception.getCause() instanceof SQLException);
        assertEquals(1142, ((SQLException) exception.getCause()).getErrorCode());
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
    public void shouldExecuteCompleteCrudOnEveryFixedTarget() {
        assertCompleteCrud(MYSQL57_OPS, "test_order", "mysql57-ops");
        assertCompleteCrud(MYSQL57_AUDIT, "test_user", "mysql57-audit");
        assertCompleteCrud(MYSQL84_OPS, "test_wildcard", "mysql84-ops");
        assertCompleteCrud(MYSQL84_AUDIT, "test_extra_field", "mysql84-audit");
    }

    private void assertCompleteCrud(String datasourceKey, String routeKey, String recordId) {
        String initialContent = recordId + "-initial";
        String updatedContent = recordId + "-updated";
        template.executeOn(datasourceKey,
                () -> template.routingJdbcTemplate().update(
                        "DELETE FROM test_route_crud WHERE record_id = ?", recordId));
        try {
            assertEquals(1, template.execute(routeKey,
                    () -> template.routingJdbcTemplate().update(
                            "INSERT INTO test_route_crud (record_id, content) VALUES (?, ?)",
                            recordId, initialContent)));
            assertEquals(initialContent, template.executeOn(datasourceKey,
                    () -> template.routingJdbcTemplate().queryForObject(
                            "SELECT content FROM test_route_crud WHERE record_id = ?", String.class, recordId)));
            assertEquals(1, template.execute(routeKey,
                    () -> template.routingJdbcTemplate().update(
                            "UPDATE test_route_crud SET content = ? WHERE record_id = ?", updatedContent, recordId)));
            assertEquals(updatedContent, template.executeOn(datasourceKey,
                    () -> template.routingJdbcTemplate().queryForObject(
                            "SELECT content FROM test_route_crud WHERE record_id = ?", String.class, recordId)));
            assertEquals(1, template.execute(routeKey,
                    () -> template.routingJdbcTemplate().update(
                            "DELETE FROM test_route_crud WHERE record_id = ?", recordId)));
            assertEquals(Integer.valueOf(0), template.executeOn(datasourceKey,
                    () -> template.routingJdbcTemplate().queryForObject(
                            "SELECT COUNT(*) FROM test_route_crud WHERE record_id = ?", Integer.class, recordId)));
        } finally {
            template.executeOn(datasourceKey,
                    () -> template.routingJdbcTemplate().update(
                            "DELETE FROM test_route_crud WHERE record_id = ?", recordId));
        }
    }

    private void assertTarget(String datasourceKey, String routeKey, String expectedClusterKey,
                              String expectedDatabase, String expectedClusterId, String expectedVersionPrefix,
                              String expectedUsername) {
        MySqlRouteTarget target = registry.getTarget(datasourceKey);
        assertEquals(datasourceKey, target.getDatasourceKey());
        assertEquals(expectedClusterKey, target.getClusterKey());
        assertEquals(expectedDatabase, target.getDatabase());

        TargetIdentity explicitIdentity = identity(datasourceKey);
        assertEquals(expectedDatabase, explicitIdentity.database);
        assertEquals(expectedClusterId, explicitIdentity.clusterId);
        assertTrue(explicitIdentity.version.startsWith(expectedVersionPrefix));
        assertTrue(explicitIdentity.currentUser.startsWith(expectedUsername + "@"));

        TargetIdentity routeIdentity = template.execute(routeKey, () -> new TargetIdentity(
                currentDatabase(), currentVersion(), currentClusterId(), currentUser()));
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
                () -> new TargetIdentity(currentDatabase(), currentVersion(), currentClusterId(), currentUser()));
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

    private String currentUser() {
        return template.routingJdbcTemplate().queryForObject("SELECT CURRENT_USER()", String.class);
    }

    private static final class TargetIdentity {
        private final String database;
        private final String version;
        private final String clusterId;
        private final String currentUser;

        private TargetIdentity(String database, String version, String clusterId, String currentUser) {
            this.database = database;
            this.version = version;
            this.clusterId = clusterId;
            this.currentUser = currentUser;
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
            return database.equals(that.database) && version.equals(that.version) && clusterId.equals(that.clusterId)
                    && currentUser.equals(that.currentUser);
        }

        @Override
        public int hashCode() {
            int result = database.hashCode();
            result = 31 * result + version.hashCode();
            result = 31 * result + clusterId.hashCode();
            result = 31 * result + currentUser.hashCode();
            return result;
        }
    }
}
