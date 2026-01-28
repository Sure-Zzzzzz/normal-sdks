package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

/**
 * OAuth2 Token Helper
 *
 * <p>用于测试中从 aksk-server 获取真实的 JWT Token
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class OAuth2TokenHelper {

    @Value("${test.oauth2.client-id}")
    private String clientId;

    @Value("${test.oauth2.client-secret}")
    private String clientSecret;

    @Value("${test.oauth2.token-uri}")
    private String tokenUri;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从 aksk-server 获取 JWT Token
     *
     * @return JWT Token
     */
    public String getToken() {
        try {
            log.info("Getting token from aksk-server: {}", tokenUri);
            log.info("Client ID: {}", clientId);

            // 构造请求头 - Basic Auth
            String auth = clientId + ":" + clientSecret;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Authorization", "Basic " + encodedAuth);

            // 构造请求体 - grant_type=client_credentials
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

            // 调用 token 端点
            ResponseEntity<String> response = restTemplate.exchange(
                    tokenUri,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                String accessToken = jsonResponse.get("access_token").asText();
                log.info("Successfully obtained token from aksk-server");
                log.debug("Token: {}", accessToken);
                return accessToken;
            } else {
                throw new RuntimeException("Failed to get token, status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to get token from aksk-server", e);
            throw new RuntimeException("Failed to get token: " + e.getMessage(), e);
        }
    }
}
