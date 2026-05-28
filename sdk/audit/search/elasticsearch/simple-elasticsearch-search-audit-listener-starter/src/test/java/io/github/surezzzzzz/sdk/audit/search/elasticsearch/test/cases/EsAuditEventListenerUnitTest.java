package io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.cases;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.model.EsAuditRecord;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.EsAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.TestEsAuditHandler;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.provider.TestTraceIdProvider;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.provider.TestUserProvider;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.model.AggExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.model.QueryExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ES 审计事件监听器单元测试
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(
        classes = EsAuditListenerTestApplication.class,
        properties = {
                "test.es.audit.use-mock-provider=true",
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.enabled=false"
        }
)
public class EsAuditEventListenerUnitTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TestEsAuditHandler testAuditHandler;

    @Autowired
    private TestUserProvider testUserProvider;

    @Autowired
    private TestTraceIdProvider testTraceIdProvider;

    @BeforeEach
    public void setUp() {
        log.info("========== 开始准备 ES 审计测试 ==========");
        testAuditHandler.reset();
        testUserProvider.reset();
        testTraceIdProvider.reset();
        log.info("========== ES 审计测试准备完成 ==========");
    }

    @Test
    public void testEsQueryEventHandling() throws InterruptedException {
        log.info("========== 测试：ES 查询事件处理 ==========");

        testUserProvider.setClientId("test-client");
        testUserProvider.setClientType("platform");
        testUserProvider.setUserId("user-123");
        testUserProvider.setUsername("testuser");
        testTraceIdProvider.setTraceId("trace-query-123");

        QueryRequest request = QueryRequest.builder()
                .index("test-index")
                .build();

        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        QueryResponse response = QueryResponse.builder()
                .total(100L)
                .items(items)
                .took(50L)
                .build();

        QueryExecutionContext context = QueryExecutionContext.builder()
                .actualIndices(new String[]{"test-index-2024"})
                .datasource("primary")
                .downgradeLevel(0)
                .sourceType("QUERY_API")
                .build();

        eventPublisher.publishEvent(new EsQueryEvent(this, request, response, context));

        boolean received = testAuditHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Audit handler should receive the event");
        assertEquals(1, testAuditHandler.records.size());

        EsAuditRecord record = testAuditHandler.records.get(0);
        log.info("验证审计记录内容: {}", record);
        assertEquals("test-client", record.getClientId());
        assertEquals("platform", record.getClientType());
        assertEquals("user-123", record.getUserId());
        assertEquals("testuser", record.getUsername());
        assertEquals("test-index", record.getIndexAlias());
        assertArrayEquals(new String[]{"test-index-2024"}, record.getActualIndices());
        assertEquals("primary", record.getDatasource());
        assertEquals(100L, record.getTotal());
        assertEquals(0, record.getReturnedSize());
        assertEquals(50L, record.getTook());
        assertEquals("trace-query-123", record.getTraceId());
        assertNotNull(record.getTimestamp());
        // 1.0.2 新字段
        assertEquals("success", record.getResult());
        assertEquals(0, record.getDowngradeLevel());
        assertEquals("QUERY_API", record.getSourceType());
        assertNull(record.getErrorMessage());

        log.info("testEsQueryEventHandling passed");
    }

    @Test
    public void testEsAggEventHandling() throws InterruptedException {
        log.info("========== 测试：ES 聚合事件处理 ==========");

        testUserProvider.setClientId("agg-client");
        testUserProvider.setUserId("user-456");
        testTraceIdProvider.setTraceId("trace-agg-456");

        AggRequest request = AggRequest.builder()
                .index("agg-index")
                .build();

        AggResponse response = AggResponse.builder()
                .aggregations(new HashMap<String, Object>())
                .took(30L)
                .build();

        AggExecutionContext context = AggExecutionContext.builder()
                .actualIndices(new String[]{"agg-index-2024"})
                .datasource("secondary")
                .downgradeLevel(1)
                .sourceType("NL_API")
                .build();

        eventPublisher.publishEvent(new EsAggEvent(this, request, response, context));

        boolean received = testAuditHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received);

        EsAuditRecord record = testAuditHandler.records.get(0);
        log.info("验证审计记录内容: {}", record);
        assertEquals("agg-client", record.getClientId());
        assertEquals("user-456", record.getUserId());
        assertEquals("agg-index", record.getIndexAlias());
        assertEquals("secondary", record.getDatasource());
        assertNull(record.getTotal());
        assertEquals(0, record.getReturnedSize());
        assertEquals(30L, record.getTook());
        assertEquals("trace-agg-456", record.getTraceId());
        // 1.0.2 新字段
        assertEquals("success", record.getResult());
        assertEquals(1, record.getDowngradeLevel());
        assertEquals("NL_API", record.getSourceType());
        assertNull(record.getErrorMessage());

        log.info("testEsAggEventHandling passed");
    }

    @Test
    public void testEsQueryErrorEventHandling() throws InterruptedException {
        log.info("========== 测试：ES 查询失败事件处理 ==========");

        testUserProvider.setClientId("test-client");
        testUserProvider.setUserId("user-123");
        testTraceIdProvider.setTraceId("trace-err-001");

        QueryRequest request = QueryRequest.builder()
                .index("test-index")
                .build();
        request.setSourceType("QUERY_API");

        eventPublisher.publishEvent(new EsQueryErrorEvent(
                this, request, new RuntimeException("connection refused"), "primary"));

        boolean received = testAuditHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Audit handler should receive error event");

        EsAuditRecord record = testAuditHandler.records.get(0);
        log.info("验证查询失败审计记录: {}", record);
        assertEquals("test-client", record.getClientId());
        assertEquals("test-index", record.getIndexAlias());
        assertEquals("primary", record.getDatasource());
        assertNull(record.getActualIndices());
        assertNull(record.getTotal());
        assertNull(record.getTook());
        assertEquals("trace-err-001", record.getTraceId());
        // 1.0.2 新字段
        assertEquals("failure", record.getResult());
        assertEquals(0, record.getDowngradeLevel());
        assertEquals("QUERY_API", record.getSourceType());
        assertEquals("connection refused", record.getErrorMessage());

        log.info("testEsQueryErrorEventHandling passed");
    }

    @Test
    public void testEsAggErrorEventHandling() throws InterruptedException {
        log.info("========== 测试：ES 聚合失败事件处理 ==========");

        testUserProvider.setClientId("agg-client");
        testTraceIdProvider.setTraceId("trace-err-002");

        AggRequest request = AggRequest.builder()
                .index("agg-index")
                .build();
        request.setSourceType("NL_API");

        eventPublisher.publishEvent(new EsAggErrorEvent(
                this, request, new RuntimeException("agg execution failed"), null));

        boolean received = testAuditHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Audit handler should receive agg error event");

        EsAuditRecord record = testAuditHandler.records.get(0);
        log.info("验证聚合失败审计记录: {}", record);
        assertEquals("agg-client", record.getClientId());
        assertEquals("agg-index", record.getIndexAlias());
        assertNull(record.getDatasource());   // 路由前失败，datasource 为 null
        assertNull(record.getActualIndices());
        assertEquals("trace-err-002", record.getTraceId());
        // 1.0.2 新字段
        assertEquals("failure", record.getResult());
        assertEquals(0, record.getDowngradeLevel());
        assertEquals("NL_API", record.getSourceType());
        assertEquals("agg execution failed", record.getErrorMessage());

        log.info("testEsAggErrorEventHandling passed");
    }

    @Test
    public void testWithoutProviders() throws InterruptedException {
        log.info("========== 测试：无 Provider 场景 ==========");

        testUserProvider.reset();
        testTraceIdProvider.setTraceId(null);

        QueryRequest request = QueryRequest.builder().index("test").build();
        QueryResponse response = QueryResponse.builder().total(10L).took(5L).build();
        QueryExecutionContext context = QueryExecutionContext.builder()
                .actualIndices(new String[]{"test-2024"})
                .datasource("primary")
                .downgradeLevel(0)
                .sourceType("EXPRESSION_API")
                .build();

        eventPublisher.publishEvent(new EsQueryEvent(this, request, response, context));

        boolean received = testAuditHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received);

        EsAuditRecord record = testAuditHandler.records.get(0);
        // 用户信息因无 Provider 而为 null
        assertNull(record.getClientId());
        assertNull(record.getClientType());
        assertNull(record.getUserId());
        assertNull(record.getUsername());
        assertNull(record.getTraceId());
        // 请求上下文字段应正确填充
        assertEquals("test", record.getIndexAlias());
        assertArrayEquals(new String[]{"test-2024"}, record.getActualIndices());
        assertEquals("primary", record.getDatasource());
        assertEquals(10L, record.getTotal());
        assertEquals(0, record.getReturnedSize());
        assertEquals(5L, record.getTook());
        // 1.0.2 新字段
        assertEquals("success", record.getResult());
        assertEquals(0, record.getDowngradeLevel());
        assertEquals("EXPRESSION_API", record.getSourceType());
        assertNull(record.getErrorMessage());
        assertNotNull(record.getTimestamp());

        log.info("testWithoutProviders passed");
    }
}