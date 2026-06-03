package io.github.surezzzzzz.sdk.metrics.elasticsearch.search.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.metrics.elasticsearch.search.test.SimpleElasticsearchSearchMetricsTestApplication;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Elasticsearch Search Metrics 端到端测试
 * <p>
 * 通过真实 HTTP 请求 → ES 查询 → 事件发布 → 指标记录，验证指标采集的完整链路。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchMetricsTestApplication.class)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ElasticsearchSearchMetricsEndToEndTest {

    private static final String INDEX = "test_metrics_index";
    private static final String DATASOURCE = "primary";
    // spring.application.name 配置值，与 application.yml 保持一致
    private static final String EXPECTED_ME = "es-search-metrics-test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SimpleElasticsearchRouteRegistry registry;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void setupAll(@Autowired SimpleElasticsearchRouteRegistry registry) throws Exception {
        RestHighLevelClient client = registry.getHighLevelClient(DATASOURCE);

        if (client.indices().exists(new GetIndexRequest(INDEX), RequestOptions.DEFAULT)) {
            client.indices().delete(new DeleteIndexRequest(INDEX), RequestOptions.DEFAULT);
        }

        CreateIndexRequest request = new CreateIndexRequest(INDEX);
        request.mapping(
                "{\"properties\":{" +
                        "\"name\":{\"type\":\"keyword\"}," +
                        "\"age\":{\"type\":\"long\"}," +
                        "\"status\":{\"type\":\"keyword\"}" +
                        "}}",
                org.elasticsearch.xcontent.XContentType.JSON
        );
        client.indices().create(request, RequestOptions.DEFAULT);
        log.info("✓ 已创建索引: {}", INDEX);

        List<Map<String, Object>> docs = Arrays.asList(
                doc("Alice", 25, "active"),
                doc("Bob",   30, "inactive"),
                doc("Carol", 22, "active")
        );
        for (int i = 0; i < docs.size(); i++) {
            client.index(new IndexRequest(INDEX).id("m-" + (i + 1)).source(docs.get(i)), RequestOptions.DEFAULT);
        }

        Thread.sleep(2000);
        log.info("✓ 测试数据写入完成，共 {} 条", docs.size());
    }

    @AfterAll
    static void cleanupAll(@Autowired SimpleElasticsearchRouteRegistry registry) throws Exception {
        RestHighLevelClient client = registry.getHighLevelClient(DATASOURCE);
        if (client.indices().exists(new GetIndexRequest(INDEX), RequestOptions.DEFAULT)) {
            client.indices().delete(new DeleteIndexRequest(INDEX), RequestOptions.DEFAULT);
            log.info("✓ 已删除索引: {}", INDEX);
        }
    }

    @BeforeEach
    void clearMetrics() {
        meterRegistry.clear();
    }

    // ==================== 查询指标 ====================

    @Test
    @Order(1)
    @DisplayName("普通查询成功 → Counter +1，me=application name，Timer 有记录")
    void testQueryApiRecordsMetrics() throws Exception {
        log.info("========== 测试：普通查询成功记录指标 ==========");

        QueryRequest request = QueryRequest.builder()
                .index(INDEX)
                .query(QueryCondition.builder()
                        .field("status").op("eq").value("active")
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Counter counter = meterRegistry.find("simple_elasticsearch_search_request_total")
                .tag("eventType", "query")
                .tag("result", "success")
                .tag("sourceType", "QUERY_API")
                .tag("me", EXPECTED_ME)
                .counter();

        log.info("counter={}", counter != null ? counter.count() : null);
        assertNotNull(counter, "查询成功 Counter 应被注册");
        assertEquals(1.0, counter.count(), 0.001, "查询成功 Counter 应为 1");

        Timer timer = meterRegistry.find("simple_elasticsearch_search_request_seconds")
                .tag("eventType", "query")
                .tag("sourceType", "QUERY_API")
                .tag("me", EXPECTED_ME)
                .timer();

        log.info("timer count={}, totalMs={}", timer != null ? timer.count() : null,
                timer != null ? timer.totalTime(TimeUnit.MILLISECONDS) : null);
        assertNotNull(timer, "查询成功 Timer 应被注册");
        assertEquals(1, timer.count(), "Timer count 应为 1");
        assertTrue(timer.totalTime(TimeUnit.MILLISECONDS) > 0, "Timer 耗时应 > 0");
    }

    @Test
    @Order(2)
    @DisplayName("表达式查询成功 → sourceType=EXPRESSION_API")
    void testExpressionQueryApiRecordsMetrics() throws Exception {
        log.info("========== 测试：表达式查询成功记录指标 ==========");

        String body = "{\"index\":\"" + INDEX + "\",\"expression\":\"status = 'active'\"}";
        mockMvc.perform(post("/api/query/expression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk());

        Counter counter = meterRegistry.find("simple_elasticsearch_search_request_total")
                .tag("eventType", "query")
                .tag("result", "success")
                .tag("sourceType", "EXPRESSION_API")
                .tag("me", EXPECTED_ME)
                .counter();

        log.info("counter={}", counter != null ? counter.count() : null);
        assertNotNull(counter, "表达式查询 Counter 应被注册");
        assertEquals(1.0, counter.count(), 0.001);
    }

    @Test
    @Order(3)
    @DisplayName("多次查询 → Counter 累加")
    void testMultipleQueriesAccumulateCounter() throws Exception {
        log.info("========== 测试：多次查询 Counter 累加 ==========");

        QueryRequest request = QueryRequest.builder()
                .index(INDEX)
                .build();

        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/query").contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/query").contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/query").contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8").content(body))
                .andExpect(status().isOk());

        Counter counter = meterRegistry.find("simple_elasticsearch_search_request_total")
                .tag("eventType", "query")
                .tag("result", "success")
                .tag("sourceType", "QUERY_API")
                .tag("me", EXPECTED_ME)
                .counter();

        log.info("counter={}", counter != null ? counter.count() : null);
        assertNotNull(counter);
        assertEquals(3.0, counter.count(), 0.001, "3 次查询 Counter 应为 3");

        Timer timer = meterRegistry.find("simple_elasticsearch_search_request_seconds")
                .tag("eventType", "query")
                .tag("sourceType", "QUERY_API")
                .tag("me", EXPECTED_ME)
                .timer();

        log.info("timer count={}", timer != null ? timer.count() : null);
        assertNotNull(timer);
        assertEquals(3, timer.count(), "3 次查询 Timer count 应为 3");
    }

    @Test
    @Order(4)
    @DisplayName("聚合查询成功 → Counter +1，eventType=agg，Timer 有记录")
    void testAggApiRecordsMetrics() throws Exception {
        log.info("========== 测试：聚合查询成功记录指标 ==========");

        AggRequest request = AggRequest.builder()
                .index(INDEX)
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("avg_age")
                                .type("AVG")
                                .field("age")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Counter counter = meterRegistry.find("simple_elasticsearch_search_request_total")
                .tag("eventType", "agg")
                .tag("result", "success")
                .tag("sourceType", "QUERY_API")
                .tag("me", EXPECTED_ME)
                .counter();

        log.info("counter={}", counter != null ? counter.count() : null);
        assertNotNull(counter, "聚合成功 Counter 应被注册");
        assertEquals(1.0, counter.count(), 0.001, "聚合成功 Counter 应为 1");

        Timer timer = meterRegistry.find("simple_elasticsearch_search_request_seconds")
                .tag("eventType", "agg")
                .tag("sourceType", "QUERY_API")
                .tag("me", EXPECTED_ME)
                .timer();

        log.info("timer count={}, totalMs={}", timer != null ? timer.count() : null,
                timer != null ? timer.totalTime(TimeUnit.MILLISECONDS) : null);
        assertNotNull(timer, "聚合成功 Timer 应被注册");
        assertEquals(1, timer.count(), "Timer count 应为 1");
        assertTrue(timer.totalTime(TimeUnit.MILLISECONDS) > 0, "Timer 耗时应 > 0");
    }

    @Test
    @Order(5)
    @DisplayName("表达式聚合查询成功 → sourceType=EXPRESSION_API，eventType=agg")
    void testExpressionAggApiRecordsMetrics() throws Exception {
        log.info("========== 测试：表达式聚合查询成功记录指标 ==========");

        String body = "{\"index\":\"" + INDEX + "\",\"expression\":\"status is not null\",\"aggs\":[{\"name\":\"avg_age\",\"type\":\"AVG\",\"field\":\"age\"}]}";
        mockMvc.perform(post("/api/agg/expression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk());

        Counter counter = meterRegistry.find("simple_elasticsearch_search_request_total")
                .tag("eventType", "agg")
                .tag("result", "success")
                .tag("sourceType", "EXPRESSION_API")
                .tag("me", EXPECTED_ME)
                .counter();

        log.info("counter={}", counter != null ? counter.count() : null);
        assertNotNull(counter, "表达式聚合 Counter 应被注册");
        assertEquals(1.0, counter.count(), 0.001);
    }

    @Test
    @Order(6)
    @DisplayName("NL查询成功 → sourceType=NL_API，eventType=query")
    void testNLQueryApiRecordsMetrics() throws Exception {
        log.info("========== 测试：NL查询成功记录指标 ==========");

        String body = "{\"nl\":\"查询test_metrics_index索引，status等于active\"}";
        mockMvc.perform(post("/api/query/nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(body))
                .andExpect(status().isOk());

        Counter counter = meterRegistry.find("simple_elasticsearch_search_request_total")
                .tag("eventType", "query")
                .tag("result", "success")
                .tag("sourceType", "NL_API")
                .tag("me", EXPECTED_ME)
                .counter();

        log.info("counter={}", counter != null ? counter.count() : null);
        assertNotNull(counter, "NL查询 Counter 应被注册");
        assertEquals(1.0, counter.count(), 0.001);
    }

    // ==================== 工具方法 ====================

    private static Map<String, Object> doc(String name, int age, String status) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("age", age);
        map.put("status", status);
        return map;
    }

    }
