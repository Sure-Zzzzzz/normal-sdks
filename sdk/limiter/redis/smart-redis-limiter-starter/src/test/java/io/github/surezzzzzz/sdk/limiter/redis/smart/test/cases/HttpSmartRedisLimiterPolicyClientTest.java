package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.client.HttpSmartRedisLimiterPolicyClient;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.json.SmartRedisLimiterPolicyJsonCodec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * 默认远程策略 HTTP 客户端认证与响应契约测试
 *
 * @author surezzzzzz
 */
public class HttpSmartRedisLimiterPolicyClientTest {

    private static final String SNAPSHOT_URL = "http://management.internal/api/v1/policy/snapshot";

    @Test
    public void testFetchSendsConfiguredPolicyTokenAndEtagWithoutTokenInUrl() {
        SmartRedisLimiterProperties properties = properties("policy-token");
        HttpSmartRedisLimiterPolicyClient client = new HttpSmartRedisLimiterPolicyClient(properties, jsonCodec());
        MockRestServiceServer server = mockServer(client);

        server.expect(once(), requestTo(SNAPSHOT_URL + "?serviceCode=test-service"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(SmartRedisLimiterStarterConstant.HTTP_HEADER_POLICY_TOKEN, "policy-token"))
                .andExpect(header(SmartRedisLimiterStarterConstant.HTTP_HEADER_IF_NONE_MATCH, "\"v1\""))
                .andRespond(withStatus(HttpStatus.NOT_MODIFIED));

        assertTrue(client.fetch("test-service", "\"v1\"").isNotModified(),
                "304 响应必须保持未修改语义");
        server.verify();
    }

    @Test
    public void testFetchDoesNotSendPolicyTokenWhenNotConfigured() {
        SmartRedisLimiterProperties properties = properties(null);
        HttpSmartRedisLimiterPolicyClient client = new HttpSmartRedisLimiterPolicyClient(properties, jsonCodec());
        MockRestServiceServer server = mockServer(client);

        server.expect(once(), requestTo(SNAPSHOT_URL + "?serviceCode=test-service"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist(SmartRedisLimiterStarterConstant.HTTP_HEADER_POLICY_TOKEN))
                .andRespond(withStatus(HttpStatus.NOT_MODIFIED));

        assertTrue(client.fetch("test-service", null).isNotModified(),
                "未配置 token 时仍必须保持原有抓取协议");
        server.verify();
    }

    @Test
    public void testFetchRejectsUnexpectedStatusAndMissingEtag() {
        HttpSmartRedisLimiterPolicyClient statusClient = new HttpSmartRedisLimiterPolicyClient(properties(null), jsonCodec());
        MockRestServiceServer statusServer = mockServer(statusClient);
        statusServer.expect(once(), requestTo(SNAPSHOT_URL + "?serviceCode=test-service"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertErrorCode(ErrorCode.POLICY_RESPONSE_INVALID,
                () -> statusClient.fetch("test-service", null), "非 200/304 必须拒绝");
        statusServer.verify();

        HttpSmartRedisLimiterPolicyClient etagClient = new HttpSmartRedisLimiterPolicyClient(properties(null), jsonCodec());
        MockRestServiceServer etagServer = mockServer(etagClient);
        etagServer.expect(once(), requestTo(SNAPSHOT_URL + "?serviceCode=test-service"))
                .andRespond(withStatus(HttpStatus.OK)
                        .body("{}")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON));

        assertErrorCode(ErrorCode.POLICY_RESPONSE_INVALID,
                () -> etagClient.fetch("test-service", null), "200 缺失 ETag 必须拒绝");
        etagServer.verify();
    }

    @Test
    public void testFetchRejectsDeclaredAndStreamingOversizeResponse() {
        SmartRedisLimiterProperties declaredProperties = properties(null);
        declaredProperties.getRemotePolicy().setMaxResponseBytes(4L);
        HttpSmartRedisLimiterPolicyClient declaredClient =
                new HttpSmartRedisLimiterPolicyClient(declaredProperties, jsonCodec());
        MockRestServiceServer declaredServer = mockServer(declaredClient);
        HttpHeaders headers = new HttpHeaders();
        headers.setETag("\"v1\"");
        headers.setContentLength(5L);
        declaredServer.expect(once(), requestTo(SNAPSHOT_URL + "?serviceCode=test-service"))
                .andRespond(withStatus(HttpStatus.OK)
                        .headers(headers)
                        .body("12345"));

        assertErrorCode(ErrorCode.POLICY_RESPONSE_INVALID,
                () -> declaredClient.fetch("test-service", null), "声明超限响应不得解码");
        declaredServer.verify();

        SmartRedisLimiterProperties streamingProperties = properties(null);
        streamingProperties.getRemotePolicy().setMaxResponseBytes(4L);
        HttpSmartRedisLimiterPolicyClient streamingClient =
                new HttpSmartRedisLimiterPolicyClient(streamingProperties, this::consume);
        MockRestServiceServer streamingServer = mockServer(streamingClient);
        HttpHeaders streamingHeaders = new HttpHeaders();
        streamingHeaders.setETag("\"v1\"");
        streamingHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
        streamingServer.expect(once(), requestTo(SNAPSHOT_URL + "?serviceCode=test-service"))
                .andRespond(withStatus(HttpStatus.OK)
                        .headers(streamingHeaders)
                        .body("12345"));

        assertErrorCode(ErrorCode.POLICY_FETCH_FAILED,
                () -> streamingClient.fetch("test-service", null), "流式超限必须作为传输失败处理");
        streamingServer.verify();
    }

    @Test
    public void testFetchKeepsTransportAndJsonErrorClassification() {
        HttpSmartRedisLimiterPolicyClient transportClient = new HttpSmartRedisLimiterPolicyClient(properties(null), jsonCodec());
        MockRestServiceServer transportServer = mockServer(transportClient);
        transportServer.expect(once(), requestTo(SNAPSHOT_URL + "?serviceCode=test-service"))
                .andRespond(withException(new IOException("connect failed")));

        assertErrorCode(ErrorCode.POLICY_FETCH_FAILED,
                () -> transportClient.fetch("test-service", null), "传输失败必须稳定分类");
        transportServer.verify();

        HttpSmartRedisLimiterPolicyClient jsonClient = new HttpSmartRedisLimiterPolicyClient(
                properties(null), inputStream -> {
            throw new SmartRedisLimiterException(ErrorCode.POLICY_JSON_INVALID, "malformed json");
        });
        MockRestServiceServer jsonServer = mockServer(jsonClient);
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setETag("\"v1\"");
        jsonServer.expect(once(), requestTo(SNAPSHOT_URL + "?serviceCode=test-service"))
                .andRespond(withStatus(HttpStatus.OK)
                        .headers(jsonHeaders)
                        .body("not-json"));

        assertErrorCode(ErrorCode.POLICY_JSON_INVALID,
                () -> jsonClient.fetch("test-service", null), "JSON 失败不得被改写为传输失败");
        jsonServer.verify();
    }

    private SmartRedisLimiterProperties properties(String policyToken) {
        SmartRedisLimiterProperties properties = new SmartRedisLimiterProperties();
        properties.getRemotePolicy().setSnapshotUrl(SNAPSHOT_URL);
        properties.getRemotePolicy().setPolicyToken(policyToken);
        return properties;
    }

    private SmartRedisLimiterPolicyJsonCodec jsonCodec() {
        return inputStream -> {
            throw new AssertionError("当前测试响应不得解析策略正文");
        };
    }

    private io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot consume(
            InputStream inputStream) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            throw new AssertionError("应在达到字节上限前抛出异常，实际="
                    + new String(output.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void assertErrorCode(String expectedCode, Runnable action, String message) {
        SmartRedisLimiterException exception = assertThrows(
                SmartRedisLimiterException.class, action::run, message);
        assertEquals(expectedCode, exception.getErrorCode(), message);
    }

    private MockRestServiceServer mockServer(HttpSmartRedisLimiterPolicyClient client) {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        return MockRestServiceServer.bindTo(restTemplate).build();
    }
}
