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
 * 基于 AkskAccessEvent + ThreadLocal 的 ES审计集成测试（Header认证）
 *
 * <p>演示通过监听 AkskAccessEvent 获取用户信息的方式：
 * <ol>
 *   <li>{@code AkskAccessEventUserListener} 同步监听 AkskAccessEvent，存入 ThreadLocal</li>
 *   <li>{@code AkskContextEsAuditUserProvider} 从 ThreadLocal 读取用户信息</li>
 *   <li>{@code AkskContextClearInterceptor} 在请求结束后清理 ThreadLocal</li>
 * </ol>
 *
 * <p>此方案统一了 Header 和 JWT 两种认证方式的用户信息获取逻辑，
 * 业务方只需注册以上三个组件即可接入。
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
                "test.es.audit.provider-type=aksk-event"
        }
)
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
public class EsAuditAkskEventHeaderIntegrationTest {

    private static final String USER_INDEX = "aksk_event_header_user";
    private static final String ORDER_INDEX = "aksk_event_header_order";

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
        log.info("========== 创建 AkskEvent+Header 测试索引 ==========");
        RestHighLevelClient client = registry.getHighLevelClient("primary");

        createIndex(client, USER_INDEX,
                "{\"properties\":{" +
                        "\"name\":{\"type\":\"keyword\"}," +
                        "\"age\":{\"type\":\"integer\"}," +
                        "\"city\":{\"type\":\"keyword\"}" +
                        "}}");
        indexDoc(client, USER_INDEX, "1", map("name", "张三", "age", 25, "city", "北京"));
        indexDoc(client, USER_INDEX, "2", map("name", "李四", "age", 30, "city", "上海"));

        createIndex(client, ORDER_INDEX,
                "{\"properties\":{" +
                        "\"product\":{\"type\":\"keyword\"}," +
                        "\"category\":{\"type\":\"keyword\"}," +
                        "\"amount\":{\"type\":\"double\"}" +
                        "}}");
        indexDoc(client, ORDER_INDEX, "1", map("product", "iPhone", "category", "电子", "amount", 7999.0));
        indexDoc(client, ORDER_INDEX, "2", map("product", "耳机", "category", "配件", "amount", 1999.0));

        Thread.sleep(2000);
        log.info("========== AkskEvent+Header 测试数据准备完成 ==========");
    }

    @AfterAll
    static void cleanupAll(@Autowired SimpleElasticsearchRouteRegistry registry) throws Exception {
        RestHighLevelClient client = registry.getHighLevelClient("primary");
        deleteIndex(client, USER_INDEX);
        deleteIndex(client, ORDER_INDEX);
    }

    @BeforeEach
    void setUp() {
        testEsAuditHandler.reset();
    }

    @Test
    public void testAkskEventHeaderWithEsQuery() throws Exception {
        log.info("========== 测试：AkskEvent(Header) + ES查询 + 审计 ==========");

        QueryRequest request = QueryRequest.builder()
                .index(USER_INDEX)
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

        // Header认证 → AkskSecurityContextFilter发布AkskAccessEvent
        // → AkskAccessEventUserListener存入ThreadLocal
        // → ES查询 → EsAuditEventListener → AkskContextEsAuditUserProvider从ThreadLocal读
        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request))
                        .header("x-sure-auth-aksk-client-id", "event-header-client")
                        .header("x-sure-auth-aksk-client-type", "platform")
                        .header("x-sure-auth-aksk-user-id", "event-header-user-001")
                        .header("x-sure-auth-aksk-username", "eventuser"))
                .andExpect(status().isOk());

        boolean received = testEsAuditHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Should receive audit event");
        assertEquals(1, testEsAuditHandler.records.size());

        EsAuditRecord record = testEsAuditHandler.records.get(0);
        assertEquals("event-header-client", record.getClientId());
        assertEquals("platform", record.getClientType());
        assertEquals("event-header-user-001", record.getUserId());
        assertEquals("eventuser", record.getUsername());
        assertEquals(USER_INDEX, record.getIndexAlias());
        assertEquals("primary", record.getDatasource());
        assertNotNull(record.getTotal());
        assertNotNull(record.getTook());

        log.info("✓ AkskEvent(Header)+查询审计通过: user={}, index={}", record.getUsername(), record.getIndexAlias());
    }

    @Test
    public void testAkskEventHeaderWithEsAgg() throws Exception {
        log.info("========== 测试：AkskEvent(Header) + ES聚合 + 审计 ==========");

        AggRequest request = AggRequest.builder()
                .index(ORDER_INDEX)
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
                        .header("x-sure-auth-aksk-client-id", "event-agg-client")
                        .header("x-sure-auth-aksk-user-id", "event-agg-user-002")
                        .header("x-sure-auth-aksk-username", "aggeventuser"))
                .andExpect(status().isOk());

        boolean received = testEsAuditHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Should receive audit event");

        EsAuditRecord record = testEsAuditHandler.records.get(0);
        assertEquals("event-agg-client", record.getClientId());
        assertEquals("event-agg-user-002", record.getUserId());
        assertEquals("aggeventuser", record.getUsername());
        assertEquals(ORDER_INDEX, record.getIndexAlias());
        assertNull(record.getTotal());
        assertNotNull(record.getTook());

        log.info("✓ AkskEvent(Header)+聚合审计通过: user={}, index={}", record.getUsername(), record.getIndexAlias());
    }

    @Test
    public void testThreadLocalCleanedBetweenRequests() throws Exception {
        log.info("========== 测试：ThreadLocal 在请求间被正确清理 ==========");

        testEsAuditHandler.reset(2);

        // 第一个请求：用户A
        QueryRequest request1 = QueryRequest.builder()
                .index(USER_INDEX)
                .pagination(PaginationInfo.builder().type("offset").page(1).size(10).build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request1))
                        .header("x-sure-auth-aksk-client-id", "client-A")
                        .header("x-sure-auth-aksk-username", "userA"))
                .andExpect(status().isOk());

        // 第二个请求：用户B
        QueryRequest request2 = QueryRequest.builder()
                .index(ORDER_INDEX)
                .pagination(PaginationInfo.builder().type("offset").page(1).size(10).build())
                .build();

        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content(toJson(request2))
                        .header("x-sure-auth-aksk-client-id", "client-B")
                        .header("x-sure-auth-aksk-username", "userB"))
                .andExpect(status().isOk());

        boolean received = testEsAuditHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Should receive 2 audit events");
        assertEquals(2, testEsAuditHandler.records.size());

        // 验证：每个请求的用户信息独立，没有 ThreadLocal 污染
        EsAuditRecord recordA = testEsAuditHandler.records.get(0);
        EsAuditRecord recordB = testEsAuditHandler.records.get(1);

        assertEquals("client-A", recordA.getClientId());
        assertEquals("userA", recordA.getUsername());
        assertEquals("client-B", recordB.getClientId());
        assertEquals("userB", recordB.getUsername());

        log.info("✓ ThreadLocal 清理验证通过：userA={}, userB={}（互不污染）",
                recordA.getUsername(), recordB.getUsername());
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
