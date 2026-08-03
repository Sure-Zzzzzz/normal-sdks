package io.github.surezzzzzz.sdk.mysql.route.test.cases;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.SQLExceptionOverride;
import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteProperties;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.datasource.DefaultMySqlRouteDataSourceFactory;
import io.github.surezzzzzz.sdk.mysql.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.mysql.route.exception.SimpleMysqlRouteException;
import io.github.surezzzzzz.sdk.mysql.route.model.MySqlRouteTarget;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 默认 MySQL Route DataSource 工厂测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultMySqlRouteDataSourceFactoryTest {

    private final DefaultMySqlRouteDataSourceFactory factory = new DefaultMySqlRouteDataSourceFactory();

    @BeforeEach
    public void logTestStart() {
        log.info("开始执行默认 MySQL Route DataSource 工厂测试");
    }

    @Test
    public void shouldCreateHikariDatasourceWithAllSupportedScalarProperties() {
        SimpleMysqlRouteProperties.DatasourceConfig datasource = datasource();
        Map<String, String> hikari = datasource.getHikari();
        hikari.put("connection-timeout", "5000");
        hikari.put("validation_timeout", "2000");
        hikari.put("connectionTestQuery", "SELECT 1");
        hikari.put("connection-init-sql", "SET SESSION sql_mode = 'ANSI'");
        hikari.put("maximum-pool-size", "4");
        hikari.put("minimum_idle", "1");
        hikari.put("idleTimeout", "600000");
        hikari.put("max-lifetime", "1800000");
        hikari.put("initialization-fail-timeout", "-1");
        hikari.put("auto_commit", "false");
        hikari.put("read-only", "true");
        hikari.put("transaction_isolation", "TRANSACTION_READ_COMMITTED");
        hikari.put("catalog", "test_order");
        hikari.put("schema", "test_schema");
        hikari.put("isolate-internal-queries", "true");
        hikari.put("allow_pool_suspension", "true");
        hikari.put("pool-name", "test-route-pool");
        hikari.put("leak_detection_threshold", "0");
        hikari.put("register-mbeans", "false");
        hikari.put("exception-override-class-name", TestExceptionOverride.class.getName());

        Object createdDataSource = factory.create(target(), datasource);
        assertTrue(createdDataSource instanceof HikariDataSource);
        HikariDataSource dataSource = (HikariDataSource) createdDataSource;
        log.info("Hikari 连接池名称：{}", dataSource.getPoolName());

        try {
            assertEquals("jdbc:mysql://example.invalid/test_order", dataSource.getJdbcUrl());
            assertEquals("test-user", dataSource.getUsername());
            assertEquals("com.mysql.cj.jdbc.Driver", dataSource.getDriverClassName());
            assertEquals(5000L, dataSource.getConnectionTimeout());
            assertEquals(2000L, dataSource.getValidationTimeout());
            assertEquals("SELECT 1", dataSource.getConnectionTestQuery());
            assertEquals("SET SESSION sql_mode = 'ANSI'", dataSource.getConnectionInitSql());
            assertEquals(4, dataSource.getMaximumPoolSize());
            assertEquals(1, dataSource.getMinimumIdle());
            assertEquals(600000L, dataSource.getIdleTimeout());
            assertEquals(1800000L, dataSource.getMaxLifetime());
            assertEquals(-1L, dataSource.getInitializationFailTimeout());
            assertFalse(dataSource.isAutoCommit());
            assertTrue(dataSource.isReadOnly());
            assertEquals("TRANSACTION_READ_COMMITTED", dataSource.getTransactionIsolation());
            assertEquals("test_order", dataSource.getCatalog());
            assertEquals("test_schema", dataSource.getSchema());
            assertTrue(dataSource.isIsolateInternalQueries());
            assertTrue(dataSource.isAllowPoolSuspension());
            assertEquals("test-route-pool", dataSource.getPoolName());
            assertEquals(0L, dataSource.getLeakDetectionThreshold());
            assertFalse(dataSource.isRegisterMbeans());
            assertEquals(TestExceptionOverride.class.getName(), dataSource.getExceptionOverrideClassName());
        } finally {
            dataSource.close();
        }
    }

    @Test
    public void shouldAddMysqlDriverPropertiesWithoutChangingDatasourceIdentity() {
        SimpleMysqlRouteProperties.DatasourceConfig datasource = datasource();
        datasource.getHikari().put("data-source-properties.cachePrepStmts", "true");
        datasource.getHikari().put("data_source_properties.prepStmtCacheSize", "250");

        Object createdDataSource = factory.create(target(), datasource);
        assertTrue(createdDataSource instanceof HikariDataSource);
        HikariDataSource dataSource = (HikariDataSource) createdDataSource;
        log.info("MySQL 驱动属性数量：{}", dataSource.getDataSourceProperties().size());

        try {
            assertEquals("true", dataSource.getDataSourceProperties().getProperty("cachePrepStmts"));
            assertEquals("250", dataSource.getDataSourceProperties().getProperty("prepStmtCacheSize"));
            assertEquals("jdbc:mysql://example.invalid/test_order", dataSource.getJdbcUrl());
            assertEquals("test-user", dataSource.getUsername());
        } finally {
            dataSource.close();
        }
    }

    @Test
    public void shouldRejectUnsupportedAndProtectedHikariPropertiesWithoutLeakingDetails() {
        String[] propertyNames = {
                "jdbc-url", "username", "password", "driver-class-name", "data-source-class-name",
                "data-source-jndi", "data-source", "metric-registry", "metrics-tracker-factory",
                "health-check-registry", "health-check-properties", "scheduled-executor", "thread-factory",
                "exception-override", "data-source-properties", "unknown-property", "data-source-properties.",
                "data-source-properties. "
        };
        for (String propertyName : propertyNames) {
            SimpleMysqlRouteProperties.DatasourceConfig datasource = datasource();
            datasource.getHikari().put(propertyName, "secret-value");

            ConfigurationException exception = assertThrows(ConfigurationException.class,
                    () -> factory.create(target(), datasource));
            log.info("拒绝 Hikari 配置：property={}，code={}，message={}", propertyName, exception.getCode(),
                    exception.getMessage());

            assertEquals(ErrorCode.HIKARI_CONFIGURATION_INVALID, exception.getCode());
            assertNull(exception.getCause());
            assertTrue(exception.getMessage().contains("test-datasource"));
            assertFalse(exception.getMessage().contains("secret-value"));
            assertFalse(exception.getMessage().contains("example.invalid"));
            assertFalse(exception.getMessage().contains("test-user"));
        }
    }

    @Test
    public void shouldRejectBlankAndUnconvertibleHikariValues() {
        String[] propertyNames = {" ", "maximum-pool-size", "connection-timeout"};
        String[] propertyValues = {"4", " ", "not-a-number"};
        for (int index = 0; index < propertyNames.length; index++) {
            SimpleMysqlRouteProperties.DatasourceConfig datasource = datasource();
            datasource.getHikari().put(propertyNames[index], propertyValues[index]);

            ConfigurationException exception = assertThrows(ConfigurationException.class,
                    () -> factory.create(target(), datasource));
            log.info("非法 Hikari 配置：property={}，code={}，message={}", propertyNames[index], exception.getCode(),
                    exception.getMessage());

            assertEquals(ErrorCode.HIKARI_CONFIGURATION_INVALID, exception.getCode());
            assertNull(exception.getCause());
            if (!propertyValues[index].trim().isEmpty()) {
                assertFalse(exception.getMessage().contains(propertyValues[index]));
            }
        }
    }

    @Test
    public void shouldRejectInvalidHikariCapacityCombination() {
        SimpleMysqlRouteProperties.DatasourceConfig datasource = datasource();
        datasource.getHikari().put("maximum-pool-size", "1");
        datasource.getHikari().put("minimum-idle", "2");

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> factory.create(target(), datasource));
        log.info("非法 Hikari 容量组合：code={}，message={}", exception.getCode(), exception.getMessage());

        assertEquals(ErrorCode.HIKARI_CONFIGURATION_INVALID, exception.getCode());
        assertNull(exception.getCause());
        assertTrue(exception.getMessage().contains("test-datasource"));
    }

    @Test
    public void shouldHideVerifyFailureCause() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        SQLException connectionFailure = new SQLException("jdbc:mysql://example.invalid/test_order test-user test-password");
        when(dataSource.getConnection()).thenThrow(connectionFailure);

        ConfigurationException exception = assertThrows(ConfigurationException.class, () -> factory.verify(dataSource));

        assertEquals(ErrorCode.DATASOURCE_VERIFY_FAILED, exception.getCode());
        assertNull(exception.getCause());
        assertFalse(exception.getMessage().contains("example.invalid"));
        assertFalse(exception.getMessage().contains("test-user"));
        assertFalse(exception.getMessage().contains("test-password"));
    }

    @Test
    public void shouldHideCloseFailureCause() throws Exception {
        AutoCloseableDataSource dataSource = mock(AutoCloseableDataSource.class);
        doThrow(new IllegalStateException("jdbc:mysql://example.invalid/test_order test-password"))
                .when(dataSource).close();

        SimpleMysqlRouteException exception = assertThrows(SimpleMysqlRouteException.class,
                () -> factory.close(dataSource));

        assertEquals(ErrorCode.DATASOURCE_CLOSE_FAILED, exception.getCode());
        assertNull(exception.getCause());
        assertFalse(exception.getMessage().contains("example.invalid"));
        assertFalse(exception.getMessage().contains("test-password"));
    }

    private MySqlRouteTarget target() {
        return new MySqlRouteTarget("test-datasource");
    }

    private SimpleMysqlRouteProperties.DatasourceConfig datasource() {
        SimpleMysqlRouteProperties.DatasourceConfig datasource = new SimpleMysqlRouteProperties.DatasourceConfig();
        datasource.setUrl("jdbc:mysql://example.invalid/test_order");
        datasource.setUsername("test-user");
        datasource.setPassword("test-password");
        return datasource;
    }

    private interface AutoCloseableDataSource extends DataSource, AutoCloseable {
    }

    public static class TestExceptionOverride implements SQLExceptionOverride {

        @java.lang.Override
        public Override adjudicate(SQLException exception) {
            return Override.CONTINUE_EVICT;
        }
    }
}
