package io.github.surezzzzzz.sdk.mysql.route.test.cases;

import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteProperties;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.credential.MySqlRouteCredentialResolver;
import io.github.surezzzzzz.sdk.mysql.route.datasource.MySqlRouteDataSourceFactory;
import io.github.surezzzzzz.sdk.mysql.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.mysql.route.exception.SimpleMysqlRouteException;
import io.github.surezzzzzz.sdk.mysql.route.matcher.MySqlRoutePatternMatcher;
import io.github.surezzzzzz.sdk.mysql.route.model.MySqlRouteCredential;
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
        SimpleMysqlRouteProperties properties = propertiesWithTwoDatasources();
        RecordingFactory factory = new RecordingFactory();
        factory.failOnVerifyCall = 2;
        MySqlRouteCredentialResolver credentials = reference -> new MySqlRouteCredential("test-user", "test-password");

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> new SimpleMysqlRouteRegistry(properties,
                        new MySqlRoutePropertiesValidator(new MySqlRoutePatternMatcher()), credentials, factory));
        assertEquals(ErrorCode.DATASOURCE_CREATE_FAILED, exception.getCode());
        assertEquals(2, factory.closed.size());
        assertSame(factory.created.get(1), factory.closed.get(0));
        assertSame(factory.created.get(0), factory.closed.get(1));
    }

    @Test
    public void shouldDestroyIdempotently() {
        RecordingFactory factory = new RecordingFactory();
        SimpleMysqlRouteRegistry registry = new SimpleMysqlRouteRegistry(propertiesWithOneDatasource(),
                new MySqlRoutePropertiesValidator(new MySqlRoutePatternMatcher()),
                reference -> new MySqlRouteCredential("test-user", "test-password"), factory);
        registry.destroy();
        registry.destroy();
        assertEquals(1, factory.closed.size());
        SimpleMysqlRouteException exception = assertThrows(SimpleMysqlRouteException.class,
                () -> registry.getDataSource("test-ops-a"));
        assertEquals(ErrorCode.REGISTRY_DESTROYED, exception.getCode());
    }

    private SimpleMysqlRouteProperties propertiesWithOneDatasource() {
        SimpleMysqlRouteProperties properties = new SimpleMysqlRouteProperties();
        SimpleMysqlRouteProperties.ClusterConfig cluster = new SimpleMysqlRouteProperties.ClusterConfig();
        cluster.setHost("example.invalid");
        cluster.setCredentialRef("test-reader-credential");
        properties.getClusters().put("test-cluster-a", cluster);
        SimpleMysqlRouteProperties.DatasourceConfig datasource = new SimpleMysqlRouteProperties.DatasourceConfig();
        datasource.setClusterKey("test-cluster-a");
        datasource.setDatabase("test_ops");
        properties.getDatasources().put("test-ops-a", datasource);
        return properties;
    }

    private SimpleMysqlRouteProperties propertiesWithTwoDatasources() {
        SimpleMysqlRouteProperties properties = propertiesWithOneDatasource();
        SimpleMysqlRouteProperties.ClusterConfig secondCluster = new SimpleMysqlRouteProperties.ClusterConfig();
        secondCluster.setHost("example.invalid");
        secondCluster.setCredentialRef("test-reader-credential");
        properties.getClusters().put("test-cluster-b", secondCluster);
        SimpleMysqlRouteProperties.DatasourceConfig datasource = new SimpleMysqlRouteProperties.DatasourceConfig();
        datasource.setClusterKey("test-cluster-b");
        datasource.setDatabase("test_audit");
        properties.getDatasources().put("test-audit-a", datasource);
        return properties;
    }

    private static class RecordingFactory implements MySqlRouteDataSourceFactory {
        private final List<DataSource> created = new ArrayList<>();
        private final List<DataSource> closed = new ArrayList<>();
        private int verifyCalls;
        private int failOnVerifyCall;

        @Override
        public DataSource create(io.github.surezzzzzz.sdk.mysql.route.model.MySqlRouteTarget target,
                                 SimpleMysqlRouteProperties.ClusterConfig cluster,
                                 MySqlRouteCredential credential) {
            DataSource dataSource = mock(DataSource.class);
            created.add(dataSource);
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
