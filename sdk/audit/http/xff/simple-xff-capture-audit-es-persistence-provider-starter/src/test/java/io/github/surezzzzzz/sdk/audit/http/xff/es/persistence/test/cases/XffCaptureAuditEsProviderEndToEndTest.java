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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    private static final long WAIT_TIMEOUT_MILLIS = 10000L;
    private static final long WAIT_INTERVAL_MILLIS = 100L;
    private static final Set<String> CORE_FIELD_NAMES = new LinkedHashSet<>(Arrays.asList(
            "eventId", "capturedTime", "applicationName", "requestId", "traceId",
            "requestMethod", "requestUri", "hostList", "xffPresent", "xffRawList", "xffIpList",
            "publicIpList", "applicationRawRemoteAddress", "applicationRemoteIp",
            "classificationVersion"));

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
        headers.add("X-Forwarded-For", PUBLIC_IPV4 + ", " + PRIVATE_IPV4
                + ", unknown, , " + PUBLIC_IPV4 + ", " + PUBLIC_IPV6_RAW);
        headers.add(TestAuditContextProvider.REQUEST_ID_HEADER, REQUEST_ID);
        headers.add(TestAuditContextProvider.TRACE_ID_HEADER, TRACE_ID);
        headers.add(TestAuditContextProvider.CLIENT_ID_HEADER, CLIENT_ID);
        headers.add(TestAuditContextProvider.UNMAPPED_EXTENSION_HEADER,
                UNMAPPED_EXTENSION_VALUE);

        ResponseEntity<String> response = restTemplate.exchange("/audit-e2e", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        Map<String, Object> hit = awaitHitByRequestId(REQUEST_ID);
        Map<String, Object> source = map(hit.get("_source"));

        log.info("E2E 响应状态：{}，物理索引：{}，文档ID={}，字段：{}",
                response.getStatusCodeValue(), physicalIndex, hit.get("_id"), source.keySet());
        assertEquals(200, response.getStatusCodeValue(), "真实 HTTP 请求应成功");
        assertTrue(ElasticsearchLowLevelRequestHelper.indexExists(client, physicalIndex),
                "首次真实写入应由 Persistence/Route 创建物理日索引");
        assertFalse(ElasticsearchLowLevelRequestHelper.indexExists(client, LOGICAL_INDEX),
                "逻辑索引不能被直接作为物理索引写入");
        Set<String> expectedDocumentFields = new LinkedHashSet<>(CORE_FIELD_NAMES);
        expectedDocumentFields.add("extensions");
        assertEquals(expectedDocumentFields, source.keySet(),
                "最终文档只能包含 15 个核心字段和固定 extensions 信封");
        assertFalse(source.containsKey(TestAuditContextProvider.CLIENT_ID_EXTENSION),
                "业务扩展不能平铺到审计文档顶层");
        assertEquals(source.get("eventId"), hit.get("_id"), "ES 文档 ID 必须等于 eventId");
        assertEquals("xff-capture-audit-test-service", source.get("applicationName"),
                "应用名应来自 spring.application.name");
        assertEquals(REQUEST_ID, source.get("requestId"), "requestId 应由同步 Provider 补充");
        assertEquals(TRACE_ID, source.get("traceId"), "traceId 应由同步 Provider 补充");
        assertEquals("GET", source.get("requestMethod"), "HTTP 方法应准确");
        assertEquals("/audit-e2e", source.get("requestUri"), "URI 应不含 query string");
        List<?> hostList = (List<?>) source.get("hostList");
        assertNotNull(hostList, "Host 列表应存在");
        assertEquals(1, hostList.size(), "真实 HTTP 请求应产生一个 Host 值");
        assertTrue(String.valueOf(hostList.get(0)).contains("localhost"),
                "Host 应来自真实随机端口请求");
        assertEquals(Boolean.TRUE, source.get("xffPresent"), "XFF present 应准确");
        assertEquals(Arrays.asList(PUBLIC_IPV4, PRIVATE_IPV4, "unknown", "", PUBLIC_IPV4,
                PUBLIC_IPV6_RAW), source.get("xffRawList"), "原始 XFF 链应完整保留");
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

        assertEquals(1L, countByTerm("publicIpList", PUBLIC_IPV4),
                "应能按公网 IP 精确查询");
        assertEquals(1L, countByTerm("xffIpList", "10.20.0.0/16"),
                "应能按内网 CIDR 查询");
        assertEquals(1L, countByTerm("extensions.clientId", CLIENT_ID),
                "应能按已声明扩展字段精确查询");
        assertEquals(0L, countByTerm("extensions.unmappedExtension", UNMAPPED_EXTENSION_VALUE),
                "未声明扩展字段不能自动变为可查询字段");
        assertMappingType("xffIpList", "ip");
        assertMappingType("publicIpList", "ip");
        assertMappingType("applicationRemoteIp", "ip");
        assertExtensionsMapping();
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
        String body = "{\"query\":{\"term\":{\"" + field + "\":\"" + value + "\"}}}";
        Request request = ElasticsearchLowLevelRequestHelper.newJsonRequest(
                SimpleElasticsearchRouteConstant.HTTP_METHOD_POST,
                "/" + physicalIndex + "/_count", body);
        Response response = ElasticsearchLowLevelRequestHelper.execute(client, request);
        return ((Number) parse(ElasticsearchLowLevelRequestHelper.readResponseBody(response)).get("count"))
                .longValue();
    }

    private void assertMappingType(String field, String expectedType) throws Exception {
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
        String actualType = String.valueOf(map(properties.get(field)).get("type"));

        log.info("mapping 字段类型：field={}，type={}", field, actualType);
        assertEquals(expectedType, actualType, field + " mapping 类型应准确");
    }

    private void assertExtensionsMapping() throws Exception {
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
        Map<String, Object> extensionsMapping = map(properties.get("extensions"));
        Map<String, Object> extensionProperties = map(extensionsMapping.get("properties"));
        String clientIdType = String.valueOf(map(extensionProperties.get(
                TestAuditContextProvider.CLIENT_ID_EXTENSION)).get("type"));

        log.info("extensions mapping：type={}，dynamic={}，clientId.type={}",
                extensionsMapping.get("type"), extensionsMapping.get("dynamic"), clientIdType);
        Object extensionsType = extensionsMapping.get("type");
        assertTrue(extensionsType == null || "object".equals(extensionsType),
                "extensions 必须为 object，ES 6/7 回显可省略 object 类型");
        assertEquals("false", String.valueOf(extensionsMapping.get("dynamic")),
                "extensions 必须使用 dynamic:false");
        assertEquals("keyword", clientIdType, "extensions.clientId 必须为 keyword");
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

        @GetMapping("/audit-e2e")
        String audit() {
            return "ok";
        }
    }
}
