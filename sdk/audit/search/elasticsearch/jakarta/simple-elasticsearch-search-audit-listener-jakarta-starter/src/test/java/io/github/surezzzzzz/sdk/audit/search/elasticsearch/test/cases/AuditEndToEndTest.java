package io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.cases;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.model.EsAuditRecord;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.AuditTestApplication;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggDefinition;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionAggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.request.ExpressionQueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.engine.SearchEngine;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.ExpressionService;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 ES7/ES8：正式查询、计数和表达式进入 Core 事件后异步输出审计。
 */
@SpringBootTest(classes = AuditTestApplication.class)
@Slf4j
class AuditEndToEndTest {
    private final Map<String, String> owned = new LinkedHashMap<>();
    @Autowired
    private SearchEngine engine;
    @Autowired
    private ExpressionService expressions;
    @Autowired
    private SimpleElasticsearchRouteRegistry routes;
    @Autowired
    private AuditTestApplication.Collector collector;
    @Autowired
    private AuditTestApplication.Users users;

    @BeforeEach
    void prepare() {
        collector.getRecords().clear();
        users.current.set("sample-audit-user");
    }

    @AfterEach
    void cleanup() throws Exception {
        users.current.remove();
        for (Map.Entry<String, String> entry : owned.entrySet()) {
            assertTrue(entry.getKey().matches("test-jsa[78]-[a-f0-9-]{36}"));
            routes.getLowLevelClient(entry.getValue()).performRequest(new Request("DELETE", "/" + entry.getKey()));
        }
        owned.clear();
    }

    private String index(String source) throws Exception {
        String value = "test-jsa" + (source.equals("primary") ? "7-" : "8-") + UUID.randomUUID();
        Request create = new Request("PUT", "/" + value);
        create.setJsonEntity("{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0},\"mappings\":{\"properties\":{\"amount\":{\"type\":\"integer\"}}}}");
        routes.getLowLevelClient(source).performRequest(create);
        owned.put(value, source);
        Request seed = new Request("PUT", "/" + value + "/_doc/sample");
        seed.addParameter("refresh", "true");
        seed.setJsonEntity("{\"amount\":4}");
        routes.getLowLevelClient(source).performRequest(seed);
        return value;
    }

    private EsAuditRecord take(String source, String result) throws Exception {
        EsAuditRecord record = collector.getRecords().poll(5, TimeUnit.SECONDS);
        assertNotNull(record, "真实主链必须发布审计记录");
        assertEquals(source, record.getDatasource());
        assertEquals(result, record.getResult());
        assertEquals("sample-audit-user", record.getUserId());
        assertEquals("sample-audit-user", record.getTraceId());
        assertNull(record.getIndexAlias());
        assertNull(record.getActualIndices());
        assertNull(record.getQueryCondition());
        assertNull(record.getReturnedSize());
        assertTrue(collector.getThreadName().startsWith("es-audit-"));
        return record;
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void queryCountAndAggregationPublishOnlySafeStatistics(String source) throws Exception {
        String index = index(source);
        assertEquals(1, engine.query(QueryRequest.builder().index(index).build()).getItems().size());
        EsAuditRecord query = take(source, "success");
        assertEquals(Long.valueOf(1), query.getTotal());
        assertFalse(query.getCountOnly());
        assertNotNull(query.getTook());
        assertEquals(1, engine.count(QueryRequest.builder().index(index).build()).getTotal());
        assertTrue(take(source, "success").getCountOnly());
        assertEquals(4.0, ((Number) engine.aggregate(AggRequest.builder().index(index).aggs(Collections.singletonList(
                AggDefinition.builder().name("sampleSum").type("sum").field("amount").build())).build()).getAggregations().get("sampleSum")).doubleValue());
        EsAuditRecord aggregate = take(source, "success");
        assertNull(aggregate.getTotal());
        assertNull(aggregate.getCountOnly());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void queryCountAndAggregationFailuresAreAudited(String source) throws Exception {
        String index = index(source);
        QueryRequest request = QueryRequest.builder().index(index).query(QueryCondition.builder().field("missing").op("eq").value("private-marker").build()).build();
        assertThrows(RuntimeException.class, () -> engine.query(request));
        assertFalse(take(source, "failure").getCountOnly());
        assertThrows(RuntimeException.class, () -> engine.count(request));
        assertTrue(take(source, "failure").getCountOnly());
        assertThrows(RuntimeException.class, () -> engine.aggregate(AggRequest.builder().index(index).aggs(Collections.singletonList(
                AggDefinition.builder().name("sample").type("sum").field("missing").build())).build()));
        EsAuditRecord error = take(source, "failure");
        assertFalse(error.getErrorMessage().contains("private-marker"));
        assertNull(error.getTotal());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void expressionDelegationUsesUnderlyingSearchEvents(String source) throws Exception {
        String index = index(source);
        assertEquals(1, expressions.query(ExpressionQueryRequest.builder().index(index).expression("amount=4").build()).getTotal());
        assertEquals("structured", take(source, "success").getSourceType());
        assertEquals(1, expressions.query(ExpressionQueryRequest.builder().index(index).expression("amount=4").countOnly(true).build()).getTotal());
        assertTrue(take(source, "success").getCountOnly());
        assertNotNull(expressions.aggregate(ExpressionAggRequest.builder().index(index).expression("amount=4").aggs(Collections.singletonList(
                AggDefinition.builder().name("sampleSum").type("sum").field("amount").build())).build()));
        assertNull(take(source, "success").getCountOnly());
        // 解析阶段在进入 SearchEngine 前失败，不把未发布的事件伪装为查询失败。
        assertThrows(RuntimeException.class, () -> expressions.query(ExpressionQueryRequest.builder().index(index).expression("amount=(").build()));
        assertTrue(collector.getRecords().isEmpty());
    }
}
