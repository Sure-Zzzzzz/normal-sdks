package io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.test.cases;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.test.TestAuditContextFilter;
import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.test.TestAuditContextProvider;
import io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.test.XffCaptureAuditEsProviderTestApplication;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.WriteIndexResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchLowLevelRequestHelper;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XFF Capture Filter 到真实 Elasticsearch 的完整端到端测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = XffCaptureAuditEsProviderTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({XffCaptureAuditEsProviderEndToEndTest.TestController.class,
        XffCaptureAuditEsProviderEndToEndTest.TestFilterConfiguration.class})
class XffCaptureAuditEsProviderEndToEndTest {

    private static final String DATASOURCE = "secondary";
    private static final String LOGICAL_INDEX = "xff-capture-audit";
    private static final String PHYSICAL_INDEX_PREFIX = "xff-capture-audit-test-e2e-";
    private static final String REQUEST_ID = "request-e2e-001";
    private static final String TRACE_ID = "trace-e2e-001";
    private static final String CLIENT_ID = "client-e2e-001";
    private static final String UNMAPPED_EXTENSION_VALUE = "unmapped-e2e-001";
    private static final String PUBLIC_IPV4 = "8.8.8.8";
    private static final String PRIVATE_IPV4 = "10.20.30.40";
    private static final String PUBLIC_IPV6_RAW = "2001:4860:4860:0:0:0:0:8888";
    private static final String PUBLIC_IPV6_NORMALIZED = "2001:4860:4860::8888";
    private static final String X_REAL_IP = "10.20.30.41";
    private static final String X_FORWARDED_HOST = "audit.example.test";
    private static final String X_FORWARDED_PORT = "443";
    private static final String X_FORWARDED_PROTO = "https";
    private static final String REQUEST_BODY = "{\"message\":\"audit-body\"}";
    private static final long WAIT_TIMEOUT_MILLIS = 10000L;
    private static final long WAIT_INTERVAL_MILLIS = 100L;

    private RestClient client;
    private String physicalIndex;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SimpleElasticsearchRouteRegistry registry;

    @Autowired
    private WriteIndexResolver writeIndexResolver;

    @BeforeEach
    void setUp() throws Exception {
        client = registry.getLowLevelClient(DATASOURCE);
        physicalIndex = writeIndexResolver.resolveWriteIndex(LOGICAL_INDEX);
        deleteTestResources();
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteTestResources();
    }

    @Test
    void shouldCaptureHttpRequestAndWriteFinalDocumentToDailyIndex() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", PUBLIC_IPV4 + ", " + PRIVATE_IPV4 + ", unknown");
        headers.add("X-Forwarded-For", PUBLIC_IPV4 + ", " + PUBLIC_IPV6_RAW);
        headers.add("X-Real-IP", X_REAL_IP);
        headers.add("X-Forwarded-Host", X_FORWARDED_HOST);
        headers.add("X-Forwarded-Port", X_FORWARDED_PORT);
        headers.add("X-Forwarded-Proto", X_FORWARDED_PROTO);
        headers.add(TestAuditContextProvider.REQUEST_ID_HEADER, REQUEST_ID);
        headers.add(TestAuditContextProvider.TRACE_ID_HEADER, TRACE_ID);
        headers.add(TestAuditContextProvider.CLIENT_ID_HEADER, CLIENT_ID);
        headers.add(TestAuditContextProvider.UNMAPPED_EXTENSION_HEADER,
                UNMAPPED_EXTENSION_VALUE);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange("/audit-e2e?tag=one&tag=two", HttpMethod.POST,
                new HttpEntity<String>(REQUEST_BODY, headers), String.class);
        Map<String, Object> hit = awaitHitByRequestId(REQUEST_ID);
        Map<String, Object> source = map(hit.get("_source"));

        log.info("E2E 响应状态：{}，物理索引：{}，文档ID={}，字段={}，requestData字段={}",
                response.getStatusCodeValue(), physicalIndex, hit.get("_id"), source.keySet(),
                map(source.get("requestData")).keySet());
        assertEquals(200, response.getStatusCodeValue(), "真实 HTTP 请求应成功");
        assertEquals(REQUEST_BODY, response.getBody(), "Capture 不得破坏 Controller 接收的完整 Body");
        assertTrue(ElasticsearchLowLevelRequestHelper.indexExists(client, physicalIndex),
                "首次真实写入应由 Persistence/Route 创建物理日索引");
        assertFalse(ElasticsearchLowLevelRequestHelper.indexExists(client, LOGICAL_INDEX),
                "逻辑索引不能被直接作为物理索引写入");
        assertTrue(source.containsKey("requestData"), "Listener 的请求数据快照必须写入 ES 文档");
        assertFalse(source.containsKey(TestAuditContextProvider.CLIENT_ID_EXTENSION),
                "业务扩展不能平铺到审计文档顶层");
        assertEquals(source.get("eventId"), hit.get("_id"), "ES 文档 ID 必须等于 eventId");
        assertEquals("xff-capture-audit-test-service", source.get("applicationName"),
                "应用名应来自 spring.application.name");
        assertEquals(REQUEST_ID, source.get("requestId"), "requestId 应由同步 Provider 补充");
        assertEquals(TRACE_ID, source.get("traceId"), "traceId 应由同步 Provider 补充");
        assertEquals("POST", source.get("requestMethod"), "HTTP 方法应准确");
        assertEquals("/audit-e2e", source.get("requestUri"), "URI 应不含 query string");
        List<?> hostList = (List<?>) source.get("hostList");
        assertNotNull(hostList, "Host 列表应存在");
        assertEquals(1, hostList.size(), "真实 HTTP 请求应产生一个 Host 值");
        assertTrue(String.valueOf(hostList.get(0)).contains("localhost"),
                "Host 应来自真实随机端口请求");
        assertEquals(Boolean.TRUE, source.get("xffPresent"), "XFF present 应准确");
        assertEquals(Arrays.asList(PUBLIC_IPV4 + ", " + PRIVATE_IPV4 + ", unknown",
                        PUBLIC_IPV4 + ", " + PUBLIC_IPV6_RAW),
                source.get("xffRawHeaderList"), "同名 XFF Header 边界应完整保留");
        assertEquals(Arrays.asList(PUBLIC_IPV4, PRIVATE_IPV4, "unknown", PUBLIC_IPV4,
                PUBLIC_IPV6_RAW), source.get("xffRawList"), "原始 XFF 链应完整保留");
        assertEquals(Collections.singletonList(X_REAL_IP), source.get("xRealIpList"),
                "X-Real-IP 应完整入库");
        assertEquals(Collections.singletonList(X_FORWARDED_HOST), source.get("xForwardedHostList"),
                "X-Forwarded-Host 应完整入库");
        assertEquals(Collections.singletonList(X_FORWARDED_PORT), source.get("xForwardedPortList"),
                "X-Forwarded-Port 应完整入库");
        assertEquals(Collections.singletonList(X_FORWARDED_PROTO), source.get("xForwardedProtoList"),
                "X-Forwarded-Proto 应完整入库");
        assertEquals(Arrays.asList(PUBLIC_IPV4, PRIVATE_IPV4, PUBLIC_IPV6_NORMALIZED),
                source.get("xffIpList"), "合法 IP 应规范化并去重");
        assertEquals(Arrays.asList(PUBLIC_IPV4, PUBLIC_IPV6_NORMALIZED),
                source.get("publicIpList"), "公网 IP 子集应准确");
        assertNotNull(source.get("applicationRawRemoteAddress"), "应用原始远端地址应保留");
        assertNotNull(source.get("applicationRemoteIp"), "应用远端 IP 投影应存在");
        assertEquals("iana-2025-10-09", source.get("classificationVersion"),
                "分类版本应准确");
        assertTrue(String.valueOf(source.get("capturedTime")).endsWith("Z"),
                "capturedTime 应为 UTC date_time");
        Map<String, Object> extensions = map(source.get("extensions"));
        log.info("E2E 扩展字段：{}", extensions.keySet());
        assertEquals(CLIENT_ID, extensions.get(TestAuditContextProvider.CLIENT_ID_EXTENSION),
                "已声明扩展字段必须写入 _source");
        assertEquals(UNMAPPED_EXTENSION_VALUE,
                extensions.get(TestAuditContextProvider.UNMAPPED_EXTENSION),
                "未声明扩展字段必须可保留在 _source");
        assertRequestData(source.get("requestData"));

        assertEquals(1L, countByTerm("xffRawHeaderList", PUBLIC_IPV4 + ", " + PRIVATE_IPV4 + ", unknown"),
                "应能按原始 XFF Header 精确查询");
        assertEquals(1L, countByTerm("xRealIpList", X_REAL_IP),
                "应能按 X-Real-IP 精确查询");
        assertEquals(1L, countByTerm("xForwardedHostList", X_FORWARDED_HOST),
                "应能按 X-Forwarded-Host 精确查询");
        assertEquals(1L, countByTerm("xForwardedPortList", X_FORWARDED_PORT),
                "应能按 X-Forwarded-Port 精确查询");
        assertEquals(1L, countByTerm("xForwardedProtoList", X_FORWARDED_PROTO),
                "应能按 X-Forwarded-Proto 精确查询");
        assertEquals(1L, countByTerm("publicIpList", PUBLIC_IPV4),
                "应能按公网 IP 精确查询");
        assertEquals(1L, countByTerm("xffIpList", "10.20.0.0/16"),
                "应能按内网 CIDR 查询");
        assertEquals(1L, countByTerm("requestData.queryParameters.values.tag", "one"),
                "请求 Query 参数值应能直接精确查询");
        assertEquals(1L, countByTerm("requestData.body.text", REQUEST_BODY),
                "请求 Body 原文应能直接精确查询");
        assertEquals(1L, countByTerm(
                        "extensions." + TestAuditContextProvider.CLIENT_ID_EXTENSION, CLIENT_ID),
                "业务扩展值应能直接精确查询");
        assertKnownMappingTypes();
    }

    private void assertRequestData(Object value) {
        Map<String, Object> requestData = map(value);
        Map<String, Object> query = map(requestData.get("queryParameters"));
        Map<String, Object> form = map(requestData.get("formParameters"));
        Map<String, Object> body = map(requestData.get("body"));

        log.info("requestData 快照：query={}，form={}，body={}", requestData.keySet(),
                form.keySet(), body.keySet());
        assertEquals("CAPTURED", query.get("status"), "Query 状态必须随文档写入");
        assertEquals(Arrays.asList("one", "two"),
                map(query.get("values")).get("tag"), "Query 多值必须完整写入");
        assertEquals("DISABLED", form.get("status"), "未启用 Form 时状态必须原样保留");
        assertEquals("CAPTURED", body.get("status"), "Body 状态必须随文档写入");
        assertEquals("application/json", body.get("contentType"), "Body Content-Type 必须保留");
        assertEquals(REQUEST_BODY, body.get("text"), "Body 文本必须原样写入");
        assertEquals(REQUEST_BODY.length(), ((Number) body.get("capturedByteCount")).longValue(),
                "Body 保留字节数必须准确");
    }

    private Map<String, Object> awaitHitByRequestId(String requestId) throws Exception {
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MILLIS;
        String query = "{\"query\":{\"term\":{\"requestId\":\"" + requestId + "\"}}}";
        while (System.currentTimeMillis() < deadline) {
            if (!ElasticsearchLowLevelRequestHelper.indexExists(client, physicalIndex)) {
                Thread.sleep(WAIT_INTERVAL_MILLIS);
                continue;
            }
            ElasticsearchLowLevelRequestHelper.refreshIndex(client, physicalIndex);
            Request request = ElasticsearchLowLevelRequestHelper.newJsonRequest(
                    SimpleElasticsearchRouteConstant.HTTP_METHOD_POST,
                    "/" + physicalIndex + "/_search", query);
            Response response = ElasticsearchLowLevelRequestHelper.execute(client, request);
            Map<String, Object> body = parse(ElasticsearchLowLevelRequestHelper.readResponseBody(response));
            List<Map<String, Object>> hits = hits(body);
            if (!hits.isEmpty()) {
                return hits.get(0);
            }
            Thread.sleep(WAIT_INTERVAL_MILLIS);
        }
        fail("等待 XFF 审计文档超时，requestId=" + requestId);
        return Collections.emptyMap();
    }

    private long countByTerm(String field, String value) throws Exception {
        Map<String, Object> term = new LinkedHashMap<>();
        term.put(field, value);
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("term", term);
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("query", query);
        String body = new ObjectMapper().writeValueAsString(requestBody);
        Request request = ElasticsearchLowLevelRequestHelper.newJsonRequest(
                SimpleElasticsearchRouteConstant.HTTP_METHOD_POST,
                "/" + physicalIndex + "/_count", body);
        Response response = ElasticsearchLowLevelRequestHelper.execute(client, request);
        return ((Number) parse(ElasticsearchLowLevelRequestHelper.readResponseBody(response)).get("count"))
                .longValue();
    }

    private void assertKnownMappingTypes() throws Exception {
        Request request = ElasticsearchLowLevelRequestHelper.newRequest(
                SimpleElasticsearchRouteConstant.HTTP_METHOD_GET,
                "/" + physicalIndex + "/_mapping");
        Map<String, Object> body = parse(ElasticsearchLowLevelRequestHelper.readResponseBody(
                ElasticsearchLowLevelRequestHelper.execute(client, request)));
        Map<String, Object> indexMapping = map(body.get(physicalIndex));
        Map<String, Object> mappings = map(indexMapping.get("mappings"));
        Map<String, Object> properties = mappings.containsKey("_doc")
                ? map(map(mappings.get("_doc")).get("properties"))
                : map(mappings.get("properties"));

        Map<String, String> expectedTypes = new LinkedHashMap<>();
        expectedTypes.put("eventId", "keyword");
        expectedTypes.put("capturedTime", "date");
        expectedTypes.put("applicationName", "keyword");
        expectedTypes.put("requestId", "keyword");
        expectedTypes.put("traceId", "keyword");
        expectedTypes.put("requestMethod", "keyword");
        expectedTypes.put("requestUri", "keyword");
        expectedTypes.put("hostList", "keyword");
        expectedTypes.put("xffPresent", "boolean");
        expectedTypes.put("xffRawHeaderList", "keyword");
        expectedTypes.put("xffRawList", "keyword");
        expectedTypes.put("xRealIpList", "keyword");
        expectedTypes.put("xForwardedHostList", "keyword");
        expectedTypes.put("xForwardedPortList", "keyword");
        expectedTypes.put("xForwardedProtoList", "keyword");
        expectedTypes.put("xffIpList", "ip");
        expectedTypes.put("publicIpList", "ip");
        expectedTypes.put("applicationRawRemoteAddress", "keyword");
        expectedTypes.put("applicationRemoteIp", "ip");
        expectedTypes.put("classificationVersion", "keyword");

        for (Map.Entry<String, String> entry : expectedTypes.entrySet()) {
            assertEquals(entry.getValue(), String.valueOf(map(properties.get(entry.getKey())).get("type")),
                    entry.getKey() + " mapping 类型应准确");
        }

        Map<String, Object> requestData = map(properties.get("requestData"));
        Map<String, Object> requestDataProperties = map(requestData.get("properties"));
        Map<String, Object> queryParameters = map(requestDataProperties.get("queryParameters"));
        Map<String, Object> formParameters = map(requestDataProperties.get("formParameters"));
        Map<String, Object> requestBody = map(requestDataProperties.get("body"));
        assertNotNull(requestDataProperties, "requestData 必须定义固定子字段");
        assertNotNull(queryParameters.get("properties"), "queryParameters 必须定义固定子字段");
        assertNotNull(formParameters.get("properties"), "formParameters 必须定义固定子字段");
        assertNotNull(requestBody.get("properties"), "body 必须定义固定子字段");

        Map<String, Object> queryProperties = map(queryParameters.get("properties"));
        Map<String, Object> formProperties = map(formParameters.get("properties"));
        Map<String, Object> bodyProperties = map(requestBody.get("properties"));
        assertEquals("keyword", map(queryProperties.get("status")).get("type"),
                "Query status mapping 类型应准确");
        Map<String, Object> queryValuesProperties = map(map(queryProperties.get("values"))
                .get("properties"));
        assertEquals("keyword", map(queryValuesProperties.get("tag")).get("type"),
                "动态 Query 参数值必须映射为 keyword");
        assertEquals("keyword", map(formProperties.get("status")).get("type"),
                "Form status mapping 类型应准确");
        assertNotNull(formProperties.get("values"),
                "Form values 固定容器必须保留");
        assertEquals("keyword", map(bodyProperties.get("status")).get("type"),
                "Body status mapping 类型应准确");
        assertEquals("keyword", map(bodyProperties.get("contentType")).get("type"),
                "Body contentType mapping 类型应准确");
        assertEquals("long", map(bodyProperties.get("declaredContentLength")).get("type"),
                "Body declaredContentLength mapping 类型应准确");
        assertEquals("long", map(bodyProperties.get("capturedByteCount")).get("type"),
                "Body capturedByteCount mapping 类型应准确");
        Map<String, Object> bodyTextMapping = map(bodyProperties.get("text"));
        assertEquals("keyword", bodyTextMapping.get("type"), "Body text mapping 类型应准确");
        assertEquals(32766, ((Number) bodyTextMapping.get("ignore_above")).intValue(),
                "Body text 的精确索引长度上限应准确");

        Map<String, Object> extensionProperties = map(map(properties.get("extensions"))
                .get("properties"));
        assertEquals("keyword", map(extensionProperties.get(
                        TestAuditContextProvider.CLIENT_ID_EXTENSION)).get("type"),
                "业务扩展值必须映射为 keyword");
        log.info("已知 Audit Document 与 requestData mapping 已验证，字段数={}", expectedTypes.size());
    }

    private void deleteTestResources() throws Exception {
        if (client == null) {
            return;
        }
        assertTrue(physicalIndex.startsWith(PHYSICAL_INDEX_PREFIX),
                "只允许删除严格测试日索引前缀");
        ElasticsearchLowLevelRequestHelper.deleteIndex(client, physicalIndex);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> hits(Map<String, Object> body) {
        return (List<Map<String, Object>>) map(body.get("hits")).get("hits");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private Map<String, Object> parse(String value) throws Exception {
        return new ObjectMapper().readValue(value, new TypeReference<Map<String, Object>>() {
        });
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TestFilterConfiguration {

        @Bean
        FilterRegistrationBean<TestAuditContextFilter> testAuditContextFilter() {
            FilterRegistrationBean<TestAuditContextFilter> registration = new FilterRegistrationBean<>();
            registration.setFilter(new TestAuditContextFilter());
            registration.setName("testAuditContextFilter");
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }

    @RestController
    static class TestController {

        @PostMapping("/audit-e2e")
        String audit(@RequestBody String requestBody) {
            return requestBody;
        }
    }
}
