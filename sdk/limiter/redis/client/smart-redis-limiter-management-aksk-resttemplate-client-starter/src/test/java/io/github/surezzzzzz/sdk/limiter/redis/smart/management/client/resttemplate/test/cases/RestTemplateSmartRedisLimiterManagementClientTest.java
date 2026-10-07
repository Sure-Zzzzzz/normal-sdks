package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.resttemplate.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.model.SmartRedisLimiterPolicyFetchResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.resttemplate.RestTemplateSmartRedisLimiterManagementClient;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.resttemplate.configuration.SmartRedisLimiterManagementClientProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.support.JacksonSmartRedisLimiterManagementJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * RestTemplate 策略客户端契约测试（条件请求头/304/200/字节上限/未配置端点）
 *
 * @author surezzzzzz
 */
@Slf4j
public class RestTemplateSmartRedisLimiterManagementClientTest {

    private SmartRedisLimiterManagementClientProperties properties;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private RestTemplateSmartRedisLimiterManagementClient client;

    @BeforeEach
    public void setUp() {
        properties = new SmartRedisLimiterManagementClientProperties();
        properties.setPolicySnapshotUrl("http://management.internal/api/v1/policy/snapshot");
        properties.setTypedPolicySnapshotUrl("http://management.internal/api/v2/policy/snapshot");
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new RestTemplateSmartRedisLimiterManagementClient(
                properties, new JacksonSmartRedisLimiterManagementJsonCodec(), restTemplate);
    }

    @Test
    public void testFetchPolicySendsIfNoneMatchAndParses200() {
        String body = "{\"schemaVersion\":\"1\",\"serviceCode\":\"test-service\","
                + "\"revision\":7,\"publishedAt\":\"2026-07-18T00:00:00Z\",\"policies\":[]}";
        server.expect(requestTo("http://management.internal/api/v1/policy/snapshot?serviceCode=test-service"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("If-None-Match", "\"etag-1\""))
                .andRespond(request -> {
                    org.springframework.mock.http.client.MockClientHttpResponse response =
                            new org.springframework.mock.http.client.MockClientHttpResponse(
                                    body.getBytes(java.nio.charset.StandardCharsets.UTF_8), HttpStatus.OK);
                    response.getHeaders().set("ETag", "\"etag-2\"");
                    return response;
                });
        SmartRedisLimiterPolicyFetchResult result = client.fetchPolicy("test-service", "\"etag-1\"");
        log.info("200 拉取: etag={}, revision={}", result.getEtag(), result.getSnapshot().getRevision());
        assertEquals("\"etag-2\"", result.getEtag());
        assertEquals(7L, result.getSnapshot().getRevision());
        server.verify();
    }

    @Test
    public void testFetchPolicyNotModifiedOn304() {
        server.expect(requestTo("http://management.internal/api/v1/policy/snapshot?serviceCode=test-service"))
                .andRespond(withStatus(HttpStatus.NOT_MODIFIED));
        assertTrue(client.fetchPolicy("test-service", "\"etag-1\"").isNotModified(), "304 应映射为未修改");
    }

    @Test
    public void testFetchPolicyRejectsUnexpectedStatus() {
        server.expect(requestTo("http://management.internal/api/v1/policy/snapshot?serviceCode=test-service"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThrows(SmartRedisLimiterException.class, () -> client.fetchPolicy("test-service", null),
                "非 200/304 状态必须拒绝");
    }

    @Test
    public void testFetchTypedPolicyParsesV2Snapshot() {
        String body = "{\"schemaVersion\":\"2\",\"serviceCode\":\"test-service\",\"policyEpoch\":1,"
                + "\"revision\":5,\"publishedAt\":\"2026-10-06T00:00:00Z\",\"rules\":[]}";
        server.expect(requestTo("http://management.internal/api/v2/policy/snapshot?serviceCode=test-service"))
                .andRespond(request -> {
                    org.springframework.mock.http.client.MockClientHttpResponse response =
                            new org.springframework.mock.http.client.MockClientHttpResponse(
                                    body.getBytes(java.nio.charset.StandardCharsets.UTF_8), HttpStatus.OK);
                    response.getHeaders().set("ETag", "\"etag-9\"");
                    return response;
                });
        assertEquals(1L, client.fetchTypedPolicy("test-service", null).getSnapshot().getPolicyEpoch(),
                "v2 快照应携带代次");
    }

    @Test
    public void testMissingUrlFailsLoudly() {
        properties.setPolicySnapshotUrl(null);
        assertThrows(SmartRedisLimiterException.class, () -> client.fetchPolicy("test-service", null),
                "端点未配置必须响亮失败");
    }
}
