package io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.audit.http.xff.context.XffCaptureAuditContext;
import io.github.surezzzzzz.sdk.audit.http.xff.context.XffCaptureAuditContextProvider;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.WriteIndexResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 两个独立 ES 版本复用同一条 HTTP 到物理索引的验收断言。
 *
 * @author surezzzzzz
 */
@Slf4j
abstract class XffCaptureAuditEsProviderE2eBase {

    private static final String LOGICAL_INDEX = "xff-capture-audit";
    private static final String TEST_INDEX_PATTERN =
            "xff-capture-audit-jakarta-test-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final String BODY = "{\"message\":\"audit-body\"}";
    private static final String REQUEST_ID = "request-e2e-001";
    private static final String CLIENT_ID = "client-e2e-001";
    private static final long WAIT_TIMEOUT_MILLIS = 20000L;
    private static final long WAIT_INTERVAL_MILLIS = 100L;
    private static final ThreadLocal<XffCaptureAuditContext> CONTEXT = new ThreadLocal<>();

    private final ObjectMapper mapper = new ObjectMapper();
    private RestClient client;
    private String physicalIndex;
    private String templateName;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SimpleElasticsearchRouteRegistry routeRegistry;

    @Autowired
    private WriteIndexResolver writeIndexResolver;

    protected abstract String expectedElasticsearchVersion();

    @BeforeEach
    void setUp() throws Exception {
        client = routeRegistry.getLowLevelClient("primary");
        String actualVersion = responseJson(client.performRequest(new Request("GET", "/")))
                .path("version").path("number").asText();
        assertEquals(expectedElasticsearchVersion(), actualVersion,
                "每条 E2E 必须连接到声明的 Elasticsearch 服务端版本");
        physicalIndex = writeIndexResolver.resolveWriteIndex(LOGICAL_INDEX);
        assertTrue(physicalIndex.matches(TEST_INDEX_PATTERN), "测试只允许使用 UUID 独占索引");
        templateName = physicalIndex + "-template";
        installExactTemplate();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null && physicalIndex != null && templateName != null) {
            assertTrue(physicalIndex.matches(TEST_INDEX_PATTERN), "清理只允许使用 UUID 独占索引");
            deleteIfPresent("/" + physicalIndex);
            deleteIfPresent("/_index_template/" + templateName);
        }
    }

    @Test
    void shouldWriteCapturedHttpFactsThroughListenerAndPersistence() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Forwarded-For", "8.8.8.8, 10.20.30.40, unknown");
        headers.add("X-Real-IP", "10.20.30.41");
        headers.add("X-Forwarded-Host", "audit.example.test");
        headers.add("X-Test-Request-ID", REQUEST_ID);
        headers.add("X-Test-Client-ID", CLIENT_ID);

        ResponseEntity<String> response = restTemplate.exchange("/audit-e2e?tag=one&tag=two",
                HttpMethod.POST, new HttpEntity<>(BODY, headers), String.class);
        JsonNode hit = awaitSingleHit();
        JsonNode source = hit.path("_source");

        log.info("XFF Jakarta E2E：物理索引={}，响应状态={}，字段数={}",
                physicalIndex, response.getStatusCode().value(), source.size());
        assertEquals(200, response.getStatusCode().value(), "业务请求不应被审计链路改变");
        assertEquals(BODY, response.getBody(), "Capture 不得破坏 Controller 收到的 Body");
        assertFalse(hit.path("_id").asText().isEmpty(), "事件 ID 必须成为 ES 文档 ID");
        assertEquals(hit.path("_id").asText(), source.path("eventId").asText(),
                "ES 文档 ID 与审计事件 ID 必须一致");
        assertEquals("xff-capture-audit-jakarta-test-service", source.path("applicationName").asText());
        assertEquals("POST", source.path("requestMethod").asText());
        assertEquals("/audit-e2e", source.path("requestUri").asText(), "审计 URI 不应携带 Query");
        assertEquals(REQUEST_ID, source.path("requestId").asText(), "同步上下文必须写入审计文档");
        assertTrue(source.path("xffPresent").asBoolean(), "XFF 存在性应保留");
        assertEquals("8.8.8.8, 10.20.30.40, unknown",
                source.path("xffRawHeaderList").get(0).asText(), "原始 XFF Header 必须保留");
        assertEquals("8.8.8.8", source.path("xffIpList").get(0).asText());
        assertEquals("10.20.30.40", source.path("xffIpList").get(1).asText());
        assertEquals("10.20.30.41", source.path("xRealIpList").get(0).asText());
        assertEquals("audit.example.test", source.path("xForwardedHostList").get(0).asText());
        assertFalse(source.has("clientId"), "扩展字段不能平铺到文档顶层");
        assertEquals(CLIENT_ID, source.path("extensions").path("clientId").asText());
        assertEquals("CAPTURED", source.path("requestData").path("queryParameters")
                .path("status").asText());
        assertEquals("one", source.path("requestData").path("queryParameters")
                .path("values").path("tag").get(0).asText());
        assertEquals("two", source.path("requestData").path("queryParameters")
                .path("values").path("tag").get(1).asText());
        assertEquals("CAPTURED", source.path("requestData").path("body").path("status").asText());
        assertEquals("DISABLED", source.path("requestData").path("formParameters")
                .path("status").asText());
        assertEquals(BODY, source.path("requestData").path("body").path("text").asText());
        assertEquals(1L, countByTerm("xffIpList", "8.8.8.8"), "IP 字段应可精确查询");
        assertEquals(1L, countByTerm("requestData.body.text", BODY), "Body 字段应可精确查询");
        assertEquals(1L, countByTerm("requestData.queryParameters.values.tag", "one"),
                "Query 参数值应可精确查询");
        assertEquals(1L, countByTerm("extensions.clientId", CLIENT_ID),
                "扩展字段应可精确查询");
        assertEquals("ip", mappingType("xffIpList"), "测试模板必须保持 IP 字段类型");
        assertEquals("keyword", mappingType("requestData.body.text"),
                "测试模板必须保持 Body 精确查询类型");
        assertEquals("keyword", mappingType("requestData.queryParameters.values.tag"),
                "动态 Query 参数值必须是 keyword");
        assertEquals("keyword", mappingType("extensions.clientId"),
                "动态扩展字段必须是 keyword");
    }

    private void installExactTemplate() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.putArray("index_patterns").add(physicalIndex);
        root.put("priority", 100);
        ObjectNode mappings = root.putObject("template").putObject("mappings");
        ObjectNode keywordTemplate = mappings.putArray("dynamic_templates").addObject()
                .putObject("request_parameter_values_as_keyword");
        keywordTemplate.put("path_match", "requestData.*Parameters.values.*");
        keywordTemplate.put("match_mapping_type", "string");
        keywordTemplate.putObject("mapping").put("type", "keyword");
        ObjectNode extensionTemplate = mappings.withArray("dynamic_templates").addObject()
                .putObject("extension_values_as_keyword");
        extensionTemplate.put("path_match", "extensions.*");
        extensionTemplate.put("match_mapping_type", "string");
        extensionTemplate.putObject("mapping").put("type", "keyword");
        ObjectNode properties = mappings.putObject("properties");
        for (String field : new String[]{"eventId", "applicationName", "requestId", "traceId",
                "requestMethod", "requestUri", "hostList", "xffRawHeaderList", "xffRawList",
                "xRealIpList", "xForwardedHostList", "xForwardedPortList", "xForwardedProtoList",
                "applicationRawRemoteAddress", "classificationVersion"}) {
            properties.putObject(field).put("type", "keyword");
        }
        properties.putObject("capturedTime").put("type", "date");
        properties.putObject("xffPresent").put("type", "boolean");
        properties.putObject("xffIpList").put("type", "ip");
        properties.putObject("publicIpList").put("type", "ip");
        properties.putObject("applicationRemoteIp").put("type", "ip");
        properties.putObject("extensions").put("type", "object").putObject("properties");
        ObjectNode requestData = properties.putObject("requestData").putObject("properties");
        for (String field : new String[]{"queryParameters", "formParameters"}) {
            ObjectNode parameterFields = requestData.putObject(field).putObject("properties");
            parameterFields.putObject("status").put("type", "keyword");
            parameterFields.putObject("values").put("type", "object").putObject("properties");
        }
        ObjectNode bodyFields = requestData.putObject("body").putObject("properties");
        bodyFields.putObject("status").put("type", "keyword");
        bodyFields.putObject("contentType").put("type", "keyword");
        bodyFields.putObject("declaredContentLength").put("type", "long");
        bodyFields.putObject("capturedByteCount").put("type", "long");
        bodyFields.putObject("text").put("type", "keyword").put("ignore_above", 32766);

        Request request = new Request("PUT", "/_index_template/" + templateName);
        request.setJsonEntity(mapper.writeValueAsString(root));
        client.performRequest(request);
    }

    private JsonNode awaitSingleHit() throws Exception {
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            try {
                client.performRequest(new Request("POST", "/" + physicalIndex + "/_refresh"));
                Request search = new Request("POST", "/" + physicalIndex + "/_search");
                search.setJsonEntity("{\"query\":{\"match_all\":{}}}");
                JsonNode hits = responseJson(client.performRequest(search)).path("hits").path("hits");
                if (hits.size() == 1) {
                    return hits.get(0);
                }
            } catch (ResponseException e) {
                if (e.getResponse().getStatusLine().getStatusCode() != 404) {
                    throw e;
                }
            }
            Thread.sleep(WAIT_INTERVAL_MILLIS);
        }
        fail("等待 XFF 审计文档写入超时");
        return mapper.createObjectNode();
    }

    private long countByTerm(String field, String value) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("query").putObject("term").put(field, value);
        Request count = new Request("POST", "/" + physicalIndex + "/_count");
        count.setJsonEntity(mapper.writeValueAsString(body));
        return responseJson(client.performRequest(count)).path("count").asLong();
    }

    private String mappingType(String field) throws Exception {
        JsonNode properties = responseJson(client.performRequest(new Request("GET",
                "/" + physicalIndex + "/_mapping"))).path(physicalIndex).path("mappings")
                .path("properties");
        String[] parts = field.split("\\.");
        JsonNode current = properties;
        for (String part : parts) {
            current = current.path(part);
            if (!part.equals(parts[parts.length - 1])) {
                current = current.path("properties");
            }
        }
        assertFalse(current.isMissingNode(), "mapping 字段必须存在");
        return current.path("type").asText();
    }

    private JsonNode responseJson(Response response) throws IOException {
        return mapper.readTree(EntityUtils.toString(response.getEntity()));
    }

    private void deleteIfPresent(String endpoint) throws IOException {
        try {
            client.performRequest(new Request("DELETE", endpoint));
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() != 404) {
                throw e;
            }
        }
    }

    @RestController
    static class TestController {

        @PostMapping("/audit-e2e")
        String audit(@RequestBody String body) {
            return body;
        }
    }

    /**
     * 在 Capture Filter 前放入请求线程上下文，供 Listener 同步读取。
     */
    static class TestAuditContextFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            String clientId = request.getHeader("X-Test-Client-ID");
            Map<String, String> extensions = new LinkedHashMap<>();
            if (clientId != null) {
                extensions.put("clientId", clientId);
            }
            CONTEXT.set(new XffCaptureAuditContext(request.getHeader("X-Test-Request-ID"), null,
                    Collections.unmodifiableMap(extensions)));
            try {
                chain.doFilter(request, response);
            } finally {
                CONTEXT.remove();
            }
        }
    }

    @TestConfiguration
    static class TestFilterConfiguration {

        @Bean
        XffCaptureAuditContextProvider auditContextProvider() {
            return CONTEXT::get;
        }

        @Bean
        FilterRegistrationBean<TestAuditContextFilter> testAuditContextFilter() {
            FilterRegistrationBean<TestAuditContextFilter> registration = new FilterRegistrationBean<>();
            registration.setFilter(new TestAuditContextFilter());
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }
}
