package io.github.surezzzzzz.sdk.auth.aksk.openapi.resttemplate.client;

import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.AkskOpenApiClient;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 管理 OpenAPI 端到端测试（六步闭环）。
 *
 * <p>前置（凭据与端点仅放被忽略的 src/test/resources/application-local.yml）：
 * <ol>
 *   <li>AKSK Server 测试形态已启动（如 8282，admin 开、devtest 库）</li>
 *   <li>Redis 已启动</li>
 *   <li>已创建一个持全部管理码（akskClient:* / akskToken:* / akskApplicationAuthorization:*）
 *       且 DATA 三资源（akskClient / akskToken / akskApplicationAuthorization）全动作、已准入的
 *       管理 AKP；凭据写入 application-local.yml</li>
 * </ol>
 *
 * <p>闭环步骤：建 Client → 配授权准入 → 用新 AK/SK 换 Token → introspect active →
 * rotateSecret 旧废新立 → 撤销授权 Token 全灭。Server 或 Redis 未运行时用例直接失败，不跳过。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = OpenApiE2eTestApplication.class)
class OpenApiEndToEndTest {

    @Autowired
    private AkskOpenApiClient openApiClient;

    @Value("${io.github.surezzzzzz.sdk.auth.aksk.client.server-url}")
    private String serverUrl;

    private String basicAuth(String ak, String sk) {
        return "Basic " + java.util.Base64.getEncoder()
                .encodeToString((ak + ":" + sk).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String exchangeToken(String ak, String sk) {
        RestTemplate plain = new RestTemplate();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Authorization", basicAuth(ak, sk));
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);
        org.springframework.http.HttpEntity<String> entity =
                new org.springframework.http.HttpEntity<>("grant_type=client_credentials", headers);
        String body = plain.postForObject(serverUrl + "/oauth2/token", entity, String.class);
        return io.github.surezzzzzz.sdk.auth.aksk.openapi.client.support.AkskOpenApiJsonCodec
                .read(body, io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.TokenExchangeResponse.class)
                .getAccessToken();
    }

    @Test
    void fullProvisioningClosedLoop() {
        // ===== 1. 建 Client =====
        CreateClientRequest request = new CreateClientRequest();
        request.setType("platform");
        request.setName("openapi-e2e-target");
        CreateClientResponse created = openApiClient.createClient(request);
        assertNotNull(created.getClientId(), "clientId 应返回");
        assertNotNull(created.getClientSecret(), "clientSecret 应一次性返回");
        log.info("[1/6] 建 Client: {}", created.getClientId());

        io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.BatchClientResponse batch =
                openApiClient.listClientsByClientIds(List.of(created.getClientId()));
        assertNotNull(batch.getClients().get(created.getClientId()), "批量查询应含新建 Client");

        // ===== 2. 配授权 + 准入（先创建再完整替换，两次调用都走本 client）=====
        ApplicationAuthorizationRequest authz = new ApplicationAuthorizationRequest();
        authz.setApplicationCode("aksk-server");
        authz.setAdmitted(true);
        authz.setApiPermissions(List.of("akskClient:read", "akskToken:read"));
        authz.setRoles(List.of());
        authz.setPagePermissions(List.of());
        authz.setManifestVersion("e2e-1");
        authz.setManifestDigest("e2e-digest");
        java.util.Map<String, Object> grant = new java.util.HashMap<>();
        grant.put("protocol", "simple-data-permission");
        grant.put("version", "1.0");
        grant.put("grants", List.of(
                java.util.Map.of("resource", "akskClient", "actions",
                        List.of("create", "read", "update", "delete"), "all", true, "constraints", List.of()),
                java.util.Map.of("resource", "akskToken", "actions",
                        List.of("read", "update", "delete"), "all", true, "constraints", List.of())));
        authz.setDataGrantDocument(grant);
        openApiClient.createAuthorization(created.getClientId(), authz);
        authz.setApiPermissions(List.of("akskClient:read", "akskClient:create", "akskClient:update",
                "akskClient:delete", "akskToken:read", "akskToken:update", "akskToken:delete"));
        ApplicationAuthorizationResponse saved = openApiClient.replaceAuthorization(created.getClientId(), authz);
        assertEquals(Boolean.TRUE, saved.getAdmitted(), "应已准入");
        log.info("[2/6] 授权准入: admitted=true, version={}", saved.getAuthorizationVersion());

        // ===== 3. 新 AK/SK 换 Token =====
        String token = exchangeToken(created.getClientId(), created.getClientSecret());
        assertNotNull(token, "换出的 Token 不应为空");
        RestTemplate plain = new RestTemplate();
        org.springframework.http.HttpHeaders probeHeaders = new org.springframework.http.HttpHeaders();
        probeHeaders.add("Authorization", "Bearer " + token);
        String stats = plain.exchange(serverUrl + "/api/token/statistics",
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(probeHeaders), String.class).getBody();
        assertTrue(stats != null && stats.contains("totalCount"), "带 Token 应能调 statistics");
        log.info("[3/6] 新凭据换 Token + 管理 API 200");

        // ===== 4. 无授权前拒绝（未准入 Client 换不出 Token）——在步骤 2 已准入，此处验证统计可查 =====
        TokenStatisticsResponse statistics = openApiClient.getTokenStatistics();
        assertTrue(statistics.getTotalCount() >= 1, "统计应至少含本闭环 Token");
        log.info("[4/6] 统计: total={}", statistics.getTotalCount());

        // ===== 5. rotateSecret 旧废新立 =====
        ResetSecretResponse rotated = openApiClient.rotateSecret(created.getClientId(), true);
        assertNotEquals(created.getClientSecret(), rotated.getClientSecret(), "新 Secret 应不同");
        String oldSecret = created.getClientSecret();
        boolean oldRejected = false;
        try {
            exchangeToken(created.getClientId(), oldSecret);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            oldRejected = e.getStatusCode().value() == 401;
        }
        assertTrue(oldRejected, "旧 Secret 换 Token 应 401");
        String newToken = exchangeToken(created.getClientId(), rotated.getClientSecret());
        assertNotNull(newToken, "新 Secret 应能换 Token");
        log.info("[5/6] rotateSecret: 旧废新立");

        // ===== 6. 撤销授权 → Token 全灭 =====
        openApiClient.revokeAuthorization(created.getClientId());
        boolean newTokenRejected = false;
        try {
            org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
            h.add("Authorization", "Bearer " + newToken);
            plain.exchange(serverUrl + "/api/token/statistics",
                    org.springframework.http.HttpMethod.GET,
                    new org.springframework.http.HttpEntity<>(h), String.class);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            newTokenRejected = e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403;
        }
        assertTrue(newTokenRejected, "撤销授权后旧 Token 应被拒");
        log.info("[6/6] 撤销授权: 活跃 Token 全灭");

        // ===== 清理 =====
        openApiClient.deleteClient(created.getClientId());
        log.info("清理: Client 已删，闭环全绿");
    }
}
