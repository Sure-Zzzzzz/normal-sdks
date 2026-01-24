package io.github.surezzzzzz.sdk.auth.aksk.server.test.helper;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.CreateClientRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.CreateClientResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;

/**
 * JWT Token测试辅助类
 * 为集成测试提供JWT Token获取和管理功能
 *
 * @author surezzzzzz
 */
@Slf4j
public class JwtTokenTestHelper {

    /**
     * 为测试创建临时Client并获取JWT Token
     *
     * @param restTemplate TestRestTemplate实例
     * @param port         服务端口
     * @return JWT Access Token
     */
    public static String createClientAndGetToken(TestRestTemplate restTemplate, int port) {
        // 1. 创建一个临时的平台级Client（用于测试）
        CreateClientRequest createRequest = new CreateClientRequest();
        createRequest.setType("platform"); // PLATFORM
        createRequest.setName("Test Client for JWT");

        String createUrl = String.format("http://localhost:%d/api/client", port);

        // 注意：创建Client的API也需要JWT认证，但我们假设有bootstrap client或者这个endpoint不需要认证
        // 如果需要认证，需要使用预先配置的bootstrap client
        ResponseEntity<CreateClientResponse> createResponse = restTemplate.postForEntity(
                createUrl,
                createRequest,
                CreateClientResponse.class
        );

        if (!createResponse.getStatusCode().is2xxSuccessful() || createResponse.getBody() == null) {
            throw new RuntimeException("Failed to create test client: " + createResponse.getStatusCode());
        }

        String clientId = createResponse.getBody().getClientId();
        String clientSecret = createResponse.getBody().getClientSecret();

        log.info("Created test client: {}", clientId);

        // 2. 使用Client凭证获取JWT Token
        return getTokenByClientCredentials(restTemplate, port, clientId, clientSecret);
    }

    /**
     * 使用Client凭证获取JWT Token
     *
     * @param restTemplate TestRestTemplate实例
     * @param port         服务端口
     * @param clientId     客户端ID
     * @param clientSecret 客户端密钥
     * @return JWT Access Token
     */
    public static String getTokenByClientCredentials(TestRestTemplate restTemplate, int port, String clientId, String clientSecret) {
        String tokenUrl = String.format("http://localhost:%d/oauth2/token", port);

        // 构建请求体
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "read write");

        // 设置Basic Auth
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Failed to get token: " + response.getStatusCode());
        }

        String accessToken = (String) response.getBody().get("access_token");
        log.info("Got access token for client: {}", clientId);

        return accessToken;
    }

    /**
     * 创建带有JWT Token的HttpHeaders
     *
     * @param token JWT Access Token
     * @return 包含Authorization header的HttpHeaders
     */
    public static HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * 创建带有JWT Token的HttpEntity
     *
     * @param token JWT Access Token
     * @param body  请求体
     * @param <T>   请求体类型
     * @return HttpEntity
     */
    public static <T> HttpEntity<T> createAuthEntity(String token, T body) {
        return new HttpEntity<>(body, createAuthHeaders(token));
    }

    /**
     * 创建不带body但带JWT Token的HttpEntity
     *
     * @param token JWT Access Token
     * @return HttpEntity
     */
    public static HttpEntity<Void> createAuthEntity(String token) {
        return new HttpEntity<>(createAuthHeaders(token));
    }
}
