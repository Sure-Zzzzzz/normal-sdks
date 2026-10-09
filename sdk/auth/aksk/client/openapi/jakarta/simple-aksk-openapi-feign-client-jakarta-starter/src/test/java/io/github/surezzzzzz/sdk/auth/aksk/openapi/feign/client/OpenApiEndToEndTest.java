package io.github.surezzzzzz.sdk.auth.aksk.openapi.feign.client;

import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.ApplicationAuthorizationRequest;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.ApplicationAuthorizationResponse;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.CreateClientRequest;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.CreateClientResponse;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.ResetSecretResponse;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.TokenExchangeResponse;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.TokenStatisticsResponse;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.support.AkskOpenApiJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理 OpenAPI 端到端测试（Feign 形态六步闭环）。
 *
 * <p>前置（凭据与端点仅放被忽略的 src/test/resources/application-local.yml，由受管
 * application.yml 激活 local profile）：
 * <ol>
 *   <li>AKSK Server 测试形态已启动（如 8282，admin 开、devtest 库）</li>
 *   <li>Redis 已启动</li>
 *   <li>已创建一个持全部管理码（akskClient:* / akskToken:* / akskApplicationAuthorization:*）
 *       且 DATA 三资源（akskClient / akskToken / akskApplicationAuthorization）全动作、已准入的
 *       管理 AKP；凭据写入 application-local.yml</li>
 * </ol>
 *
 * <p>闭环步骤：建 Client → 配授权准入 → 用新 AK/SK 换 Token → statistics 200 →
 * rotateSecret 旧废新立 → 撤销授权 Token 全灭 → 清理后 404（Feign 裸 FeignException 口径）。
 * Server 或 Redis 未运行时用例直接失败，不跳过。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = OpenApiE2eTestApplication.class)
class OpenApiEndToEndTest {

    @Autowired
    private AkskOpenApiFeignClient openApiClient;

    @Value("${io.github.surezzzzzz.sdk.auth.aksk.client.server-url}")
    private String serverUrl;

    private String basicAuth(String ak, String sk) {
        return "Basic " + java.util.Base64.getEncoder()
                .encodeToString((ak + ":" + sk).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String exchangeToken(String ak, String sk) {
        RestTemplate plain = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", basicAuth(ak, sk));
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<String> entity = new HttpEntity<>("grant_type=client_credentials", headers);
        String body = plain.postForObject(serverUrl + "/oauth2/token", entity, String.class);
        return AkskOpenApiJsonCodec.read(body, TokenExchangeResponse.class).getAccessToken();
    }

    @Test
    void fullProvisioningClosedLoop() {
        // ===== 1. 建 Client =====
        CreateClientRequest request = new CreateClientRequest();
        request.setType("platform");
        request.setName("openapi-e2e-feign-target");
        CreateClientResponse created = openApiClient.createClient(request);
        assertNotNull(created.getClientId(), "clientId 应返回");
        assertNotNull(created.getClientSecret(), "clientSecret 应一次性返回");
        log.info("[1/6] 建 Client: {}", created.getClientId());

        io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.BatchClientResponse batch =
                openApiClient.listClientsByClientIds(List.of(created.getClientId()));
        assertNotNull(batch.getClients().get(created.getClientId()), "批量查询应含新建 Client");

        // ===== 2. 配授权 + 准入（先创建再完整替换，两次调用都走 Feign 客户端）=====
        ApplicationAuthorizationRequest authz = new ApplicationAuthorizationRequest();
        authz.setApplicationCode("aksk-server");
        authz.setAdmitted(true);
        authz.setApiPermissions(List.of("akskClient:read", "akskToken:read"));
        authz.setRoles(List.of());
        authz.setPagePermissions(List.of());
        authz.setManifestVersion("e2e-1");
        authz.setManifestDigest("e2e-digest");
        Map<String, Object> grant = new java.util.HashMap<>();
        grant.put("protocol", "simple-data-permission");
        grant.put("version", "1.0");
        grant.put("grants", List.of(
                Map.of("resource", "akskClient", "actions",
                        List.of("create", "read", "update", "delete"), "all", true, "constraints", List.of()),
                Map.of("resource", "akskToken", "actions",
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
        HttpHeaders probeHeaders = new HttpHeaders();
        probeHeaders.add("Authorization", "Bearer " + token);
        String stats = plain.exchange(serverUrl + "/api/token/statistics",
                HttpMethod.GET, new HttpEntity<>(probeHeaders), String.class).getBody();
        assertTrue(stats != null && stats.contains("totalCount"), "带 Token 应能调 statistics");
        log.info("[3/6] 新凭据换 Token + 管理 API 200");

        // ===== 4. 统计可查（Feign 调用链）=====
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
            HttpHeaders h = new HttpHeaders();
            h.add("Authorization", "Bearer " + newToken);
            plain.exchange(serverUrl + "/api/token/statistics",
                    HttpMethod.GET, new HttpEntity<>(h), String.class);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            newTokenRejected = e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403;
        }
        assertTrue(newTokenRejected, "撤销授权后旧 Token 应被拒");
        log.info("[6/6] 撤销授权: 活跃 Token 全灭");

        // ===== 清理 + Feign 错误口径（裸 FeignException 保留服务端状态码）=====
        openApiClient.deleteClient(created.getClientId());
        boolean goneAs404 = false;
        try {
            openApiClient.getClient(created.getClientId());
        } catch (feign.FeignException e) {
            goneAs404 = e.status() == 404;
        }
        assertTrue(goneAs404, "已删 Client 的查询应 404（FeignException 裸口径）");
        log.info("清理: Client 已删，Feign 404 口径验证过，闭环全绿");
    }
}
