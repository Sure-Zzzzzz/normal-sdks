package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.diagnostic.KafkaRouteDiagnostics;
import io.github.surezzzzzz.sdk.kafka.route.factory.KafkaRouteAdminClientFactory;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteJdbcTemplateAliasConfiguration;
import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteManagedDatasourceConfiguration;
import io.github.surezzzzzz.sdk.mysql.route.configuration.SimpleMysqlRouteProperties;
import io.github.surezzzzzz.sdk.mysql.route.constant.SimpleMysqlRouteConstant;
import io.github.surezzzzzz.sdk.mysql.route.datasource.MySqlRouteDataSourceFactory;
import io.github.surezzzzzz.sdk.mysql.route.datasource.MySqlRoutingDataSource;
import io.github.surezzzzzz.sdk.mysql.route.model.MySqlRouteTarget;
import io.github.surezzzzzz.sdk.mysql.route.registry.SimpleMysqlRouteRegistry;
import io.github.surezzzzzz.sdk.mysql.route.template.MySqlRouteTemplate;
import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditPublisher;
import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditSearchService;
import io.github.surezzzzzz.sdk.ops.middleware.audit.PersistenceEngineMiddlewareOpsAuditPublisher;
import io.github.surezzzzzz.sdk.ops.middleware.catalog.DatasourceCatalogExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.catalog.DatasourceCatalogRequest;
import io.github.surezzzzzz.sdk.ops.middleware.catalog.DatasourceCatalogResponse;
import io.github.surezzzzzz.sdk.ops.middleware.configuration.SmartMiddlewareOpsServerAutoConfiguration;
import io.github.surezzzzzz.sdk.ops.middleware.controller.MiddlewareOpsController;
import io.github.surezzzzzz.sdk.ops.middleware.controller.MiddlewareOpsHttpExceptionHandler;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.*;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.*;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.*;
import io.github.surezzzzzz.sdk.ops.middleware.redis.RedisKeyDiscoveryExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.redis.RedisKeyDiscoveryRequestValidator;
import io.github.surezzzzzz.sdk.ops.middleware.redis.RedisOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsServerEngine;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareType;
import io.github.surezzzzzz.sdk.ops.middleware.ui.MiddlewareOpsPageController;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 默认运行链替换与 Route 启动快照边界测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class SmartMiddlewareOpsServerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SmartMiddlewareOpsServerAutoConfiguration.class);

    private final ApplicationContextRunner mysqlRouteContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                    DataSourceTransactionManagerAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
                    SimpleMysqlRouteManagedDatasourceConfiguration.class,
                    SimpleMysqlRouteJdbcTemplateAliasConfiguration.class,
                    SmartMiddlewareOpsServerAutoConfiguration.class));

    @Test
    void shouldBackOffEntireAutoConfigurationWhenEngineIsReplaced() {
        MiddlewareOpsServerEngine customEngine = new MiddlewareOpsServerEngine() {
            @Override
            public <Res> Res execute(io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest request,
                                     Class<Res> responseType) {
                throw new UnsupportedOperationException("test");
            }
        };

        contextRunner.withBean(MiddlewareOpsServerEngine.class, () -> customEngine).run(context -> {
            log.info("验证使用方自定义 Engine 后默认自动配置整体退让");
            assertSame(customEngine, context.getBean(MiddlewareOpsServerEngine.class));
            ConditionEvaluationReport report = context.getBean(ConditionEvaluationReport.class);
            String source = SmartMiddlewareOpsServerAutoConfiguration.class.getName();
            assertTrue(report.getConditionAndOutcomesBySource().containsKey(source));
            assertFalse(report.getConditionAndOutcomesBySource().get(source).isFullMatch());
        });
    }

    @Test
    void shouldRegisterMarkedInternalComponentsThroughPreciseScan() {
        contextRunner.run(context -> {
            assertEquals(1, context.getBeansOfType(MiddlewareOpsController.class).size());
            assertEquals(1, context.getBeansOfType(MiddlewareOpsPageController.class).size());
            assertEquals(1, context.getBeansOfType(MiddlewareOpsAuditSearchService.class).size());
            assertEquals(1, context.getBeansOfType(MiddlewareOpsHttpExceptionHandler.class).size());
        });
    }

    @Test
    void shouldUseScannedPersistenceAuditPublisherOnlyWhenPersistenceExists() {
        contextRunner.run(context -> assertEquals(0, context.getBeansOfType(MiddlewareOpsAuditPublisher.class).size()));
        contextRunner.withBean(PersistenceEngine.class, () -> mock(PersistenceEngine.class)).run(context -> {
            assertEquals(1, context.getBeansOfType(MiddlewareOpsAuditPublisher.class).size());
            assertTrue(context.getBean(MiddlewareOpsAuditPublisher.class)
                    instanceof PersistenceEngineMiddlewareOpsAuditPublisher);
        });
        MiddlewareOpsAuditPublisher customPublisher = event -> {
        };
        contextRunner.withBean(PersistenceEngine.class, () -> mock(PersistenceEngine.class))
                .withBean(MiddlewareOpsAuditPublisher.class, () -> customPublisher).run(context -> {
                    assertEquals(1, context.getBeansOfType(MiddlewareOpsAuditPublisher.class).size());
                    assertSame(customPublisher, context.getBean(MiddlewareOpsAuditPublisher.class));
                });
    }

    @Test
    void shouldNotRegisterPersistenceAuditPublisherWhenAuditWritingIsDisabled() {
        contextRunner.withBean(PersistenceEngine.class, () -> mock(PersistenceEngine.class))
                .withPropertyValues("io.github.surezzzzzz.sdk.ops.middleware.audit.write-enabled=false")
                .run(context -> assertTrue(context.getBeansOfType(MiddlewareOpsAuditPublisher.class).isEmpty()));
    }

    @Test
    void shouldStartWithNoRouteAndExposeEmptyCatalog() {
        contextRunner.run(context -> {
            DatasourceCatalogResponse response = catalog(context.getBean(DatasourceCatalogExecutor.class),
                    MiddlewareType.ELASTICSEARCH);

            assertEquals(0, response.getItems().size());
            assertTrue(context.containsBean("middlewareOpsServerEngine"));
        });
    }

    @Test
    void shouldSnapshotOnlyAvailableRedisRoute() {
        SimpleRedisRouteRegistry redisRegistry = mock(SimpleRedisRouteRegistry.class);
        when(redisRegistry.getDatasourceKeys()).thenReturn(new LinkedHashSet<String>(
                Arrays.asList("cache-main", "cache-report")));

        contextRunner.withBean(SimpleRedisRouteRegistry.class, () -> redisRegistry).run(context -> {
            DatasourceCatalogResponse response = catalog(context.getBean(DatasourceCatalogExecutor.class),
                    MiddlewareType.REDIS);

            assertEquals(2, response.getItems().size());
            assertEquals("redis", response.getItems().get(0).getMiddlewareType().getCode());
            assertEquals("cache-main", response.getItems().get(0).getDatasourceKey());
            assertEquals("cache-report", response.getItems().get(1).getDatasourceKey());
        });
    }

    @Test
    void shouldRegisterRedisDiscoveryOnlyWhenRouteRegistryExists() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);

        contextRunner.run(context -> {
            assertTrue(context.getBeansOfType(RedisOperationsViewAdapter.class).isEmpty());
            assertTrue(context.getBeansOfType(RedisKeyDiscoveryExecutor.class).isEmpty());
            assertTrue(context.getBeansOfType(RedisKeyDiscoveryRequestValidator.class).isEmpty());
        });
        contextRunner.withBean(SimpleRedisRouteRegistry.class, () -> registry).run(context -> {
            assertEquals(1, context.getBeansOfType(RedisOperationsViewAdapter.class).size());
            assertEquals(1, context.getBeansOfType(RedisKeyDiscoveryExecutor.class).size());
            assertEquals(1, context.getBeansOfType(RedisKeyDiscoveryRequestValidator.class).size());
        });
    }

    @Test
    void shouldRegisterMysqlCapabilityOnlyWhenRouteRegistryAndTemplateExist() {
        SimpleMysqlRouteRegistry mysqlRegistry = mock(SimpleMysqlRouteRegistry.class);

        contextRunner.withBean(SimpleMysqlRouteRegistry.class, () -> mysqlRegistry).run(context -> {
            log.info("缺少 MySQL Route Template 时不得注册 MySQL 查询能力");
            assertTrue(context.getBeansOfType(MysqlOperationsViewAdapter.class).isEmpty());
            assertTrue(context.getBeansOfType(MysqlDatasourceStatusExecutor.class).isEmpty());
            assertTrue(context.getBeansOfType(MysqlSelectExecutor.class).isEmpty());
        });
        contextRunner.withBean(SimpleMysqlRouteRegistry.class, () -> mysqlRegistry)
                .withBean(MySqlRouteTemplate.class, () -> mock(MySqlRouteTemplate.class)).run(context -> {
                    log.info("MySQL Route Registry 与 Template 齐备时注册完整只读查询能力");
                    assertEquals(1, context.getBeansOfType(MysqlOperationsViewAdapter.class).size());
                    assertEquals(1, context.getBeansOfType(MysqlDatasourceStatusExecutor.class).size());
                    assertEquals(1, context.getBeansOfType(MysqlDatasourceStatusRequestValidator.class).size());
                    assertEquals(1, context.getBeansOfType(MysqlSelectExecutor.class).size());
                    assertEquals(1, context.getBeansOfType(MysqlSelectRequestValidator.class).size());
                    assertEquals(1, context.getBeansOfType(MysqlExplainExecutor.class).size());
                    assertEquals(1, context.getBeansOfType(MysqlExplainRequestValidator.class).size());
                    assertEquals(1, context.getBeansOfType(MysqlTableListExecutor.class).size());
                    assertEquals(1, context.getBeansOfType(MysqlTableListRequestValidator.class).size());
                    assertEquals(1, context.getBeansOfType(MysqlTableColumnsExecutor.class).size());
                    assertEquals(1, context.getBeansOfType(MysqlTableColumnsRequestValidator.class).size());
                    assertEquals(1, context.getBeansOfType(MysqlTableIndexesExecutor.class).size());
                    assertEquals(1, context.getBeansOfType(MysqlTableIndexesRequestValidator.class).size());
                });
    }

    @Test
    void shouldRegisterKafkaCapabilitiesOnlyWithCompleteRouteOwnedResources() {
        SimpleKafkaRouteRegistry registry = mock(SimpleKafkaRouteRegistry.class);

        contextRunner.withBean(SimpleKafkaRouteRegistry.class, () -> registry).run(context -> {
            assertTrue(context.getBeansOfType(KafkaOperationsViewAdapter.class).isEmpty());
            assertTrue(context.getBeansOfType(KafkaTopicConfigExecutor.class).isEmpty());
            assertTrue(context.getBeansOfType(KafkaConsumerGroupDetailExecutor.class).isEmpty());
        });
        contextRunner.withBean(SimpleKafkaRouteRegistry.class, () -> registry)
                .withBean(KafkaRouteDiagnostics.class, () -> mock(KafkaRouteDiagnostics.class))
                .withBean(KafkaRouteAdminClientFactory.class, () -> mock(KafkaRouteAdminClientFactory.class)).run(context -> {
                    assertEquals(1, context.getBeansOfType(KafkaOperationsViewAdapter.class).size());
                    assertEquals(1, context.getBeansOfType(KafkaTopicConfigExecutor.class).size());
                    assertEquals(1, context.getBeansOfType(KafkaTopicConfigRequestValidator.class).size());
                    assertEquals(1, context.getBeansOfType(KafkaConsumerGroupDetailExecutor.class).size());
                    assertEquals(1, context.getBeansOfType(KafkaConsumerGroupDetailRequestValidator.class).size());
                });
    }

    @Test
    void shouldRegisterElasticsearchIndexCapabilityOnlyWhenRouteRegistryExists() {
        SimpleElasticsearchRouteRegistry registry = mock(SimpleElasticsearchRouteRegistry.class);

        contextRunner.run(context -> {
            assertTrue(context.getBeansOfType(ElasticsearchOperationsViewAdapter.class).isEmpty());
            assertTrue(context.getBeansOfType(ElasticsearchIndexListExecutor.class).isEmpty());
            assertTrue(context.getBeansOfType(ElasticsearchIndexListRequestValidator.class).isEmpty());
            assertTrue(context.getBeansOfType(ElasticsearchFieldCapabilitiesExecutor.class).isEmpty());
            assertTrue(context.getBeansOfType(ElasticsearchFieldCapabilitiesRequestValidator.class).isEmpty());
        });
        contextRunner.withBean(SimpleElasticsearchRouteRegistry.class, () -> registry).run(context -> {
            assertEquals(1, context.getBeansOfType(ElasticsearchOperationsViewAdapter.class).size());
            assertEquals(1, context.getBeansOfType(ElasticsearchIndexListExecutor.class).size());
            assertEquals(1, context.getBeansOfType(ElasticsearchIndexListRequestValidator.class).size());
            assertEquals(1, context.getBeansOfType(ElasticsearchFieldCapabilitiesExecutor.class).size());
            assertEquals(1, context.getBeansOfType(ElasticsearchFieldCapabilitiesRequestValidator.class).size());
        });
    }

    @Test
    void shouldSnapshotAllAvailableRouteTypes() {
        SimpleElasticsearchRouteRegistry elasticsearchRegistry = mock(SimpleElasticsearchRouteRegistry.class);
        Map<String, Object> templates = new LinkedHashMap<>();
        templates.put("search-main", null);
        when(elasticsearchRegistry.getTemplates()).thenReturn((Map) templates);
        SimpleRedisRouteRegistry redisRegistry = mock(SimpleRedisRouteRegistry.class);
        when(redisRegistry.getDatasourceKeys()).thenReturn(new LinkedHashSet<String>(
                Collections.singletonList("cache-main")));
        SimpleKafkaRouteRegistry kafkaRegistry = mock(SimpleKafkaRouteRegistry.class);
        when(kafkaRegistry.getDatasourceKeys()).thenReturn(new LinkedHashSet<String>(
                Collections.singletonList("event-main")));
        SimpleMysqlRouteRegistry mysqlRegistry = mock(SimpleMysqlRouteRegistry.class);
        when(mysqlRegistry.getDatasources()).thenReturn(new LinkedHashSet<String>(
                Collections.singletonList("ops-primary")));

        contextRunner.withBean(SimpleElasticsearchRouteRegistry.class, () -> elasticsearchRegistry)
                .withBean(SimpleRedisRouteRegistry.class, () -> redisRegistry)
                .withBean(SimpleKafkaRouteRegistry.class, () -> kafkaRegistry)
                .withBean(SimpleMysqlRouteRegistry.class, () -> mysqlRegistry)
                .withBean(KafkaRouteDiagnostics.class, () -> mock(KafkaRouteDiagnostics.class))
                .withBean(KafkaRouteAdminClientFactory.class, () -> mock(KafkaRouteAdminClientFactory.class))
                .run(context -> {
                    DatasourceCatalogExecutor executor = context.getBean(DatasourceCatalogExecutor.class);

                    assertEquals(1, catalog(executor, MiddlewareType.ELASTICSEARCH).getItems().size());
                    assertEquals(1, catalog(executor, MiddlewareType.KAFKA).getItems().size());
                    DatasourceCatalogResponse mysql = catalog(executor, MiddlewareType.MYSQL);
                    assertEquals(1, mysql.getItems().size());
                    assertEquals("ops-primary", mysql.getItems().get(0).getDatasourceKey());
                    DatasourceCatalogResponse redis = catalog(executor, MiddlewareType.REDIS);
                    assertEquals(1, redis.getItems().size());
                    assertEquals("cache-main", redis.getItems().get(0).getDatasourceKey());
                });
    }

    @Test
    void shouldExposeMysqlOpsCapabilitiesWithRouteOwnedDatasourceConfiguration() {
        RecordingMySqlRouteDataSourceFactory factory = new RecordingMySqlRouteDataSourceFactory();

        mysqlRouteContextRunner.withBean(MySqlRouteDataSourceFactory.class, () -> factory)
                .withPropertyValues(mysqlRouteProperties())
                .run(context -> {
                    assertFalse(context.getStartupFailure() != null, String.valueOf(context.getStartupFailure()));
                    assertEquals(2, factory.getCreated().size());
                    assertEquals(2, factory.getVerified().size());
                    assertEquals(2, context.getBean(SimpleMysqlRouteRegistry.class).getDatasources().size());
                    assertTrue(context.getBean(SimpleMysqlRouteRegistry.class).containsDatasource("ops-primary"));
                    assertTrue(context.getBean(SimpleMysqlRouteRegistry.class).containsDatasource("ops-secondary"));
                    MySqlRoutingDataSource routingDataSource = context.getBean(
                            SimpleMysqlRouteConstant.ROUTING_DATASOURCE_BEAN_NAME, MySqlRoutingDataSource.class);
                    assertSame(routingDataSource, context.getBean(DataSource.class));
                    assertEquals(1, context.getBeansOfType(MySqlRouteTemplate.class).size());
                    assertSame(routingDataSource, context.getBean(MySqlRouteTemplate.class).routingDataSource());
                    assertEquals(1, context.getBeansOfType(MysqlOperationsViewAdapter.class).size());
                    assertEquals(1, context.getBeansOfType(MysqlDatasourceStatusExecutor.class).size());
                    assertEquals(1, context.getBeansOfType(MysqlSelectExecutor.class).size());

                    DatasourceCatalogResponse mysql = catalog(context.getBean(DatasourceCatalogExecutor.class),
                            MiddlewareType.MYSQL);
                    assertEquals(2, mysql.getItems().size());
                    assertEquals("ops-primary", mysql.getItems().get(0).getDatasourceKey());
                    assertEquals("ops-secondary", mysql.getItems().get(1).getDatasourceKey());
                });
        assertEquals(2, factory.getClosed().size());
    }

    private String[] mysqlRouteProperties() {
        return new String[]{
                "io.github.surezzzzzz.sdk.mysql.route.enable=true",
                "io.github.surezzzzzz.sdk.mysql.route.primary-datasource=ops-primary",
                "io.github.surezzzzzz.sdk.mysql.route.datasources.ops-primary.url=jdbc:mysql://example.invalid/ops_primary",
                "io.github.surezzzzzz.sdk.mysql.route.datasources.ops-primary.username=ops-primary-reader",
                "io.github.surezzzzzz.sdk.mysql.route.datasources.ops-primary.password=test-primary-password",
                "io.github.surezzzzzz.sdk.mysql.route.datasources.ops-secondary.url=jdbc:mysql://example.invalid/ops_secondary",
                "io.github.surezzzzzz.sdk.mysql.route.datasources.ops-secondary.username=ops-secondary-reader",
                "io.github.surezzzzzz.sdk.mysql.route.datasources.ops-secondary.password=test-secondary-password"
        };
    }

    private DatasourceCatalogResponse catalog(DatasourceCatalogExecutor executor, MiddlewareType middlewareType) {
        return executor.execute(new DatasourceCatalogRequest(middlewareType));
    }

    private static class RecordingMySqlRouteDataSourceFactory implements MySqlRouteDataSourceFactory {
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

        private List<DataSource> getCreated() {
            return created;
        }

        private List<DataSource> getVerified() {
            return verified;
        }

        private List<DataSource> getClosed() {
            return closed;
        }
    }
}
