package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.DowngradeLevel;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.QueryException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.MappingManager;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.ResolvedIndexConfig;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.executor.QueryExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.ops.middleware.audit.*;
import io.github.surezzzzzz.sdk.ops.middleware.configuration.SmartMiddlewareOpsServerProperties;
import io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant;
import io.github.surezzzzzz.sdk.ops.middleware.controller.response.MiddlewareOpsAuditPageResponse;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 固定审计读侧展示标签测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class MiddlewareOpsAuditSearchServiceTest {

    @Test
    void shouldUseLogicalDailyWriteIndexAndWildcardReadIndex() {
        assertEquals("request-1", MiddlewareOpsAuditIndexDefinition.documentId("request-1"));
        assertEquals("middleware-ops-audit", SmartMiddlewareOpsServerConstant.AUDIT_WRITE_INDEX);
        assertEquals("middleware-ops-audit-*", SmartMiddlewareOpsServerConstant.AUDIT_READ_INDEX_PATTERN);
    }

    @Test
    void shouldProjectOnlyStrictAuditDocumentFields() {
        MiddlewareOpsAuditContext context = MiddlewareOpsAuditContext.builder().elasticsearchIndex("orders-v1")
                .elasticsearchDsl("{\"query\":{\"match_all\":{}}}").mysqlSql("SELECT id FROM orders")
                .redisKey("order:7").redisField("status").kafkaTopic("order-events").kafkaGroupId("order-group")
                .page(2).size(50).offset(10L).build();
        MiddlewareOpsAuditEvent event = MiddlewareOpsAuditEvent.builder().occurredAt(Instant.parse("2026-08-04T08:00:00Z"))
                .subject("ops-user").capability(MiddlewareOpsCapability.MYSQL_SELECT).middlewareType(MiddlewareType.MYSQL)
                .datasourceKey("mysql84-ops").clusterTag("MySQL 8.4 运维库").resourceDigest("sha256:fixture")
                .context(context).status(200).durationMillis(45L).requestId("request-2").build();

        Map<String, Object> document = MiddlewareOpsAuditIndexDefinition.document(event);
        log.info("审计文档投影字段：{}", document.keySet());

        assertEquals(20, document.size());
        assertEquals("request-2", document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_ID));
        assertEquals("2026-08-04T08:00:00Z", document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_OCCURRED_AT));
        assertEquals("ops-user", document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_SUBJECT));
        assertEquals("MYSQL_SELECT", document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_CAPABILITY));
        assertEquals("mysql", document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_MIDDLEWARE_TYPE));
        assertEquals("mysql84-ops", document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_DATASOURCE_KEY));
        assertEquals("MySQL 8.4 运维库", document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_CLUSTER_TAG));
        assertEquals("sha256:fixture", document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_RESOURCE_DIGEST));
        assertEquals(200, document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_HTTP_STATUS));
        assertEquals(45L, document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_DURATION_MILLIS));
        assertEquals("orders-v1", document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_ELASTICSEARCH_INDEX));
        assertEquals("{\"query\":{\"match_all\":{}}}",
                document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_ELASTICSEARCH_DSL));
        assertEquals("SELECT id FROM orders", document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_MYSQL_SQL));
        assertEquals("order:7", document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_REDIS_KEY));
        assertEquals("status", document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_REDIS_FIELD));
        assertEquals("order-events", document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_KAFKA_TOPIC));
        assertEquals("order-group", document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_KAFKA_GROUP_ID));
        assertEquals(2, document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_PAGE));
        assertEquals(50, document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_SIZE));
        assertEquals(10L, document.get(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_OFFSET));
        assertFalse(document.containsKey("url"));
        assertFalse(document.containsKey("username"));
        assertFalse(document.containsKey("password"));
        assertFalse(document.containsKey("response"));
        assertFalse(document.containsKey("exception"));
    }

    @Test
    void shouldReadPersistedClusterTagWithoutBackfillingCurrentConfiguration() {
        TestQueryExecutor queryExecutor = new TestQueryExecutor(response(item(
                "request-1", "REDIS", "default", "蓝色缓存集群")), null);
        ObjectProvider<QueryExecutor> provider = provider(queryExecutor);

        MiddlewareOpsAuditSearchService service = new MiddlewareOpsAuditSearchService(provider, properties());
        MiddlewareOpsAuditPageResponse response = service.search(MiddlewareType.REDIS, 1, 50);
        log.info("审计读侧标签：clusterTag={}", response.getItems().get(0).getClusterTag());

        assertEquals(1L, response.getTotal());
        assertEquals("蓝色缓存集群", response.getItems().get(0).getClusterTag());
        assertEquals(SmartMiddlewareOpsServerConstant.AUDIT_READ_INDEX_PATTERN, queryExecutor.request.getIndex());
        assertEquals(20, queryExecutor.request.getFields().size());
        assertEquals(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_CLUSTER_TAG,
                queryExecutor.request.getFields().get(6));
        assertEquals(SmartMiddlewareOpsServerConstant.AUDIT_SORT_FIELD_OCCURRED_AT,
                queryExecutor.request.getPagination().getSort().get(0).getField());
        assertEquals(SmartMiddlewareOpsServerConstant.AUDIT_SORT_ORDER_DESC,
                queryExecutor.request.getPagination().getSort().get(0).getOrder());
        assertEquals(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_MIDDLEWARE_TYPE,
                queryExecutor.request.getQuery().getField());
        assertEquals("eq", queryExecutor.request.getQuery().getOp());
        assertEquals("redis", queryExecutor.request.getQuery().getValue());
    }

    @Test
    void shouldReturnSearchInjectedDefaultRangeForInitialAuditRequest() {
        TestQueryExecutor queryExecutor = new TestQueryExecutor(response(item(
                "request-default", "redis", "default", null)), null, true);

        MiddlewareOpsAuditPageResponse response = new MiddlewareOpsAuditSearchService(
                provider(queryExecutor), properties()).search(MiddlewareType.REDIS, 1, 50);

        assertEquals("2026-08-01T00:00:00", response.getFrom());
        assertEquals("2026-08-31T00:00:00", response.getTo());
        assertEquals(response.getFrom(), queryExecutor.request.getDateRange().getFrom());
        assertEquals(response.getTo(), queryExecutor.request.getDateRange().getTo());
    }

    @Test
    void shouldForwardExplicitAuditRangeWithoutOverwritingIt() {
        MiddlewareOpsAuditTimeRange timeRange = MiddlewareOpsAuditTimeRange.builder()
                .from("2026-08-01T00:00:00").to("2026-08-02T00:00:00").build();
        TestQueryExecutor queryExecutor = new TestQueryExecutor(response(item(
                "request-range", "redis", "default", null)), null);

        MiddlewareOpsAuditPageResponse response = new MiddlewareOpsAuditSearchService(
                provider(queryExecutor), properties()).search(MiddlewareType.REDIS, 2, 50, timeRange);

        assertEquals("2026-08-01T00:00:00", queryExecutor.request.getDateRange().getFrom());
        assertEquals("2026-08-02T00:00:00", queryExecutor.request.getDateRange().getTo());
        assertEquals("2026-08-01T00:00:00", response.getFrom());
        assertEquals("2026-08-02T00:00:00", response.getTo());
    }

    @Test
    void shouldFilterEveryFixedWorkspaceAuditByLowerCaseType() {
        for (MiddlewareType middlewareType : MiddlewareType.values()) {
            TestQueryExecutor queryExecutor = new TestQueryExecutor(response(item(
                    "request-" + middlewareType.getCode(), middlewareType.getCode(), "default", null)), null);

            new MiddlewareOpsAuditSearchService(provider(queryExecutor), properties()).search(middlewareType, 1, 50);
            log.info("审计工作区过滤：middlewareType={}，value={}", middlewareType,
                    queryExecutor.request.getQuery().getValue());

            assertEquals(middlewareType.getCode(), queryExecutor.request.getQuery().getValue());
        }
    }

    @Test
    void shouldKeepRequestedPageSizeAndOnlyExposeAuditWhitelist() {
        Map<String, Object> item = item("request-2", "redis", "default", "蓝色缓存集群");
        item.put("password", "must-not-leak");
        TestQueryExecutor queryExecutor = new TestQueryExecutor(QueryResponse.builder().total(8L).page(2).size(3)
                .items(Collections.singletonList(item)).build(), null);

        MiddlewareOpsAuditPageResponse response = new MiddlewareOpsAuditSearchService(
                provider(queryExecutor), properties()).search(MiddlewareType.REDIS, 2, 3);

        assertEquals(2, queryExecutor.request.getPagination().getPage());
        assertEquals(3, queryExecutor.request.getPagination().getSize());
        assertEquals(8L, response.getTotal());
        assertEquals(2, response.getPage());
        assertEquals(3, response.getSize());
        assertTrue(response.getHasMore());
        assertEquals("request-2", response.getItems().get(0).getId());
        assertEquals("redis", response.getItems().get(0).getMiddlewareType());
        assertEquals("default", response.getItems().get(0).getDatasourceKey());
        assertEquals("蓝色缓存集群", response.getItems().get(0).getClusterTag());
        assertEquals(20, queryExecutor.request.getFields().size());
        assertFalse(queryExecutor.request.getFields().contains("password"));
    }

    @Test
    void shouldForwardSearchProcessedContextWithoutLocalMasking() {
        Map<String, Object> item = item("request-context", "redis", "default", "蓝色缓存集群");
        item.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_ELASTICSEARCH_INDEX, "or****v1");
        item.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_ELASTICSEARCH_DSL, "{\"query\":{\"m****{}}}");
        item.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_MYSQL_SQL, "SELECT id F****ders");
        item.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_REDIS_KEY, "or****:7");
        item.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_REDIS_FIELD, "st****us");
        item.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_KAFKA_TOPIC, "or****ts");
        item.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_KAFKA_GROUP_ID, "or****up");
        item.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_PAGE, 2);
        item.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_SIZE, 50);
        item.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_OFFSET, 10L);
        item.put("password", "must-not-leak");

        TestQueryExecutor queryExecutor = new TestQueryExecutor(response(item), null);
        MiddlewareOpsAuditPageResponse response = new MiddlewareOpsAuditSearchService(
                provider(queryExecutor), properties()).search(MiddlewareType.REDIS, 1, 50);

        assertEquals("or****v1", response.getItems().get(0).getElasticsearchIndex());
        assertEquals("{\"query\":{\"m****{}}}", response.getItems().get(0).getElasticsearchDsl());
        assertEquals("SELECT id F****ders", response.getItems().get(0).getMysqlSql());
        assertEquals("or****:7", response.getItems().get(0).getRedisKey());
        assertEquals("st****us", response.getItems().get(0).getRedisField());
        assertEquals("or****ts", response.getItems().get(0).getKafkaTopic());
        assertEquals("or****up", response.getItems().get(0).getKafkaGroupId());
        assertEquals(2, response.getItems().get(0).getPage());
        assertEquals(50, response.getItems().get(0).getSize());
        assertEquals(10L, response.getItems().get(0).getOffset());
        assertTrue(queryExecutor.request.getFields().contains(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_ELASTICSEARCH_DSL));
        assertTrue(queryExecutor.request.getFields().contains(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_MYSQL_SQL));
        assertFalse(queryExecutor.request.getFields().contains("password"));
    }

    @Test
    void shouldMarkExactLastOffsetPageWithoutMoreResults() {
        TestQueryExecutor queryExecutor = new TestQueryExecutor(response(item(
                "request-3", "redis", "default", null)), null);

        MiddlewareOpsAuditPageResponse response = new MiddlewareOpsAuditSearchService(
                provider(queryExecutor), properties()).search(MiddlewareType.REDIS, 1, 1);

        assertEquals(1L, response.getTotal());
        assertFalse(response.getHasMore());
    }

    @Test
    void shouldKeepHistoricalClusterTagNullWhenDocumentDoesNotContainIt() {
        TestQueryExecutor queryExecutor = new TestQueryExecutor(response(item(
                "legacy-request", null, "default", null)), null);

        MiddlewareOpsAuditPageResponse response = new MiddlewareOpsAuditSearchService(
                provider(queryExecutor), properties()).search(MiddlewareType.REDIS, 1, 50);
        log.info("历史审计标签：clusterTag={}", response.getItems().get(0).getClusterTag());

        assertNull(response.getItems().get(0).getClusterTag());
    }

    @Test
    void shouldReturnEmptyPageWhenAuditReadIsDisabled() {
        SmartMiddlewareOpsServerProperties properties = properties();
        properties.getAudit().setEnabled(false);

        MiddlewareOpsAuditPageResponse response = new MiddlewareOpsAuditSearchService(
                provider(null), properties).search(MiddlewareType.REDIS, 2, 20);

        assertEquals(0L, response.getTotal());
        assertEquals(2, response.getPage());
        assertEquals(20, response.getSize());
        assertFalse(response.getHasMore());
        assertEquals(0, response.getItems().size());
    }

    @Test
    void shouldReturnUnavailableWhenAuditQueryFails() {
        TestQueryExecutor queryExecutor = new TestQueryExecutor(null,
                new IllegalStateException("backend unavailable"));

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> new MiddlewareOpsAuditSearchService(provider(queryExecutor), properties()).search(MiddlewareType.REDIS, 1, 50));

        assertEquals(503, exception.getStatus().value());
        assertEquals("审计查询暂不可用", exception.getMessage());
    }

    @Test
    void shouldReturnUnavailableWhenAuditIsEnabledButSearchStarterIsMissing() {
        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> new MiddlewareOpsAuditSearchService(provider(null), properties()).search(MiddlewareType.REDIS, 1, 50));

        assertEquals(503, exception.getStatus().value());
        assertEquals("审计查询暂不可用", exception.getMessage());
    }

    private ObjectProvider<QueryExecutor> provider(QueryExecutor queryExecutor) {
        ObjectProvider<QueryExecutor> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(queryExecutor);
        return provider;
    }

    private SmartMiddlewareOpsServerProperties properties() {
        SmartMiddlewareOpsServerProperties properties = new SmartMiddlewareOpsServerProperties();
        properties.getAudit().setEnabled(true);
        return properties;
    }

    private Map<String, Object> item(String id, String middlewareType, String datasourceKey, String clusterTag) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_ID, id);
        item.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_MIDDLEWARE_TYPE, middlewareType);
        item.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_DATASOURCE_KEY, datasourceKey);
        if (clusterTag != null) {
            item.put(SmartMiddlewareOpsServerConstant.AUDIT_FIELD_CLUSTER_TAG, clusterTag);
        }
        return item;
    }

    private QueryResponse response(Map<String, Object> item) {
        return QueryResponse.builder().total(1L).page(1).size(50).items(Collections.singletonList(item)).build();
    }

    private static class TestQueryExecutor extends QueryExecutor {

        private final QueryResponse response;
        private final RuntimeException failure;
        private final boolean injectDefaultDateRange;
        private QueryRequest request;

        private TestQueryExecutor(QueryResponse response, RuntimeException failure) {
            this(response, failure, false);
        }

        private TestQueryExecutor(QueryResponse response, RuntimeException failure, boolean injectDefaultDateRange) {
            this.response = response;
            this.failure = failure;
            this.injectDefaultDateRange = injectDefaultDateRange;
            this.mappingManager = new MappingManager(null, null, null, null) {
                @Override
                public ResolvedIndexConfig resolveIndexConfig(String requestIndex) {
                    return null;
                }

                @Override
                public IndexMetadata getMetadata(ResolvedIndexConfig resolvedIndexConfig) {
                    return null;
                }
            };
        }

        @Override
        protected void validateRequest(QueryRequest request) {
            assertEquals(SmartMiddlewareOpsServerConstant.AUDIT_READ_INDEX_PATTERN, request.getIndex());
            if (injectDefaultDateRange) {
                assertNull(request.getDateRange());
                request.setDateRange(QueryRequest.DateRange.builder().from("2026-08-01T00:00:00")
                        .to("2026-08-31T00:00:00").build());
            }
        }

        @Override
        protected boolean needsDowngradeRetry(QueryRequest request, ResolvedIndexConfig resolvedIndexConfig,
                                              IndexMetadata metadata) {
            return false;
        }

        @Override
        protected QueryResponse executeOnce(QueryRequest request, ResolvedIndexConfig resolvedIndexConfig,
                                            IndexMetadata metadata, long startTime, DowngradeLevel level)
                throws IOException {
            this.request = request;
            if (failure != null) {
                throw failure;
            }
            return response;
        }

        @Override
        protected String getIndex(QueryRequest request) {
            return request.getIndex();
        }

        @Override
        protected RuntimeException wrapIoException(IOException exception) {
            return new QueryException(null, "测试查询失败", exception);
        }
    }
}
