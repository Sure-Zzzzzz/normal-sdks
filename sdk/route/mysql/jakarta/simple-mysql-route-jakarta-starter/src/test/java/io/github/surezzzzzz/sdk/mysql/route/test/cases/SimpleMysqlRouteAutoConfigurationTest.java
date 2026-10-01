package io.github.surezzzzzz.sdk.mysql.route.test.cases;

import io.github.surezzzzzz.sdk.mysql.route.audit.MySqlRouteAuditPublisher;
import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteJdbcTemplateAliasConfiguration;
import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteManagedDatasourceConfiguration;
import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteProperties;
import io.github.surezzzzzz.sdk.mysql.route.constant.SimpleMysqlRouteConstant;
import io.github.surezzzzzz.sdk.mysql.route.datasource.MySqlRouteDataSourceFactory;
import io.github.surezzzzzz.sdk.mysql.route.datasource.MySqlRoutingDataSource;
import io.github.surezzzzzz.sdk.mysql.route.model.MySqlRouteTarget;
import io.github.surezzzzzz.sdk.mysql.route.registry.SimpleMysqlRouteRegistry;
import io.github.surezzzzzz.sdk.mysql.route.template.MySqlRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * MySQL Route 自动配置测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleMysqlRouteAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                    DataSourceTransactionManagerAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
                    SimpleMysqlRouteManagedDatasourceConfiguration.class,
                    SimpleMysqlRouteJdbcTemplateAliasConfiguration.class));

    @BeforeEach
    public void logTestStart() {
        log.info("开始执行 MySQL Route 自动配置测试");
    }

    @Test
    public void shouldNotCreateRouteBeansWhenDisabled() {
        contextRunner.withBean(DataSource.class, () -> mock(DataSource.class)).run(context -> {
            assertNoStartupFailure(context);
            assertFalse(context.containsBean("simpleMysqlRouteRegistry"));
            assertFalse(context.containsBean(SimpleMysqlRouteConstant.ROUTING_DATASOURCE_BEAN_NAME));
        });
    }

    @Test
    public void shouldCreateRouteOwnedPrimaryBeforeBootJdbcInfrastructure() {
        RecordingFactory factory = new RecordingFactory();
        contextRunner.withBean(MySqlRouteDataSourceFactory.class, () -> factory)
                .withPropertyValues(routeOwnedProperties())
                .run(context -> {
                    assertNoStartupFailure(context);
                    MySqlRoutingDataSource routingDataSource = context.getBean(
                            SimpleMysqlRouteConstant.ROUTING_DATASOURCE_BEAN_NAME, MySqlRoutingDataSource.class);
                    SimpleMysqlRouteRegistry registry = context.getBean(SimpleMysqlRouteRegistry.class);
                    JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                    NamedParameterJdbcTemplate namedParameterJdbcTemplate =
                            context.getBean(NamedParameterJdbcTemplate.class);
                    DataSourceTransactionManager transactionManager = context.getBean(DataSourceTransactionManager.class);

                    log.info("Route 自建目标数量：{}", factory.created.size());
                    assertEquals(2, factory.created.size());
                    assertEquals(2, factory.verified.size());
                    assertSame(factory.created.get(0), registry.getDataSource("test-ops"));
                    assertSame(factory.created.get(1), registry.getDataSource("test-audit"));
                    assertSame(routingDataSource, jdbcTemplate.getDataSource());
                    assertSame(routingDataSource, namedParameterJdbcTemplate.getJdbcTemplate().getDataSource());
                    assertSame(routingDataSource, transactionManager.getDataSource());
                    assertSame(jdbcTemplate, context.getBean(SimpleMysqlRouteConstant.JDBC_TEMPLATE_BEAN_NAME));
                    assertSame(namedParameterJdbcTemplate,
                            context.getBean(SimpleMysqlRouteConstant.NAMED_PARAMETER_JDBC_TEMPLATE_BEAN_NAME));
                    assertSame(routingDataSource, context.getBean(MySqlRouteTemplate.class).routingDataSource());
                });
        assertEquals(2, factory.closed.size());
    }

    @Test
    public void shouldFailStartupWhenPrimaryDatasourceIsMissing() {
        contextRunner.withBean(MySqlRouteDataSourceFactory.class, RecordingFactory::new)
                .withPropertyValues("io.github.surezzzzzz.sdk.mysql.route.enable=true",
                        "io.github.surezzzzzz.sdk.mysql.route.datasources.test-ops.url=jdbc:mysql://example.invalid/test_order",
                        "io.github.surezzzzzz.sdk.mysql.route.datasources.test-ops.username=test-user",
                        "io.github.surezzzzzz.sdk.mysql.route.datasources.test-ops.password=test-password")
                .run(context -> {
                    log.info("缺少主数据源启动异常：{}", context.getStartupFailure());
                    assertNotNull(context.getStartupFailure());
                });
    }

    @Test
    public void shouldKeepCustomAuditPublisher() {
        MySqlRouteAuditPublisher customPublisher = event -> {
        };
        contextRunner.withBean(MySqlRouteDataSourceFactory.class, RecordingFactory::new)
                .withBean(MySqlRouteAuditPublisher.class, () -> customPublisher)
                .withPropertyValues(routeOwnedProperties())
                .run(context -> {
                    assertNoStartupFailure(context);
                    assertSame(customPublisher, context.getBean(MySqlRouteAuditPublisher.class));
                });
    }

    private void assertNoStartupFailure(org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        assertFalse(context.getStartupFailure() != null, String.valueOf(context.getStartupFailure()));
    }

    private String[] routeOwnedProperties() {
        return new String[]{
                "io.github.surezzzzzz.sdk.mysql.route.enable=true",
                "io.github.surezzzzzz.sdk.mysql.route.primary-datasource=test-ops",
                "io.github.surezzzzzz.sdk.mysql.route.datasources.test-ops.url=jdbc:mysql://example.invalid/test_order",
                "io.github.surezzzzzz.sdk.mysql.route.datasources.test-ops.username=test-ops-user",
                "io.github.surezzzzzz.sdk.mysql.route.datasources.test-ops.password=test-ops-password",
                "io.github.surezzzzzz.sdk.mysql.route.datasources.test-audit.url=jdbc:mysql://example.invalid/test_audit",
                "io.github.surezzzzzz.sdk.mysql.route.datasources.test-audit.username=test-audit-user",
                "io.github.surezzzzzz.sdk.mysql.route.datasources.test-audit.password=test-audit-password"
        };
    }

    private static class RecordingFactory implements MySqlRouteDataSourceFactory {
        private final List<DataSource> created = new ArrayList<>();
        private final List<DataSource> verified = new ArrayList<>();
        private final List<DataSource> closed = new ArrayList<>();

        @Override
        public DataSource create(MySqlRouteTarget target, SimpleMysqlRouteProperties.DatasourceConfig datasource) {
            DataSource dataSource = mock(DataSource.class);
            created.add(dataSource);
            return dataSource;
        }

        @Override
        public void verify(DataSource dataSource) {
            verified.add(dataSource);
        }

        @Override
        public void close(DataSource dataSource) {
            closed.add(dataSource);
        }
    }
}
