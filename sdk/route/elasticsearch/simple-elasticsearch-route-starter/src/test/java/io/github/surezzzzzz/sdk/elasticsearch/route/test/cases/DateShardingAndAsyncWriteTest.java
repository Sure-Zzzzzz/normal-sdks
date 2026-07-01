package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.RouteException;
import io.github.surezzzzzz.sdk.elasticsearch.route.proxy.RouteRoutingInterceptor;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 1.1.0 新功能：日期分片（renderTemplate）和异步写（asyncWrite=true）集成测试
 *
 * @author surezzzzzz
 * @since 1.1.0
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class DateShardingAndAsyncWriteTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER_DAY = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter DATE_FORMATTER_MONTH = DateTimeFormatter.ofPattern("yyyy.MM");
    private static final DateTimeFormatter DATE_FORMATTER_YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter DATE_FORMATTER_COMPACT_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long ES_REFRESH_WAIT_MS = 2000L;

    @Autowired
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Autowired
    private SimpleElasticsearchRouteRegistry registry;

    private Object callSave(Object doc) {
        try {
            java.lang.reflect.Method save = elasticsearchRestTemplate.getClass()
                    .getMethod("save", Object.class);
            return save.invoke(elasticsearchRestTemplate, doc);
        } catch (Exception e) {
            throw new RouteException(ErrorCode.ROUTE_TEMPLATE_UNAVAILABLE, "Failed to call save()", e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T callGet(String id, Class<T> documentClass) {
        try {
            java.lang.reflect.Method get = elasticsearchRestTemplate.getClass()
                    .getMethod("get", String.class, Class.class);
            return (T) get.invoke(elasticsearchRestTemplate, id, documentClass);
        } catch (Exception e) {
            throw new RouteException(ErrorCode.ROUTE_TEMPLATE_UNAVAILABLE, "Failed to call get()", e);
        }
    }

    private void assertDocumentFields(Object doc, String expectedId, String expectedValue) {
        assertEquals(expectedId, readProperty(doc, "getId"), "代理查询读回的 id 应匹配");
        assertEquals(expectedValue, readProperty(doc, "getValue"), "代理查询读回的 value 应匹配");
    }

    private Object readProperty(Object doc, String methodName) {
        try {
            return doc.getClass().getMethod(methodName).invoke(doc);
        } catch (Exception e) {
            throw new RouteException(ErrorCode.ROUTE_TEMPLATE_UNAVAILABLE,
                    "Failed to read property: " + methodName, e);
        }
    }

    @Test
    public void renderTemplateReplacesDatePlaceholder() {
        log.info("=== renderTemplateReplacesDatePlaceholder ===");
        RouteRoutingInterceptor interceptor = new RouteRoutingInterceptor(
                Collections.emptyMap(), null, null, Collections.emptyList(), Collections.emptyMap(),
                ZoneId.systemDefault());
        String today = LocalDate.now().format(DATE_FORMATTER_DAY);

        String result = interceptor.renderTemplate("log-{yyyy.MM.dd}");

        log.info("renderTemplate result: {}", result);
        assertEquals("log-" + today, result, "日期占位符应被替换为今日日期");
    }

    @Test
    public void renderTemplateWithNoPlaceholderReturnsOriginal() {
        log.info("=== renderTemplateWithNoPlaceholderReturnsOriginal ===");
        RouteRoutingInterceptor interceptor = new RouteRoutingInterceptor(
                Collections.emptyMap(), null, null, Collections.emptyList(), Collections.emptyMap(),
                ZoneId.systemDefault());

        String result = interceptor.renderTemplate("no-placeholder");

        log.info("renderTemplate result: {}", result);
        assertEquals("no-placeholder", result, "无占位符时应原样返回");
    }

    @Test
    public void renderTemplateWithInvalidPatternReturnsOriginal() {
        log.info("=== renderTemplateWithInvalidPatternReturnsOriginal ===");
        RouteRoutingInterceptor interceptor = new RouteRoutingInterceptor(
                Collections.emptyMap(), null, null, Collections.emptyList(), Collections.emptyMap(),
                ZoneId.systemDefault());
        String template = "a-{invalid!!}";

        String result = interceptor.renderTemplate(template);

        log.info("renderTemplate result: {}", result);
        assertEquals(template, result, "非法 pattern 应原样返回，不抛异常");
    }

    @Test
    public void renderTemplateWithEmptyReturnsEmpty() {
        log.info("=== renderTemplateWithEmptyReturnsEmpty ===");
        RouteRoutingInterceptor interceptor = new RouteRoutingInterceptor(
                Collections.emptyMap(), null, null, Collections.emptyList(), Collections.emptyMap(),
                ZoneId.systemDefault());

        log.info("renderTemplate null result: {}", interceptor.renderTemplate(null));
        assertNull(interceptor.renderTemplate(null), "null 应返回 null");
        assertEquals("", interceptor.renderTemplate(""), "空字符串应返回空字符串");
    }

    @Test
    public void renderTemplateWithPrefixOnly() {
        log.info("=== renderTemplateWithPrefixOnly ===");
        RouteRoutingInterceptor interceptor = new RouteRoutingInterceptor(
                Collections.emptyMap(), null, null, Collections.emptyList(), Collections.emptyMap(),
                ZoneId.systemDefault());
        String today = LocalDate.now().format(DATE_FORMATTER_COMPACT_DAY);

        String result = interceptor.renderTemplate("{yyyyMMdd}");

        log.info("renderTemplate result: {}", result);
        assertEquals(today, result, "仅日期占位符时应返回纯日期字符串");
    }

    @Test
    public void asyncWriteReturnsNullImmediately() {
        log.info("=== asyncWriteReturnsNullImmediately ===");
        AsyncWriteTestDocument doc = new AsyncWriteTestDocument("async-id-1", "async-value");

        Object result = callSave(doc);

        log.info("save() 返回值: {}", result);
        assertNull(result, "asyncWrite=true 时写操作应立即返回 null，不等待 ES 响应");
    }

    @Test
    public void asyncWriteWithTemplateCombinationReturnsNull() {
        log.info("=== asyncWriteWithTemplateCombinationReturnsNull ===");
        AsyncWriteTestDocument doc = new AsyncWriteTestDocument("async-combo-1", "combo-value");

        Object result = callSave(doc);

        log.info("save() 返回值: {}，asyncWrite 优先，模板渲染后提交线程池", result);
        assertNull(result, "asyncWrite=true 时，即使同时配了 writeIndexTemplate，写操作也应立即返回 null");
    }

    @Test
    public void dateShardWriteRoutesToYearIndex() throws Exception {
        log.info("=== dateShardWriteRoutesToYearIndex ===");
        String indexName = "date_shard_year-" + LocalDate.now().format(DATE_FORMATTER_YEAR);
        String docId = "shard-year-" + System.currentTimeMillis();
        assertDateShardWriteAndRead(indexName, new DateShardYearTestDocument(docId, "year-value"),
                docId, "year-value", DateShardYearTestDocument.class);
    }

    @Test
    public void dateShardWriteRoutesToMonthIndex() throws Exception {
        log.info("=== dateShardWriteRoutesToMonthIndex ===");
        String indexName = "date_shard_month-" + LocalDate.now().format(DATE_FORMATTER_MONTH);
        String docId = "shard-month-" + System.currentTimeMillis();
        assertDateShardWriteAndRead(indexName, new DateShardMonthTestDocument(docId, "month-value"),
                docId, "month-value", DateShardMonthTestDocument.class);
    }

    @Test
    public void dateShardWriteRoutesToRenderedIndex() throws Exception {
        log.info("=== dateShardWriteRoutesToRenderedIndex ===");
        String todayIndex = "date_shard-" + LocalDate.now().format(DATE_FORMATTER_DAY);
        String docId = "shard-e2e-" + System.currentTimeMillis();
        assertDateShardWriteAndRead(todayIndex, new DateShardTestDocument(docId, "shard-value"),
                docId, "shard-value", DateShardTestDocument.class);
    }

    private <T> void assertDateShardWriteAndRead(String indexName, T doc, String docId, String expectedValue,
                                                Class<T> documentClass) throws Exception {
        log.info("预期写入索引：{}，文档ID：{}", indexName, docId);
        Object saved = callSave(doc);

        log.info("save() 返回值: {}", saved);
        assertNotNull(saved, "writeIndexTemplate 同步写应返回非 null");

        Thread.sleep(ES_REFRESH_WAIT_MS);
        RestClient secondaryLow = registry.getLowLevelClient("secondary");
        Response resp = secondaryLow.performRequest(new Request("GET", "/" + indexName + "/_doc/" + docId));
        String body = EntityUtils.toString(resp.getEntity());

        log.info("日期分片索引直查读回，index={}, docId={}, body={}", indexName, docId, body);
        assertEquals(SimpleElasticsearchRouteConstant.HTTP_STATUS_OK, resp.getStatusLine().getStatusCode(),
                "日期分片索引 " + indexName + " 应能读回写入的文档");

        JsonNode root = OBJECT_MAPPER.readTree(body);
        assertTrue(root.path("found").asBoolean(), "ES 响应 found 应为 true");
        assertEquals(indexName, root.path("_index").asText(), "ES 响应 _index 应匹配渲染后的索引名");
        assertEquals(docId, root.path("_id").asText(), "ES 响应 _id 应匹配写入文档 ID");
        JsonNode source = root.path("_source");
        assertFalse(source.isMissingNode(), "ES 响应应包含 _source");
        assertEquals(docId, source.path("id").asText(), "ES 响应 _source.id 应匹配写入文档 ID");
        assertEquals(expectedValue, source.path("value").asText(), "ES 响应 _source.value 应匹配写入字段值");

        T routedRead = callGet(docId, documentClass);
        log.info("日期分片代理查询读回，documentClass={}, doc={}", documentClass.getSimpleName(), routedRead);
        assertNotNull(routedRead, "通过 readIndexPattern 代理查询应能读回文档");
        assertDocumentFields(routedRead, docId, expectedValue);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Document(indexName = "async_write.test")
    public static class AsyncWriteTestDocument {
        @Id
        private String id;

        @Field(type = FieldType.Keyword)
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Document(indexName = "date_shard.year.test")
    public static class DateShardYearTestDocument {
        @Id
        private String id;

        @Field(type = FieldType.Keyword)
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Document(indexName = "date_shard.month.test")
    public static class DateShardMonthTestDocument {
        @Id
        private String id;

        @Field(type = FieldType.Keyword)
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Document(indexName = "date_shard.test")
    public static class DateShardTestDocument {
        @Id
        private String id;

        @Field(type = FieldType.Keyword)
        private String value;
    }

    // ---- 1.1.1 时区单元测试 ----

    @Test
    public void renderTemplateWithExplicitZone_Shanghai() {
        log.info("=== renderTemplateWithExplicitZone_Shanghai ===");
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        RouteRoutingInterceptor interceptor = new RouteRoutingInterceptor(
                Collections.emptyMap(), null, null, Collections.emptyList(), Collections.emptyMap(),
                zone);
        String expected = "log-" + LocalDate.now(zone).format(DATE_FORMATTER_DAY);

        String result = interceptor.renderTemplate("log-{yyyy.MM.dd}", zone);

        log.info("Shanghai 时区渲染结果: {}", result);
        assertEquals(expected, result, "Asia/Shanghai 时区渲染结果应与 LocalDate.now(Shanghai) 一致");
    }

    @Test
    public void renderTemplateWithExplicitZone_UTC() {
        log.info("=== renderTemplateWithExplicitZone_UTC ===");
        ZoneId zone = ZoneId.of("UTC");
        RouteRoutingInterceptor interceptor = new RouteRoutingInterceptor(
                Collections.emptyMap(), null, null, Collections.emptyList(), Collections.emptyMap(),
                zone);
        String expected = "log-" + LocalDate.now(zone).format(DATE_FORMATTER_DAY);

        String result = interceptor.renderTemplate("log-{yyyy.MM.dd}", zone);

        log.info("UTC 时区渲染结果: {}", result);
        assertEquals(expected, result, "UTC 时区渲染结果应与 LocalDate.now(UTC) 一致");
    }

    @Test
    public void renderTemplateEachZoneMatchesItsOwnDate() {
        log.info("=== renderTemplateEachZoneMatchesItsOwnDate ===");
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        ZoneId utc = ZoneId.of("UTC");
        RouteRoutingInterceptor interceptor = new RouteRoutingInterceptor(
                Collections.emptyMap(), null, null, Collections.emptyList(), Collections.emptyMap(),
                ZoneId.systemDefault());

        String shResult = interceptor.renderTemplate("idx-{yyyy.MM.dd}", shanghai);
        String utcResult = interceptor.renderTemplate("idx-{yyyy.MM.dd}", utc);
        String shExpected = "idx-" + LocalDate.now(shanghai).format(DATE_FORMATTER_DAY);
        String utcExpected = "idx-" + LocalDate.now(utc).format(DATE_FORMATTER_DAY);

        log.info("Shanghai 渲染: {}，UTC 渲染: {}", shResult, utcResult);
        assertEquals(shExpected, shResult, "Shanghai 渲染结果应与自身 LocalDate.now(Shanghai) 一致");
        assertEquals(utcExpected, utcResult, "UTC 渲染结果应与自身 LocalDate.now(UTC) 一致");
    }

    @Test
    public void renderTemplateWithNoZoneUsesSystemDefault() {
        log.info("=== renderTemplateWithNoZoneUsesSystemDefault ===");
        RouteRoutingInterceptor interceptor = new RouteRoutingInterceptor(
                Collections.emptyMap(), null, null, Collections.emptyList(), Collections.emptyMap(),
                ZoneId.systemDefault());
        String template = "idx-{yyyy.MM.dd}";

        String noArgResult = interceptor.renderTemplate(template);
        String explicitResult = interceptor.renderTemplate(template, ZoneId.systemDefault());

        log.info("无参渲染: {}，显式 systemDefault 渲染: {}", noArgResult, explicitResult);
        assertEquals(explicitResult, noArgResult, "无参 renderTemplate 应等价于传入 ZoneId.systemDefault()");
    }
}
