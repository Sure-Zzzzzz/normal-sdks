package io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.cases;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.model.EsAuditRecord;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.EsAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.TestEsAuditHandler;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.provider.TestTraceIdProvider;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.provider.TestUserProvider;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggEvent;
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
 * ES 审计监听器端到端测试
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(
        classes = EsAuditListenerTestApplication.class,
        properties = {
                "test.es.audit.use-mock-provider=true"  // 使用Mock Provider
        }
)
public class EsAuditListenerEndToEndTest {

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

        // 准备：设置用户信息和 traceId
        testUserProvider.setClientId("test-client");
        testUserProvider.setClientType("platform");
        testUserProvider.setUserId("user-123");
        testUserProvider.setUsername("testuser");
        testTraceIdProvider.setTraceId("trace-query-123");

        // 构建查询事件
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
                .build();

        EsQueryEvent event = new EsQueryEvent(this, request, response, context);

        // 发布事件
        log.info("发布 ES 查询事件: index={}, total={}", "test-index", 100L);
        eventPublisher.publishEvent(event);

        // 等待异步处理
        boolean received = testAuditHandler.latch.await(5, TimeUnit.SECONDS);

        // 验证：事件已处理
        log.info("验证审计事件是否被处理");
        assertTrue(received, "Audit handler should receive the event");
        assertEquals(1, testAuditHandler.records.size(), "Should receive exactly one record");

        // 验证：审计记录内容
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

        log.info("ES query audit test passed: user={}, index={}, total={}, traceId={}",
                record.getUsername(), record.getIndexAlias(), record.getTotal(), record.getTraceId());
    }

    @Test
    public void testEsAggEventHandling() throws InterruptedException {
        log.info("========== 测试：ES 聚合事件处理 ==========");

        // 准备：设置用户信息
        testUserProvider.setClientId("agg-client");
        testUserProvider.setUserId("user-456");
        testTraceIdProvider.setTraceId("trace-agg-456");

        // 构建聚合事件
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
                .build();

        EsAggEvent event = new EsAggEvent(this, request, response, context);

        // 发布事件
        log.info("发布 ES 聚合事件: index={}", "agg-index");
        eventPublisher.publishEvent(event);

        // 等待处理
        boolean received = testAuditHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received);

        // 验证
        EsAuditRecord record = testAuditHandler.records.get(0);
        log.info("验证审计记录内容: {}", record);
        assertEquals("agg-client", record.getClientId());
        assertEquals("user-456", record.getUserId());
        assertEquals("agg-index", record.getIndexAlias());
        assertEquals("secondary", record.getDatasource());
        assertNull(record.getTotal());  // 聚合没有 total
        assertEquals(0, record.getReturnedSize());
        assertEquals(30L, record.getTook());
        assertEquals("trace-agg-456", record.getTraceId());

        log.info("ES agg audit test passed: user={}, index={}, traceId={}",
                record.getUserId(), record.getIndexAlias(), record.getTraceId());
    }

    @Test
    public void testWithoutProviders() throws InterruptedException {
        log.info("========== 测试：无 Provider 场景 ==========");

        // 准备：清空所有 Provider
        testUserProvider.reset();
        testTraceIdProvider.setTraceId(null);

        // 构建事件
        QueryRequest request = QueryRequest.builder().index("test").build();
        QueryResponse response = QueryResponse.builder().total(10L).took(5L).build();
        QueryExecutionContext context = QueryExecutionContext.builder()
                .actualIndices(new String[]{"test-2024"})
                .datasource("primary")
                .build();

        EsQueryEvent event = new EsQueryEvent(this, request, response, context);
        log.info("发布事件（Providers返回null）");
        eventPublisher.publishEvent(event);

        // 等待处理
        boolean received = testAuditHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received);

        // 验证：用户信息和 traceId 都为 null
        EsAuditRecord record = testAuditHandler.records.get(0);
        log.info("验证用户信息和 traceId 为 null");
        assertNull(record.getClientId());
        assertNull(record.getClientType());
        assertNull(record.getUserId());
        assertNull(record.getUsername());
        assertNull(record.getTraceId());

        // 但资源信息应该正常
        assertEquals("test", record.getIndexAlias());
        assertEquals("primary", record.getDatasource());
        assertEquals(10L, record.getTotal());

        log.info("No providers test passed");
    }
}
