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
    public void shouldCreateVerifyAndCloseAllDatasourcesInReverseOrder() {
        RecordingFactory factory = new RecordingFactory();
        SimpleMysqlRouteRegistry registry = new SimpleMysqlRouteRegistry(propertiesWithTwoDatasources(), validator(), factory);

        log.info("已创建目标：{}", factory.createdNames);
        assertEquals("test-ops", factory.createdNames.get(0));
        assertEquals("test-audit", factory.createdNames.get(1));
        assertEquals("test-ops-user", factory.createdUsernames.get(0));
        assertEquals("test-audit-password", factory.createdPasswords.get(1));
        assertEquals(2, factory.verified.size());

        registry.destroy();

        assertSame(factory.created.get(1), factory.closed.get(0));
        assertSame(factory.created.get(0), factory.closed.get(1));
    }

    @Test
    public void shouldCloseCurrentAndRegisteredDatasourceWhenVerificationFails() {
        RecordingFactory factory = new RecordingFactory();
        factory.failOnVerifyCall = 2;

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> new SimpleMysqlRouteRegistry(propertiesWithTwoDatasources(), validator(), factory));

        log.info("初始化失败：code={}，cause={}", exception.getCode(), exception.getCause());
        assertEquals(ErrorCode.DATASOURCE_CREATE_FAILED, exception.getCode());
        assertNull(exception.getCause());
        assertEquals(2, factory.closed.size());
        assertSame(factory.created.get(1), factory.closed.get(0));
        assertSame(factory.created.get(0), factory.closed.get(1));
    }

    @Test
    public void shouldRejectDuplicatedPhysicalDatasourceFromFactory() {
        RecordingFactory factory = new RecordingFactory();
        factory.reuseCreatedDatasource = true;

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> new SimpleMysqlRouteRegistry(propertiesWithTwoDatasources(), validator(), factory));

        log.info("重复物理数据源：code={}，message={}", exception.getCode(), exception.getMessage());
        assertEquals(ErrorCode.DATASOURCE_CREATE_FAILED, exception.getCode());
        assertEquals(1, factory.closed.size());
        assertSame(factory.created.get(0), factory.closed.get(0));
    }

    @Test
    public void shouldDestroyIdempotentlyAndRejectFurtherAccess() {
        RecordingFactory factory = new RecordingFactory();
        SimpleMysqlRouteRegistry registry = new SimpleMysqlRouteRegistry(propertiesWithOneDatasource(), validator(), factory);

        registry.destroy();
        registry.destroy();

        assertEquals(1, factory.closed.size());
        SimpleMysqlRouteException exception = assertThrows(SimpleMysqlRouteException.class,
                () -> registry.getDataSource("test-ops"));
        assertEquals(ErrorCode.REGISTRY_DESTROYED, exception.getCode());
    }

    private MySqlRoutePropertiesValidator validator() {
        return new MySqlRoutePropertiesValidator(new MySqlRoutePatternMatcher());
    }

    private SimpleMysqlRouteProperties propertiesWithOneDatasource() {
        SimpleMysqlRouteProperties properties = new SimpleMysqlRouteProperties();
        properties.setPrimaryDatasource("test-ops");
        properties.getDatasources().put("test-ops", datasource("test-ops-user", "test-ops-password"));
        return properties;
    }

    private SimpleMysqlRouteProperties propertiesWithTwoDatasources() {
        SimpleMysqlRouteProperties properties = propertiesWithOneDatasource();
        properties.getDatasources().put("test-audit", datasource("test-audit-user", "test-audit-password"));
        return properties;
    }

    private SimpleMysqlRouteProperties.DatasourceConfig datasource(String username, String password) {
        SimpleMysqlRouteProperties.DatasourceConfig datasource = new SimpleMysqlRouteProperties.DatasourceConfig();
        datasource.setUrl("jdbc:mysql://example.invalid/test_order");
        datasource.setUsername(username);
        datasource.setPassword(password);
        return datasource;
    }

    private static class RecordingFactory implements MySqlRouteDataSourceFactory {
        private final List<DataSource> created = new ArrayList<>();
        private final List<DataSource> verified = new ArrayList<>();
        private final List<DataSource> closed = new ArrayList<>();
        private final List<String> createdNames = new ArrayList<>();
        private final List<String> createdUsernames = new ArrayList<>();
        private final List<String> createdPasswords = new ArrayList<>();
        private int verifyCalls;
        private int failOnVerifyCall;
        private boolean reuseCreatedDatasource;

        @Override
        public DataSource create(MySqlRouteTarget target, SimpleMysqlRouteProperties.DatasourceConfig datasource) {
            DataSource dataSource = reuseCreatedDatasource && !created.isEmpty() ? created.get(0) : mock(DataSource.class);
            if (!reuseCreatedDatasource || created.isEmpty()) {
                created.add(dataSource);
            }
            createdNames.add(target.getDatasource());
            createdUsernames.add(datasource.getUsername());
            createdPasswords.add(datasource.getPassword());
            return dataSource;
        }

        @Override
        public void verify(DataSource dataSource) {
            verified.add(dataSource);
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
