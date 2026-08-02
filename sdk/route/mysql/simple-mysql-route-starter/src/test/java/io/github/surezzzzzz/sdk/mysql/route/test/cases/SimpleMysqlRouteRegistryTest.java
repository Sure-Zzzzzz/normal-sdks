package io.github.surezzzzzz.sdk.mysql.route.test.cases;

import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteProperties;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.datasource.MySqlRouteDataSourceFactory;
import io.github.surezzzzzz.sdk.mysql.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.mysql.route.exception.SimpleMysqlRouteException;
import io.github.surezzzzzz.sdk.mysql.route.matcher.MySqlRoutePatternMatcher;
import io.github.surezzzzzz.sdk.mysql.route.model.MySqlRouteTarget;
import io.github.surezzzzzz.sdk.mysql.route.registry.SimpleMysqlRouteRegistry;
import io.github.surezzzzzz.sdk.mysql.route.validator.MySqlRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * MySQL Route Registry 测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleMysqlRouteRegistryTest {

    @BeforeEach
    public void logTestStart() {
        log.info("开始执行 MySQL Route 注册表测试");
    }

    @Test
    public void shouldCloseAlreadyCreatedAndCurrentDatasourceWhenVerificationFails() {
        RecordingFactory factory = new RecordingFactory();
        factory.failOnVerifyCall = 2;

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> new SimpleMysqlRouteRegistry(propertiesWithTwoDatasources(), validator(), factory));
        assertEquals(ErrorCode.DATASOURCE_CREATE_FAILED, exception.getCode());
        assertNull(exception.getCause());
        assertEquals(2, factory.closed.size());
        assertSame(factory.created.get(1), factory.closed.get(0));
        assertSame(factory.created.get(0), factory.closed.get(1));
    }

    @Test
    public void shouldPassEachNestedTargetCredentialOnlyToFactory() {
        RecordingFactory factory = new RecordingFactory();
        SimpleMysqlRouteRegistry registry = new SimpleMysqlRouteRegistry(propertiesWithTwoDatasources(), validator(), factory);

        assertEquals("test-cluster-a.ops", factory.createdKeys.get(0));
        assertEquals("test-cluster-b.audit", factory.createdKeys.get(1));
        assertEquals("test-ops-user", factory.createdUsernames.get(0));
        assertEquals("test-audit-user", factory.createdUsernames.get(1));
        assertEquals("test-ops-password", factory.createdPasswords.get(0));
        assertEquals("test-audit-password", factory.createdPasswords.get(1));
        assertEquals(2, registry.getDatasourceKeys().size());
        registry.destroy();
    }

    @Test
    public void shouldDestroyIdempotently() {
        RecordingFactory factory = new RecordingFactory();
        SimpleMysqlRouteRegistry registry = new SimpleMysqlRouteRegistry(propertiesWithOneDatasource(), validator(), factory);
        registry.destroy();
        registry.destroy();
        assertEquals(1, factory.closed.size());
        SimpleMysqlRouteException exception = assertThrows(SimpleMysqlRouteException.class,
                () -> registry.getDataSource("test-cluster-a.ops"));
        assertEquals(ErrorCode.REGISTRY_DESTROYED, exception.getCode());
    }

    private MySqlRoutePropertiesValidator validator() {
        return new MySqlRoutePropertiesValidator(new MySqlRoutePatternMatcher());
    }

    private SimpleMysqlRouteProperties propertiesWithOneDatasource() {
        SimpleMysqlRouteProperties properties = new SimpleMysqlRouteProperties();
        SimpleMysqlRouteProperties.ClusterConfig cluster = new SimpleMysqlRouteProperties.ClusterConfig();
        cluster.setHost("example.invalid");
        cluster.getDatasources().put("ops", datasource("test_ops", "test-ops-user", "test-ops-password"));
        properties.getClusters().put("test-cluster-a", cluster);
        return properties;
    }

    private SimpleMysqlRouteProperties propertiesWithTwoDatasources() {
        SimpleMysqlRouteProperties properties = propertiesWithOneDatasource();
        SimpleMysqlRouteProperties.ClusterConfig secondCluster = new SimpleMysqlRouteProperties.ClusterConfig();
        secondCluster.setHost("example.invalid");
        secondCluster.getDatasources().put("audit",
                datasource("test_audit", "test-audit-user", "test-audit-password"));
        properties.getClusters().put("test-cluster-b", secondCluster);
        return properties;
    }

    private SimpleMysqlRouteProperties.DatasourceConfig datasource(String database, String username, String password) {
        SimpleMysqlRouteProperties.DatasourceConfig datasource = new SimpleMysqlRouteProperties.DatasourceConfig();
        datasource.setDatabase(database);
        datasource.setUsername(username);
        datasource.setPassword(password);
        return datasource;
    }

    private static class RecordingFactory implements MySqlRouteDataSourceFactory {
        private final List<DataSource> created = new ArrayList<>();
        private final List<DataSource> closed = new ArrayList<>();
        private final List<String> createdKeys = new ArrayList<>();
        private final List<String> createdUsernames = new ArrayList<>();
        private final List<String> createdPasswords = new ArrayList<>();
        private int verifyCalls;
        private int failOnVerifyCall;

        @Override
        public DataSource create(MySqlRouteTarget target, SimpleMysqlRouteProperties.ClusterConfig cluster,
                                 SimpleMysqlRouteProperties.DatasourceConfig datasource) {
            DataSource dataSource = mock(DataSource.class);
            created.add(dataSource);
            createdKeys.add(target.getDatasourceKey());
            createdUsernames.add(datasource.getUsername());
            createdPasswords.add(datasource.getPassword());
            return dataSource;
        }

        @Override
        public void verify(DataSource dataSource) {
            verifyCalls++;
            if (verifyCalls == failOnVerifyCall) {
                throw new IllegalStateException("controlled failure");
            }
        }

        @Override
        public void close(DataSource dataSource) {
            closed.add(dataSource);
        }
    }
}
