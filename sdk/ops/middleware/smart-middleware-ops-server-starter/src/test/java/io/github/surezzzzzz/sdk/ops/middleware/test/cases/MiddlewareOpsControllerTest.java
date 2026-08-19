package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditSearchService;
import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditTimeRange;
import io.github.surezzzzzz.sdk.ops.middleware.catalog.DatasourceCatalogRequest;
import io.github.surezzzzzz.sdk.ops.middleware.catalog.DatasourceCatalogResponse;
import io.github.surezzzzzz.sdk.ops.middleware.configuration.SmartMiddlewareOpsServerProperties;
import io.github.surezzzzzz.sdk.ops.middleware.controller.MiddlewareOpsController;
import io.github.surezzzzzz.sdk.ops.middleware.controller.response.MiddlewareOpsAuditPageResponse;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.*;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.*;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.*;
import io.github.surezzzzzz.sdk.ops.middleware.redis.*;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsRequest;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsServerEngine;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 只读资源控制器输入与编排边界测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class MiddlewareOpsControllerTest {

    @Test
    void shouldCreateTypedRequestAndDelegateToEngine() {
        CapturingEngine engine = new CapturingEngine();
        MiddlewareOpsController controller = controller(engine);

        KafkaTopicListResponse response = controller.kafkaTopics("default", null, null);
        log.info("控制器构造请求：datasource={}，size={}", "default", 50);

        assertSame(engine.response, response);
        assertEquals(1, engine.calls);
        assertEquals(KafkaTopicListRequest.class, engine.request.getClass());
        KafkaTopicListRequest request = (KafkaTopicListRequest) engine.request;
        assertEquals("default", request.getDatasourceKey());
        assertEquals(50, request.getSize());
        assertEquals(KafkaTopicListResponse.class, engine.responseType);
    }

    @Test
    void shouldCreateMysqlRequestsWithoutSqlAuditScope() {
        CapturingEngine engine = new CapturingEngine();
        MiddlewareOpsController controller = controller(engine);

        controller.mysqlDatasourceOverviewStatus("orders");
        assertEquals(MysqlDatasourceStatusRequest.class, engine.request.getClass());
        assertEquals("orders", ((MysqlDatasourceStatusRequest) engine.request).getDatasourceKey());
        assertFalse(engine.request.isAuditRequired());
        assertEquals(MysqlDatasourceStatusResponse.class, engine.responseType);

        controller.mysqlDatasourceStatus("orders");
        assertEquals(MysqlDatasourceStatusRequest.class, engine.request.getClass());
        assertEquals("orders", ((MysqlDatasourceStatusRequest) engine.request).getDatasourceKey());
        assertTrue(engine.request.isAuditRequired());
        assertEquals(MysqlDatasourceStatusResponse.class, engine.responseType);

        String sql = "SELECT id FROM orders WHERE id = 1";
        controller.mysqlSelect("orders", sql, 10);
        assertEquals(MysqlSelectRequest.class, engine.request.getClass());
        MysqlSelectRequest request = (MysqlSelectRequest) engine.request;
        assertEquals("orders", request.getDatasourceKey());
        assertEquals(sql, request.getSql());
        assertEquals(10, request.getSize());
        assertEquals("controlled-select", request.getResourceScope());
        assertEquals(MysqlSelectResponse.class, engine.responseType);

        controller.mysqlExplain("orders", sql);
        assertEquals(MysqlExplainRequest.class, engine.request.getClass());
        MysqlExplainRequest explainRequest = (MysqlExplainRequest) engine.request;
        assertEquals(sql, explainRequest.getSql());
        assertEquals("controlled-explain", explainRequest.getResourceScope());
        assertEquals(MysqlExplainResponse.class, engine.responseType);

        controller.mysqlTables("orders", "order", 10);
        assertEquals(MysqlTableListRequest.class, engine.request.getClass());
        MysqlTableListRequest tablesRequest = (MysqlTableListRequest) engine.request;
        assertEquals("order", tablesRequest.getPrefix());
        assertEquals(10, tablesRequest.getSize());
        assertEquals("table-list", tablesRequest.getResourceScope());
        assertEquals(MysqlTableListResponse.class, engine.responseType);

        controller.mysqlTables("orders", null, null);
        tablesRequest = (MysqlTableListRequest) engine.request;
        assertNull(tablesRequest.getPrefix());
        assertEquals(200, tablesRequest.getSize());

        SmartMiddlewareOpsServerProperties properties = new SmartMiddlewareOpsServerProperties();
        properties.getQuery().setDefaultSize(1);
        properties.getQuery().setMaxSize(100);
        controller = controller(engine, properties, new CapturingAuditSearchService(properties));
        controller.mysqlTables("orders", null, null);
        assertEquals(100, ((MysqlTableListRequest) engine.request).getSize());

        controller.mysqlTableColumns("orders", "orders_2026");
        assertEquals(MysqlTableColumnsRequest.class, engine.request.getClass());
        assertEquals("table-columns:orders_2026", engine.request.getResourceScope());
        assertEquals(MysqlTableColumnsResponse.class, engine.responseType);

        controller.mysqlTableIndexes("orders", "orders_2026");
        assertEquals(MysqlTableIndexesRequest.class, engine.request.getClass());
        assertEquals("table-indexes:orders_2026", engine.request.getResourceScope());
        assertEquals(MysqlTableIndexesResponse.class, engine.responseType);
    }

    @Test
    void shouldCreateFixedWorkspaceCatalogRequest() {
        CapturingEngine engine = new CapturingEngine();
        MiddlewareOpsController controller = controller(engine);

        controller.elasticsearchDatasourceCatalog();
        assertCatalogRequest(engine, MiddlewareType.ELASTICSEARCH,
                MiddlewareOpsCapability.ELASTICSEARCH_DATASOURCE_CATALOG, "elasticsearch-catalog");
        controller.redisDatasourceCatalog();
        assertCatalogRequest(engine, MiddlewareType.REDIS, MiddlewareOpsCapability.REDIS_DATASOURCE_CATALOG,
                "redis-catalog");
        controller.kafkaDatasourceCatalog();
        assertCatalogRequest(engine, MiddlewareType.KAFKA, MiddlewareOpsCapability.KAFKA_DATASOURCE_CATALOG,
                "kafka-catalog");
        controller.mysqlDatasourceCatalog();
        assertCatalogRequest(engine, MiddlewareType.MYSQL, MiddlewareOpsCapability.MYSQL_DATASOURCE_CATALOG,
                "mysql-catalog");
        log.info("固定目录工作区数量：calls={}", engine.calls);

        assertEquals(4, engine.calls);
    }

    @Test
    void shouldCreateAllPublishedWorkspaceRequests() {
        CapturingEngine engine = new CapturingEngine();
        MiddlewareOpsController controller = controller(engine);

        controller.elasticsearchSummary("search-primary");
        assertEquals(ElasticsearchSummaryRequest.class, engine.request.getClass());
        assertEquals("search-primary", ((ElasticsearchSummaryRequest) engine.request).getDatasourceKey());
        assertEquals(ElasticsearchSummaryResponse.class, engine.responseType);

        controller.elasticsearchIndices("search-primary");
        assertEquals(ElasticsearchIndexListRequest.class, engine.request.getClass());
        assertEquals("search-primary", ((ElasticsearchIndexListRequest) engine.request).getDatasourceKey());
        assertEquals("index-list", engine.request.getResourceScope());
        assertEquals(ElasticsearchIndexListResponse.class, engine.responseType);

        controller.elasticsearchFieldCapabilities("search-primary", "orders");
        assertEquals(ElasticsearchFieldCapabilitiesRequest.class, engine.request.getClass());
        ElasticsearchFieldCapabilitiesRequest fieldRequest = (ElasticsearchFieldCapabilitiesRequest) engine.request;
        assertEquals("search-primary", fieldRequest.getDatasourceKey());
        assertEquals("orders", fieldRequest.getIndex());
        assertEquals("field-capabilities:orders", fieldRequest.getResourceScope());
        assertEquals(ElasticsearchFieldCapabilitiesResponse.class, engine.responseType);

        controller.elasticsearchDocuments("search-primary", "orders",
                "eyJxdWVyeSI6eyJtYXRjaF9hbGwiOnt9fX0");
        assertEquals(ElasticsearchDocumentQueryRequest.class, engine.request.getClass());
        ElasticsearchDocumentQueryRequest documentRequest = (ElasticsearchDocumentQueryRequest) engine.request;
        assertEquals("orders", documentRequest.getIndex());
        assertEquals("{\"query\":{\"match_all\":{}}}", documentRequest.getDsl());
        assertEquals(ElasticsearchDocumentQueryResponse.class, engine.responseType);

        controller.redisDatasourceOverview();
        assertEquals(RedisDatasourceListRequest.class, engine.request.getClass());
        assertFalse(engine.request.isAuditRequired());
        assertEquals(RedisDatasourceListResponse.class, engine.responseType);
        controller.redisDatasources();
        assertEquals(RedisDatasourceListRequest.class, engine.request.getClass());
        assertTrue(engine.request.isAuditRequired());
        assertEquals(RedisDatasourceListResponse.class, engine.responseType);
        controller.redisSummary("cache-primary");
        assertEquals(RedisSummaryRequest.class, engine.request.getClass());
        assertEquals("cache-primary", ((RedisSummaryRequest) engine.request).getDatasourceKey());
        assertEquals(RedisDatasourceResponse.class, engine.responseType);
        controller.redisKeyDiscovery("cache-primary", "order:", null);
        assertEquals(RedisKeyDiscoveryRequest.class, engine.request.getClass());
        RedisKeyDiscoveryRequest discoveryRequest = (RedisKeyDiscoveryRequest) engine.request;
        assertEquals("cache-primary", discoveryRequest.getDatasourceKey());
        assertEquals("order:", discoveryRequest.getPrefix());
        assertEquals(50, discoveryRequest.getSize());
        assertEquals(MiddlewareOpsCapability.REDIS_KEY_DISCOVERY, discoveryRequest.getCapability());
        assertEquals("key-discovery", discoveryRequest.getResourceScope());
        assertEquals(RedisKeyDiscoveryResponse.class, engine.responseType);
        controller.redisKeyDiscovery("cache-primary", "order:", 20);
        assertEquals(20, ((RedisKeyDiscoveryRequest) engine.request).getSize());

        controller.redisKeyMetadata("cache-primary", "order:1");
        assertEquals(RedisKeyMetadataRequest.class, engine.request.getClass());
        assertEquals("order:1", ((RedisKeyMetadataRequest) engine.request).getKey());
        assertEquals(RedisKeyMetadataResponse.class, engine.responseType);
        controller.redisKeyValue("cache-primary", "order:1", "status", 8L, 20);
        assertEquals(RedisKeyReadRequest.class, engine.request.getClass());
        RedisKeyReadRequest redisReadRequest = (RedisKeyReadRequest) engine.request;
        assertEquals("status", redisReadRequest.getField());
        assertEquals(8L, redisReadRequest.getOffset());
        assertEquals(20, redisReadRequest.getSize());
        assertEquals(RedisKeyReadResponse.class, engine.responseType);

        controller.kafkaDatasourceOverview();
        assertEquals(KafkaDatasourceListRequest.class, engine.request.getClass());
        assertFalse(engine.request.isAuditRequired());
        assertEquals(KafkaDatasourceListResponse.class, engine.responseType);
        controller.kafkaDatasources();
        assertEquals(KafkaDatasourceListRequest.class, engine.request.getClass());
        assertTrue(engine.request.isAuditRequired());
        assertEquals(KafkaDatasourceListResponse.class, engine.responseType);
        controller.kafkaTopics("kafka-primary", null, 20);
        assertEquals(KafkaTopicListRequest.class, engine.request.getClass());
        assertEquals(20, ((KafkaTopicListRequest) engine.request).getSize());
        assertEquals(KafkaTopicListResponse.class, engine.responseType);
        controller.kafkaConsumerGroups("kafka-primary", null, 20);
        assertEquals(KafkaConsumerGroupListRequest.class, engine.request.getClass());
        assertEquals(20, ((KafkaConsumerGroupListRequest) engine.request).getSize());
        assertEquals(KafkaConsumerGroupListResponse.class, engine.responseType);
        controller.kafkaTopicConfig("kafka-primary", "order-events");
        assertEquals(KafkaTopicConfigRequest.class, engine.request.getClass());
        assertEquals("order-events", ((KafkaTopicConfigRequest) engine.request).getTopic());
        assertEquals(KafkaTopicConfigResponse.class, engine.responseType);
        controller.kafkaConsumerGroupDetail("kafka-primary", "order-group");
        assertEquals(KafkaConsumerGroupDetailRequest.class, engine.request.getClass());
        assertEquals("order-group", ((KafkaConsumerGroupDetailRequest) engine.request).getGroupId());
        assertEquals(KafkaConsumerGroupDetailResponse.class, engine.responseType);
        controller.kafkaTopicRuntime("kafka-primary", "order-events");
        assertEquals(KafkaTopicRuntimeRequest.class, engine.request.getClass());
        assertEquals("order-events", ((KafkaTopicRuntimeRequest) engine.request).getTopic());
        assertEquals(KafkaTopicRuntimeResponse.class, engine.responseType);
        controller.kafkaConsumerGroupLag("kafka-primary", "order-group", 20);
        assertEquals(KafkaConsumerGroupLagListRequest.class, engine.request.getClass());
        assertEquals("order-group", ((KafkaConsumerGroupLagListRequest) engine.request).getGroupId());
        assertEquals(20, ((KafkaConsumerGroupLagListRequest) engine.request).getSize());
        assertEquals(KafkaConsumerGroupLagListResponse.class, engine.responseType);
        log.info("已覆盖四个工作区固定接口构造：calls={}", engine.calls);
    }

    @Test
    void shouldForwardDefaultAndExplicitAuditRanges() {
        CapturingEngine engine = new CapturingEngine();
        SmartMiddlewareOpsServerProperties properties = new SmartMiddlewareOpsServerProperties();
        CapturingAuditSearchService searchService = new CapturingAuditSearchService(properties);
        MiddlewareOpsController controller = controller(engine, properties, searchService);

        controller.redisAuditRecords(null, null, null, null, null);
        assertEquals(1, searchService.calls);
        assertEquals(MiddlewareType.REDIS, searchService.middlewareType);
        assertEquals(1, searchService.page);
        assertEquals(50, searchService.size);
        assertNull(searchService.timeRange);

        String[] ranges = {"1d", "7d", "30d", "90d"};
        for (String range : ranges) {
            controller.mysqlAuditRecords(2, 20, range, null, null);
            assertEquals(MiddlewareType.MYSQL, searchService.middlewareType);
            assertEquals(2, searchService.page);
            assertEquals(20, searchService.size);
            assertNotNull(searchService.timeRange);
        }
        assertEquals(5, searchService.calls);

        controller.kafkaAuditRecords(1, 20, null, "2026-08-01T00:00:00", "2026-08-02T00:00:00");
        assertEquals(6, searchService.calls);
        assertEquals(MiddlewareType.KAFKA, searchService.middlewareType);
        assertEquals("2026-08-01T00:00:00", searchService.timeRange.getFrom());
        assertEquals("2026-08-02T00:00:00", searchService.timeRange.getTo());
    }

    @Test
    void shouldRejectInvalidAuditRangeAndDeepPageBeforeSearch() {
        CapturingEngine engine = new CapturingEngine();
        SmartMiddlewareOpsServerProperties properties = new SmartMiddlewareOpsServerProperties();
        CapturingAuditSearchService searchService = new CapturingAuditSearchService(properties);
        MiddlewareOpsController controller = controller(engine, properties, searchService);

        assertAuditBadRequest(() -> controller.elasticsearchAuditRecords(1, 20, "unknown", null, null));
        assertAuditBadRequest(() -> controller.elasticsearchAuditRecords(1, 20, "7d", "2026-08-01T00:00:00", null));
        assertAuditBadRequest(() -> controller.redisAuditRecords(1, 20, null, "2026-08-01T00:00:00", null));
        assertAuditBadRequest(() -> controller.kafkaAuditRecords(1, 20, null, "bad", "2026-08-02T00:00:00"));
        assertAuditBadRequest(() -> controller.mysqlAuditRecords(1, 20, null,
                "2026-08-02T00:00:00", "2026-08-01T00:00:00"));
        assertAuditBadRequest(() -> controller.elasticsearchAuditRecords(1, 20, null,
                "2026-01-01T00:00:00", "2026-05-01T00:00:00"));
        assertAuditBadRequest(() -> controller.redisAuditRecords(101, 100, null, null, null));
        assertAuditBadRequest(() -> controller.kafkaAuditRecords(1, 201, null, null, null));
        assertEquals(0, searchService.calls);
        assertEquals(0, engine.calls);
    }

    @Test
    void shouldRejectInvalidSizeBeforeEngineInvocation() {
        CapturingEngine engine = new CapturingEngine();
        MiddlewareOpsController controller = controller(engine);

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> controller.kafkaTopics("default", null, 201));
        log.info("非法分页数量结果：status={}，message={}", exception.getStatus(), exception.getMessage());

        assertEquals(400, exception.getStatus().value());
        assertEquals("结果数量超出允许范围", exception.getMessage());
        assertEquals(0, engine.calls);
    }

    private void assertAuditBadRequest(Runnable invocation) {
        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class, invocation::run);
        assertEquals(400, exception.getStatus().value());
    }

    private void assertCatalogRequest(CapturingEngine engine, MiddlewareType middlewareType,
                                      MiddlewareOpsCapability capability, String resourceScope) {
        assertEquals(DatasourceCatalogRequest.class, engine.request.getClass());
        DatasourceCatalogRequest request = (DatasourceCatalogRequest) engine.request;
        assertEquals(middlewareType, request.getMiddlewareType());
        assertEquals(capability, request.getCapability());
        assertEquals(resourceScope, request.getResourceScope());
        assertEquals(DatasourceCatalogResponse.class, engine.responseType);
    }

    private MiddlewareOpsController controller(CapturingEngine engine) {
        SmartMiddlewareOpsServerProperties properties = new SmartMiddlewareOpsServerProperties();
        return controller(engine, properties, new CapturingAuditSearchService(properties));
    }

    private MiddlewareOpsController controller(CapturingEngine engine, SmartMiddlewareOpsServerProperties properties,
                                               MiddlewareOpsAuditSearchService auditSearchService) {
        return new MiddlewareOpsController(engine, auditSearchService, properties);
    }

    private static class CapturingAuditSearchService extends MiddlewareOpsAuditSearchService {

        private int calls;
        private MiddlewareType middlewareType;
        private int page;
        private int size;
        private MiddlewareOpsAuditTimeRange timeRange;

        @SuppressWarnings("unchecked")
        private CapturingAuditSearchService(SmartMiddlewareOpsServerProperties properties) {
            super(mock(ObjectProvider.class), properties);
        }

        @Override
        public MiddlewareOpsAuditPageResponse search(MiddlewareType middlewareType, int page, int size,
                                                     MiddlewareOpsAuditTimeRange timeRange) {
            calls++;
            this.middlewareType = middlewareType;
            this.page = page;
            this.size = size;
            this.timeRange = timeRange;
            return MiddlewareOpsAuditPageResponse.builder().total(0L).page(page).size(size)
                    .hasMore(Boolean.FALSE).items(Collections.emptyList()).build();
        }
    }

    private static class CapturingEngine implements MiddlewareOpsServerEngine {

        private final KafkaTopicListResponse response = KafkaTopicListResponse.builder()
                .items(Collections.emptyList()).build();
        private int calls;
        private MiddlewareOpsRequest request;
        private Class<?> responseType;

        @Override
        public <Res> Res execute(MiddlewareOpsRequest request, Class<Res> responseType) {
            calls++;
            this.request = request;
            this.responseType = responseType;
            if (DatasourceCatalogResponse.class == responseType) {
                return responseType.cast(DatasourceCatalogResponse.builder().items(Collections.emptyList()).build());
            }
            if (KafkaTopicListResponse.class == responseType) {
                return responseType.cast(response);
            }
            return null;
        }
    }
}
