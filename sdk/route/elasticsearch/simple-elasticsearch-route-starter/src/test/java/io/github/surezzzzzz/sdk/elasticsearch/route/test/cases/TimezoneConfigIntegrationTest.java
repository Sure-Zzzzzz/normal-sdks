package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.RouteException;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.WriteTestProfilesResolver;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.1.1 新功能：write-index.zone-id 全局默认与 rule 级覆盖集成测试
 *
 * @author surezzzzzz
 * @since 1.1.1
 */
@Slf4j
@SpringBootTest(
        classes = SimpleElasticsearchRouteTestApplication.class,
        properties = "io.github.surezzzzzz.sdk.elasticsearch.route.write-index.zone-id=Pacific/Kiritimati"
)
@ActiveProfiles(resolver = WriteTestProfilesResolver.class)
public class TimezoneConfigIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final ZoneId RULE_ZONE_ID = ZoneId.of("Etc/GMT+12");
    private static final ZoneId GLOBAL_ZONE_ID = ZoneId.of("Pacific/Kiritimati");
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

    @Test
    public void ruleZoneIdOverridesGlobal_endToEnd() throws Exception {
        log.info("=== ruleZoneIdOverridesGlobal_endToEnd ===");
        String ruleDate = LocalDate.now(RULE_ZONE_ID).format(DATE_FORMATTER);
        String globalDate = LocalDate.now(GLOBAL_ZONE_ID).format(DATE_FORMATTER);
        assertNotEquals(globalDate, ruleDate, "测试所选 rule/global 时区应稳定跨日，确保能验证 rule 级覆盖");
        String expectedIndex = "tz_rule-" + ruleDate;
        String wrongGlobalIndex = "tz_rule-" + globalDate;
        String docId = "tz-rule-" + System.currentTimeMillis();
        String value = "rule-val";

        Object saved = callSave(new TzRuleDocument(docId, value));
        assertSavedDocument(saved, docId, value, TzRuleDocument.class);
        log.info("rule 级时区覆盖写入返回: {}，预期索引: {}，错误全局索引: {}", saved, expectedIndex, wrongGlobalIndex);

        Thread.sleep(ES_REFRESH_WAIT_MS);
        RestClient low = registry.getLowLevelClient("secondary");
        assertDocument(low, expectedIndex, docId, value, "rule 级时区日期索引");
        assertDocumentNotExists(low, wrongGlobalIndex, docId, "全局时区日期索引不应包含 rule 级覆盖文档");
        assertRoutedRead(docId, value, TzRuleDocument.class, "rule 级 read-index.pattern 代理读回");
    }

    @Test
    public void ruleInheritsGlobalZoneId_endToEnd() throws Exception {
        log.info("=== ruleInheritsGlobalZoneId_endToEnd ===");
        String globalDate = LocalDate.now(GLOBAL_ZONE_ID).format(DATE_FORMATTER);
        String ruleDate = LocalDate.now(RULE_ZONE_ID).format(DATE_FORMATTER);
        assertNotEquals(ruleDate, globalDate, "测试所选 rule/global 时区应稳定跨日，确保能验证全局继承");
        String expectedIndex = "tz_global-" + globalDate;
        String wrongRuleIndex = "tz_global-" + ruleDate;
        String docId = "tz-global-" + System.currentTimeMillis();
        String value = "global-val";

        Object saved = callSave(new TzGlobalDocument(docId, value));
        assertSavedDocument(saved, docId, value, TzGlobalDocument.class);
        log.info("继承全局时区写入返回: {}，预期索引: {}，错误 rule 时区索引: {}", saved, expectedIndex, wrongRuleIndex);

        Thread.sleep(ES_REFRESH_WAIT_MS);
        RestClient low = registry.getLowLevelClient("secondary");
        assertDocument(low, expectedIndex, docId, value, "全局时区日期索引");
        assertDocumentNotExists(low, wrongRuleIndex, docId, "rule 时区日期索引不应包含继承全局文档");
        assertRoutedRead(docId, value, TzGlobalDocument.class, "全局 read-index.pattern 代理读回");
    }

    private <T> void assertSavedDocument(Object saved, String expectedId, String expectedValue, Class<T> expectedClass) {
        assertNotNull(saved, "同步 save 应返回非 null 文档");
        assertTrue(expectedClass.isInstance(saved), "save 返回对象类型应与写入文档类型一致");
        assertEquals(expectedId, readProperty(saved, "getId"), "save 返回文档 id 应匹配");
        assertEquals(expectedValue, readProperty(saved, "getValue"), "save 返回文档 value 应匹配");
    }

    private <T> void assertRoutedRead(String docId, String expectedValue,
                                      Class<T> documentClass, String description) {
        T routedRead = callGet(docId, documentClass);
        log.info("{}，documentClass={}, doc={}", description, documentClass.getSimpleName(), routedRead);
        assertNotNull(routedRead, description + " 应能读回文档");
        assertEquals(docId, readProperty(routedRead, "getId"), description + " id 应匹配");
        assertEquals(expectedValue, readProperty(routedRead, "getValue"), description + " value 应匹配");
    }

    private Object readProperty(Object doc, String methodName) {
        try {
            return doc.getClass().getMethod(methodName).invoke(doc);
        } catch (Exception e) {
            throw new RouteException(ErrorCode.ROUTE_TEMPLATE_UNAVAILABLE,
                    "Failed to read property: " + methodName, e);
        }
    }

    private void assertDocument(RestClient low, String expectedIndex, String docId,
                                String expectedValue, String indexDescription) throws Exception {
        Response resp = low.performRequest(new Request("GET", "/" + expectedIndex + "/_doc/" + docId));
        String body = EntityUtils.toString(resp.getEntity());
        log.info("{} 直查读回，index={}, docId={}, body={}", indexDescription, expectedIndex, docId, body);

        assertEquals(SimpleElasticsearchRouteConstant.HTTP_STATUS_OK, resp.getStatusLine().getStatusCode(),
                "HTTP 200：" + indexDescription + " " + expectedIndex + " 应存在该文档");
        JsonNode root = OBJECT_MAPPER.readTree(body);
        assertTrue(root.path("found").asBoolean(), "found 应为 true");
        assertEquals(expectedIndex, root.path("_index").asText(),
                "_index 应精确匹配 " + indexDescription);
        assertEquals(docId, root.path("_id").asText(), "_id 应与写入文档 ID 完全一致");
        JsonNode source = root.path("_source");
        assertFalse(source.isMissingNode(), "_source 不应缺失");
        assertEquals(docId, source.path("id").asText(), "_source.id 应与写入文档 ID 完全一致");
        assertEquals(expectedValue, source.path("value").asText(), "_source.value 应与写入字段值完全一致");
    }

    private void assertDocumentNotExists(RestClient low, String indexName, String docId, String description) throws Exception {
        try {
            Response resp = low.performRequest(new Request("GET", "/" + indexName + "/_doc/" + docId));
            assertEquals(SimpleElasticsearchRouteConstant.HTTP_STATUS_NOT_FOUND, resp.getStatusLine().getStatusCode(),
                    description + "，index=" + indexName + " 不应存在该文档");
        } catch (ResponseException e) {
            assertEquals(SimpleElasticsearchRouteConstant.HTTP_STATUS_NOT_FOUND, e.getResponse().getStatusLine().getStatusCode(),
                    description + "，index=" + indexName + " 查询应返回 404");
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Document(indexName = "tz_rule.test")
    public static class TzRuleDocument {
        @Id
        private String id;

        @Field(type = FieldType.Keyword)
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Document(indexName = "tz_global.test")
    public static class TzGlobalDocument {
        @Id
        private String id;

        @Field(type = FieldType.Keyword)
        private String value;
    }
}
