package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.executor.AggExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.executor.QueryExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 事件发布端到端测试
 *
 * <p>测试场景：
 * <ul>
 *   <li>查询执行后发布 EsQueryEvent</li>
 *   <li>聚合执行后发布 EsAggEvent</li>
 *   <li>事件包含完整的请求、响应、上下文信息</li>
 *   <li>事件监听器能正常接收事件</li>
 * </ul>
 *
 * @author surezzzzzz
 * @since 1.2.0
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
public class EventPublishEndToEndTest {

    private static final String USER_INDEX = "test_user_index";

    @Autowired
    private QueryExecutor queryExecutor;

    @Autowired
    private AggExecutor aggExecutor;

    @Autowired
    private TestEventListener testEventListener;

    @BeforeAll
    static void setupAll(@Autowired SimpleElasticsearchRouteRegistry registry) throws Exception {
        log.info("========== 开始准备事件测试数据 ==========");

        RestHighLevelClient client = registry.getHighLevelClient("primary");

        // 创建 user 索引
        createUserIndex(client);

        log.info("========== 事件测试数据准备完成 ==========");
    }

    private static void createUserIndex(RestHighLevelClient client) throws Exception {
        // 删除旧索引
        if (client.indices().exists(new GetIndexRequest(USER_INDEX), RequestOptions.DEFAULT)) {
            client.indices().delete(new DeleteIndexRequest(USER_INDEX), RequestOptions.DEFAULT);
        }

        // 创建索引
        CreateIndexRequest request = new CreateIndexRequest(USER_INDEX);
        request.mapping(
                "{" +
                        "  \"properties\": {" +
                        "    \"username\": {" +
                        "      \"type\": \"text\"," +
                        "      \"fields\": {" +
                        "        \"keyword\": {" +
                        "          \"type\": \"keyword\"" +
                        "        }" +
                        "      }" +
                        "    }," +
                        "    \"name\": {\"type\": \"keyword\"}," +
                        "    \"age\": {\"type\": \"integer\"}," +
                        "    \"city\": {\"type\": \"keyword\"}," +
                        "    \"phone\": {\"type\": \"keyword\"}," +
                        "    \"password\": {\"type\": \"keyword\"}," +
                        "    \"created_at\": {\"type\": \"date\"}" +
                        "  }" +
                        "}",
                org.elasticsearch.xcontent.XContentType.JSON
        );
        client.indices().create(request, RequestOptions.DEFAULT);
        log.info("✓ 已创建索引: {}", USER_INDEX);

        // 插入测试数据
        List<Map<String, Object>> users = Arrays.asList(
                createUser("alice", "张三", 25, "北京", "13800138000", "password123"),
                createUser("bob", "李四", 30, "上海", "13900139000", "password456"),
                createUser("charlie", "王五", 28, "北京", "13700137000", "password789")
        );

        for (int i = 0; i < users.size(); i++) {
            IndexRequest indexRequest = new IndexRequest(USER_INDEX)
                    .id(String.valueOf(i + 1))
                    .source(users.get(i));
            client.index(indexRequest, RequestOptions.DEFAULT);
        }

        Thread.sleep(2000);
        log.info("✓ 已插入 {} 条测试数据到 {}", users.size(), USER_INDEX);
    }

    private static Map<String, Object> createUser(String username, String name, int age, String city, String phone, String password) {
        Map<String, Object> user = new HashMap<String, Object>();
        user.put("username", username);
        user.put("name", name);
        user.put("age", age);
        user.put("city", city);
        user.put("phone", phone);
        user.put("password", password);
        user.put("created_at", System.currentTimeMillis());
        return user;
    }

    @Test
    public void testQueryEventPublish() throws InterruptedException {
        // 准备：重置监听器
        testEventListener.reset();

        // 执行查询（使用配置中的 test_user 索引，使用 city 字段查询）
        QueryRequest request = QueryRequest.builder()
                .index("test_user")
                .query(QueryCondition.builder()
                        .field("city")
                        .op("eq")
                        .value("北京")
                        .build())
                .pagination(PaginationInfo.builder()
                        .type("offset")
                        .page(1)
                        .size(10)
                        .build())
                .build();

        QueryResponse response = queryExecutor.execute(request);

        // 等待事件处理（异步）
        boolean received = testEventListener.queryEventLatch.await(5, TimeUnit.SECONDS);

        // 验证：事件已发布并被监听到
        assertTrue(received, "Query event should be received");
        assertEquals(1, testEventListener.queryEvents.size(), "Should receive exactly one query event");

        // 验证：事件内容
        EsQueryEvent event = testEventListener.queryEvents.get(0);
        assertNotNull(event, "Event should not be null");
        assertNotNull(event.getRequest(), "Event request should not be null");
        assertNotNull(event.getResponse(), "Event response should not be null");
        assertNotNull(event.getContext(), "Event context should not be null");
        assertNotNull(event.getTimestamp(), "Event timestamp should not be null");

        // 验证：请求信息
        assertEquals("test_user", event.getRequest().getIndex(), "Index should match");
        assertEquals("city", event.getRequest().getQuery().getField(), "Query field should match");

        // 验证：响应信息
        assertEquals(response.getTotal(), event.getResponse().getTotal(), "Total should match");
        assertEquals(response.getItems().size(), event.getResponse().getItems().size(), "Items size should match");

        // 验证：上下文信息
        assertNotNull(event.getContext().getActualIndices(), "Actual indices should not be null");
        assertNotNull(event.getContext().getDatasource(), "Datasource should not be null");
        assertTrue(event.getContext().getActualIndices().length > 0, "Should have at least one actual index");
        assertEquals(0, event.getContext().getDowngradeLevel(), "Downgrade level should be 0 for simple query");
        // sourceType 由 executor 直接调用时为 null（未经过 endpoint 设置）

        log.info("Query event test passed: index={}, datasource={}, downgradeLevel={}, sourceType={}, total={}, took={}ms",
                event.getRequest().getIndex(),
                event.getContext().getDatasource(),
                event.getContext().getDowngradeLevel(),
                event.getContext().getSourceType(),
                event.getResponse().getTotal(),
                event.getResponse().getTook());
    }

    @Test
    public void testAggEventPublish() throws InterruptedException {
        // 准备：重置监听器
        testEventListener.reset();

        // 执行聚合（使用配置中的 test_user 索引，使用 username.keyword 字段）
        AggRequest request = AggRequest.builder()
                .index("test_user")
                .aggs(Arrays.asList(
                        AggDefinition.builder()
                                .name("username_count")
                                .type("terms")
                                .field("username.keyword")
                                .build()
                ))
                .build();

        AggResponse response = aggExecutor.execute(request);

        // 等待事件处理（异步）
        boolean received = testEventListener.aggEventLatch.await(5, TimeUnit.SECONDS);

        // 验证：事件已发布并被监听到
        assertTrue(received, "Agg event should be received");
        assertEquals(1, testEventListener.aggEvents.size(), "Should receive exactly one agg event");

        // 验证：事件内容
        EsAggEvent event = testEventListener.aggEvents.get(0);
        assertNotNull(event, "Event should not be null");
        assertNotNull(event.getRequest(), "Event request should not be null");
        assertNotNull(event.getResponse(), "Event response should not be null");
        assertNotNull(event.getContext(), "Event context should not be null");
        assertNotNull(event.getTimestamp(), "Event timestamp should not be null");

        // 验证：请求信息
        assertEquals("test_user", event.getRequest().getIndex(), "Index should match");
        assertEquals(1, event.getRequest().getAggs().size(), "Should have one aggregation");
        assertEquals("username_count", event.getRequest().getAggs().get(0).getName(), "Agg name should match");

        // 验证：响应信息
        assertNotNull(event.getResponse().getAggregations(), "Aggregations should not be null");
        assertTrue(event.getResponse().getAggregations().containsKey("username_count"), "Should contain username_count aggregation");

        // 验证：上下文信息
        assertNotNull(event.getContext().getActualIndices(), "Actual indices should not be null");
        assertNotNull(event.getContext().getDatasource(), "Datasource should not be null");
        assertTrue(event.getContext().getActualIndices().length > 0, "Should have at least one actual index");
        assertEquals(0, event.getContext().getDowngradeLevel(), "Downgrade level should be 0");

        log.info("Agg event test passed: index={}, datasource={}, downgradeLevel={}, took={}ms",
                event.getRequest().getIndex(),
                event.getContext().getDatasource(),
                event.getContext().getDowngradeLevel(),
                event.getResponse().getTook());
    }

    @Test
    public void testMultipleEventsPublish() throws InterruptedException {
        // 准备：重置监听器
        testEventListener.reset();

        // 执行多次查询（使用配置中的 test_user 索引）
        for (int i = 0; i < 3; i++) {
            QueryRequest request = QueryRequest.builder()
                    .index("test_user")
                    .pagination(PaginationInfo.builder()
                            .type("offset")
                            .page(1)
                            .size(5)
                            .build())
                    .build();
            queryExecutor.execute(request);
        }

        // 等待所有事件处理
        Thread.sleep(1000);

        // 验证：收到3个事件
        assertEquals(3, testEventListener.queryEvents.size(), "Should receive 3 query events");

        log.info("Multiple events test passed: received {} events", testEventListener.queryEvents.size());
    }

    /**
     * 测试事件监听器
     */
    @Component
    @Slf4j
    public static class TestEventListener {

        public final List<EsQueryEvent> queryEvents = new ArrayList<>();
        public final List<EsAggEvent> aggEvents = new ArrayList<>();
        public final List<EsQueryErrorEvent> queryErrorEvents = new ArrayList<>();
        public final List<EsAggErrorEvent> aggErrorEvents = new ArrayList<>();
        public CountDownLatch queryEventLatch = new CountDownLatch(1);
        public CountDownLatch aggEventLatch = new CountDownLatch(1);
        public CountDownLatch queryErrorEventLatch = new CountDownLatch(1);

        @EventListener
        public void onEsQueryEvent(EsQueryEvent event) {
            log.info("Received EsQueryEvent: index={}, total={}, datasource={}, downgradeLevel={}, sourceType={}",
                    event.getRequest().getIndex(),
                    event.getResponse().getTotal(),
                    event.getContext().getDatasource(),
                    event.getContext().getDowngradeLevel(),
                    event.getContext().getSourceType());

            queryEvents.add(event);
            queryEventLatch.countDown();
        }

        @EventListener
        public void onEsAggEvent(EsAggEvent event) {
            log.info("Received EsAggEvent: index={}, datasource={}, downgradeLevel={}",
                    event.getRequest().getIndex(),
                    event.getContext().getDatasource(),
                    event.getContext().getDowngradeLevel());

            aggEvents.add(event);
            aggEventLatch.countDown();
        }

        @EventListener
        public void onEsQueryErrorEvent(EsQueryErrorEvent event) {
            log.info("Received EsQueryErrorEvent: index={}, datasource={}, error={}",
                    event.getRequest().getIndex(),
                    event.getDatasource(),
                    event.getError().getMessage());

            queryErrorEvents.add(event);
            queryErrorEventLatch.countDown();
        }

        @EventListener
        public void onEsAggErrorEvent(EsAggErrorEvent event) {
            log.info("Received EsAggErrorEvent: index={}, datasource={}, error={}",
                    event.getRequest().getIndex(),
                    event.getDatasource(),
                    event.getError().getMessage());

            aggErrorEvents.add(event);
        }

        public void reset() {
            queryEvents.clear();
            aggEvents.clear();
            queryErrorEvents.clear();
            aggErrorEvents.clear();
            queryEventLatch = new CountDownLatch(1);
            aggEventLatch = new CountDownLatch(1);
            queryErrorEventLatch = new CountDownLatch(1);
        }
    }
}
