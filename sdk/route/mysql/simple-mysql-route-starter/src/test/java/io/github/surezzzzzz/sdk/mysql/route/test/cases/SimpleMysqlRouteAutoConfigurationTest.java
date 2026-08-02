package io.github.surezzzzzz.sdk.mysql.route.test.cases;

import io.github.surezzzzzz.sdk.mysql.route.audit.MySqlRouteAuditPublisher;
import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteConfiguration;
import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteProperties;
import io.github.surezzzzzz.sdk.mysql.route.constant.SimpleMysqlRouteConstant;
import io.github.surezzzzzz.sdk.mysql.route.datasource.MySqlRouteDataSourceFactory;
import io.github.surezzzzzz.sdk.mysql.route.datasource.MySqlRoutingDataSource;
import io.github.surezzzzzz.sdk.mysql.route.model.MySqlRouteTarget;
import io.github.surezzzzzz.sdk.mysql.route.template.MySqlRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

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
            .withUserConfiguration(SimpleMysqlRouteConfiguration.class)
            .withBean(MySqlRouteDataSourceFactory.class, TestDataSourceFactory::new)
            .withPropertyValues("io.github.surezzzzzz.sdk.mysql.route.clusters.test-cluster-a.host=example.invalid",
                    "io.github.surezzzzzz.sdk.mysql.route.clusters.test-cluster-a.datasources.ops.database=test_ops",
                    "io.github.surezzzzzz.sdk.mysql.route.clusters.test-cluster-a.datasources.ops.username=test-user",
                    "io.github.surezzzzzz.sdk.mysql.route.clusters.test-cluster-a.datasources.ops.password=test-password",
                    "io.github.surezzzzzz.sdk.mysql.route.rules[0].pattern=test_order",
                    "io.github.surezzzzzz.sdk.mysql.route.rules[0].datasource-key=test-cluster-a.ops");

    @BeforeEach
    public void logTestStart() {
        log.info("开始执行 MySQL Route 自动配置测试");
    }

    @Test
    public void shouldNotCreateRouteBeansWhenDisabled() {
        contextRunner.run(context -> {
            assertFalse(context.containsBean("simpleMysqlRouteRegistry"));
            assertFalse(context.containsBean(SimpleMysqlRouteConstant.ROUTING_DATASOURCE_BEAN_NAME));
        });
    }

    @Test
    public void shouldCreateNamedRouteBeansWhenEnabled() {
        contextRunner.withPropertyValues("io.github.surezzzzzz.sdk.mysql.route.enable=true").run(context -> {
            assertTrue(context.containsBean("simpleMysqlRouteRegistry"));
            assertTrue(context.containsBean(SimpleMysqlRouteConstant.ROUTING_DATASOURCE_BEAN_NAME));
            assertTrue(context.containsBean(SimpleMysqlRouteConstant.JDBC_TEMPLATE_BEAN_NAME));
            assertTrue(context.containsBean(SimpleMysqlRouteConstant.NAMED_PARAMETER_JDBC_TEMPLATE_BEAN_NAME));
            DataSource routingDataSource = context.getBean(
                    SimpleMysqlRouteConstant.ROUTING_DATASOURCE_BEAN_NAME, DataSource.class);
            assertTrue(routingDataSource instanceof MySqlRoutingDataSource);
            assertFalse(context.getBeanFactory().getBeanDefinition(
                    SimpleMysqlRouteConstant.ROUTING_DATASOURCE_BEAN_NAME).isPrimary());
            JdbcTemplate jdbcTemplate = context.getBean(SimpleMysqlRouteConstant.JDBC_TEMPLATE_BEAN_NAME,
                    JdbcTemplate.class);
            NamedParameterJdbcTemplate namedParameterJdbcTemplate = context.getBean(
                    SimpleMysqlRouteConstant.NAMED_PARAMETER_JDBC_TEMPLATE_BEAN_NAME,
                    NamedParameterJdbcTemplate.class);
            MySqlRouteTemplate template = context.getBean(MySqlRouteTemplate.class);
            assertSame(routingDataSource, jdbcTemplate.getDataSource());
            assertSame(routingDataSource, namedParameterJdbcTemplate.getJdbcTemplate().getDataSource());
            assertSame(routingDataSource, template.routingDataSource());
            assertEquals(1, context.getBeansOfType(MySqlRouteTemplate.class).size());
            assertEquals(1, context.getBeansOfType(MySqlRouteAuditPublisher.class).size());
        });
    }

    @Test
    public void shouldFailStartupWithMissingNestedPassword() {
        new ApplicationContextRunner().withUserConfiguration(SimpleMysqlRouteConfiguration.class)
                .withBean(MySqlRouteDataSourceFactory.class, TestDataSourceFactory::new)
                .withPropertyValues("io.github.surezzzzzz.sdk.mysql.route.enable=true",
                        "io.github.surezzzzzz.sdk.mysql.route.clusters.test-cluster-a.host=example.invalid",
                        "io.github.surezzzzzz.sdk.mysql.route.clusters.test-cluster-a.datasources.ops.database=test_ops",
                        "io.github.surezzzzzz.sdk.mysql.route.clusters.test-cluster-a.datasources.ops.username=test-user")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    public void shouldRejectHostRoutingResourceDefinitionsWithReservedNames() {
        assertReservedNameFails(SimpleMysqlRouteConstant.ROUTING_DATASOURCE_BEAN_NAME, DataSource.class,
                () -> mock(DataSource.class));
        assertReservedNameFails(SimpleMysqlRouteConstant.JDBC_TEMPLATE_BEAN_NAME, JdbcTemplate.class,
                () -> new JdbcTemplate(mock(DataSource.class)));
        assertReservedNameFails(SimpleMysqlRouteConstant.NAMED_PARAMETER_JDBC_TEMPLATE_BEAN_NAME,
                NamedParameterJdbcTemplate.class,
                () -> new NamedParameterJdbcTemplate(mock(DataSource.class)));
    }

    @Test
    public void shouldKeepCustomAuditPublisher() {
        MySqlRouteAuditPublisher customPublisher = event -> {
        };
        contextRunner.withBean(MySqlRouteAuditPublisher.class, () -> customPublisher)
                .withPropertyValues("io.github.surezzzzzz.sdk.mysql.route.enable=true")
                .run(context -> assertSame(customPublisher, context.getBean(MySqlRouteAuditPublisher.class)));
    }

    private <T> void assertReservedNameFails(String beanName, Class<T> beanType,
                                             java.util.function.Supplier<T> beanSupplier) {
        contextRunner.withBean(beanName, beanType, beanSupplier)
                .withPropertyValues("io.github.surezzzzzz.sdk.mysql.route.enable=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    private static class TestDataSourceFactory implements MySqlRouteDataSourceFactory {
        @Override
        public DataSource create(MySqlRouteTarget target, SimpleMysqlRouteProperties.ClusterConfig cluster,
                                 SimpleMysqlRouteProperties.DatasourceConfig datasource) {
            return mock(DataSource.class);
        }

        @Override
        public void verify(DataSource dataSource) {
        }

        @Override
        public void close(DataSource dataSource) {
        }
    }
}
