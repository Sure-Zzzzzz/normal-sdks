package io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.model.EsAuditRecord;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.EsAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.TestEsAuditHandler;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.config.TestSecurityConfig;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.xcontent.XContentType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ES审计 + Header认证集成测试
 *
 * <p>测试真实的HTTP请求 -> Header认证 -> ES查询 -> ES审计事件 -> 审计记录捕获的完整链路
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(
        classes = EsAuditListenerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.enabled=false",
                "test.security.permit-all=true",
                "test.es.audit.provider-type=header"
        }
)
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
public class EsAuditHeaderIntegrationTest {

    private static final String TEST_INDEX = "audit_test_user";
    private static final String AGG_INDEX = "audit_test_order";
    private static final String API_INDEX_0 = "audit_test_log_0";
    private static final String API_INDEX_1 = "audit_test_log_1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestEsAuditHandler testEsAuditHandler;

    @Autowired
    private ObjectMapper objectMapper;

    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    @BeforeAll
    static void setupAll(@Autowired SimpleElasticsearchRouteRegistry registry) throws Exception {
        log.info("========== 创建测试索引并插入数据 ==========");
        RestHighLevelClient client = registry.getHighLevelClient("primary");

        createIndex(client, TEST_INDEX,
                "{\"properties\":{" +
                        "\"name\":{\"type\":\"keyword\"}," +
                        "\"age\":{\"type\":\"integer\"}," +
                        "\"city\":{\"type\":\"keyword\"}" +
                        "}}");
        indexDoc(client, TEST_INDEX, "1", map("name", "张三", "age", 25, "city", "北京"));
        indexDoc(client, TEST_INDEX, "2", map("name", "李四", "age", 30, "city", "上海"));
        indexDoc(client, TEST_INDEX, "3", map("name", "王五", "age", 28, "city", "北京"));

        createIndex(client, AGG_INDEX,
                "{\"properties\":{" +
                        "\"product\":{\"type\":\"keyword\"}," +
                        "\"category\":{\"type\":\"keyword\"}," +
                        "\"amount\":{\"type\":\"double\"}" +
                        "}}");
        indexDoc(client, AGG_INDEX, "1", map("product", "iPhone", "category", "电子", "amount", 7999.0));
        indexDoc(client, AGG_INDEX, "2", map("product", "MacBook", "category", "电子", "amount", 15999.0));
        indexDoc(client, AGG_INDEX, "3", map("product", "耳机", "category", "配件", "amount", 1999.0));

        createIndex(client, API_INDEX_0,
                "{\"properties\":{" +
                        "\"title\":{\"type\":\"keyword\"}," +
                        "\"status\":{\"type\":\"keyword\"}" +
                        "}}");
        indexDoc(client, API_INDEX_0, "1", map("title", "日志A", "status", "active"));

        createIndex(client, API_INDEX_1,
                "{\"properties\":{" +
                        "\"title\":{\"type\":\"keyword\"}," +
                        "\"status\":{\"type\":\"keyword\"}" +
                        "}}");
        indexDoc(client, API_INDEX_1, "1", map("title", "日志B", "status", "inactive"));

        Thread.sleep(2000);
        log.info("========== 测试数据准备完成 ==========");
    }

    @AfterAll
    static void cleanupAll(@Autowired SimpleElasticsearchRouteRegistry registry) throws Exception {
        log.info("========== 清理测试索引 ==========");
        RestHighLevelClient client = registry.getHighLevelClient("primary");
        deleteIndex(client, TEST_INDEX);
        deleteIndex(client, AGG_INDEX);
        deleteIndex(client, API_INDEX_0);
        deleteIndex(client, API_INDEX_1);
        log.info("========== 测试索引清理完成 ==========");
    }

    @BeforeEach
    public void setUp() {
        testEsAuditHandler.reset();
    }

    // ==================== 测试 ====================

    @Test
    public void testHeaderAuthenticationWithEsQuery() throws Exception {
        log.info("========== 测试：Header认证 + ES查询 + 审计 ==========");

        QueryRequest request = QueryRequest.builder()
                .index(TEST_INDEX)
                .query(QueryCondition.builder()
                        .field("city")
                        .op("EQ")
                        .value("北京")
                        .build())
                .pagination(PaginationInfo.builder()
                        .type("offset")
                        .page(1)
                        .size(10)
                        .build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request))
                        .header("x-sure-auth-aksk-client-id", "header-client")
                        .header("x-sure-auth-aksk-client-type", "platform")
                        .header("x-sure-auth-aksk-user-id", "header-user-123")
                        .header("x-sure-auth-aksk-username", "headeruser"))
                .andExpect(status().isOk());

        boolean received = testEsAuditHandler.latch.await(5, TimeUnit.SECONDS);

        assertTrue(received, "Should receive audit event");
        assertEquals(1, testEsAuditHandler.records.size());

        EsAuditRecord record = testEsAuditHandler.records.get(0);
        assertEquals("header-client", record.getClientId());
        assertEquals("platform", record.getClientType());
        assertEquals("header-user-123", record.getUserId());
        assertEquals("headeruser", record.getUsername());
        assertEquals(TEST_INDEX, record.getIndexAlias());
        assertEquals("primary", record.getDatasource());
        assertNotNull(record.getTotal());
        assertNotNull(record.getTook());
        assertNotNull(record.getTimestamp());

        log.info("✓ 查询审计通过: user={}, index={}, total={}", record.getUsername(), record.getIndexAlias(), record.getTotal());
    }

    @Test
    public void testHeaderAuthenticationWithEsAgg() throws Exception {
        log.info("========== 测试：Header认证 + ES聚合 + 审计 ==========");

        AggRequest request = AggRequest.builder()
                .index(AGG_INDEX)
                .aggs(Collections.singletonList(
                        AggDefinition.builder()
                                .name("by_category")
                                .type("TERMS")
                                .field("category")
                                .size(10)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/agg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request))
                        .header("x-sure-auth-aksk-client-id", "agg-client")
                        .header("x-sure-auth-aksk-user-id", "agg-user-456")
                        .header("x-sure-auth-aksk-username", "agguser"))
                .andExpect(status().isOk());

        boolean received = testEsAuditHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Should receive audit event");

        EsAuditRecord record = testEsAuditHandler.records.get(0);
        assertEquals("agg-client", record.getClientId());
        assertEquals("agg-user-456", record.getUserId());
        assertEquals("agguser", record.getUsername());
        assertEquals(AGG_INDEX, record.getIndexAlias());
        assertEquals("primary", record.getDatasource());
        assertNull(record.getTotal());  // 聚合没有total
        assertNotNull(record.getTook());

        log.info("✓ 聚合审计通过: user={}, index={}", record.getUsername(), record.getIndexAlias());
    }

    @Test
    public void testMultipleEsQueriesWithHeader() throws Exception {
        log.info("========== 测试：多个ES查询的审计 ==========");

        testEsAuditHandler.reset(2);  // 期望收到2个审计事件

        String[] indices = {API_INDEX_0, API_INDEX_1};
        for (int i = 0; i < 2; i++) {
            QueryRequest request = QueryRequest.builder()
                    .index(indices[i])
                    .pagination(PaginationInfo.builder()
                            .type("offset")
                            .page(1)
                            .size(10)
                            .build())
                    .build();

            mockMvc.perform(post("/api/query")
                            .contentType(MediaType.APPLICATION_JSON)
                            .characterEncoding("UTF-8")
                            .content(toJson(request))
                            .header("x-sure-auth-aksk-client-id", "multi-client")
                            .header("x-sure-auth-aksk-user-id", "multi-user-" + i))
                    .andExpect(status().isOk());
        }

        boolean received = testEsAuditHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Should receive 2 audit events");
        assertEquals(2, testEsAuditHandler.records.size());

        for (EsAuditRecord record : testEsAuditHandler.records) {
            assertEquals("multi-client", record.getClientId());
            assertTrue(record.getIndexAlias().startsWith("audit_test_log_"));
        }

        log.info("✓ 多查询审计通过: 收到 {} 条记录", testEsAuditHandler.records.size());
    }

    // ==================== 辅助方法 ====================

    private static void createIndex(RestHighLevelClient client, String index, String mapping) throws Exception {
        if (client.indices().exists(new GetIndexRequest(index), RequestOptions.DEFAULT)) {
            client.indices().delete(new DeleteIndexRequest(index), RequestOptions.DEFAULT);
        }
        CreateIndexRequest req = new CreateIndexRequest(index);
        req.mapping(mapping, XContentType.JSON);
        client.indices().create(req, RequestOptions.DEFAULT);
        log.info("✓ 创建索引: {}", index);
    }

    private static void deleteIndex(RestHighLevelClient client, String index) throws Exception {
        if (client.indices().exists(new GetIndexRequest(index), RequestOptions.DEFAULT)) {
            client.indices().delete(new DeleteIndexRequest(index), RequestOptions.DEFAULT);
            log.info("✓ 删除索引: {}", index);
        }
    }

    private static void indexDoc(RestHighLevelClient client, String index, String id, Map<String, Object> doc) throws Exception {
        client.index(new IndexRequest(index).id(id).source(doc), RequestOptions.DEFAULT);
    }

    private static Map<String, Object> map(Object... kvs) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kvs.length; i += 2) {
            m.put((String) kvs[i], kvs[i + 1]);
        }
        return m;
    }
}
