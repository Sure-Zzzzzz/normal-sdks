package io.github.surezzzzzz.sdk.auth.aksk.openapi.resttemplate.client;

import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception.AkskOpenApiBadRequestException;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception.AkskOpenApiTransportException;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception.AkskOpenApiUnauthorizedException;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * 默认实现契约形状测试：20 端点请求（路径/方法/查询参数）+ 响应反序列化 + 异常族映射。
 *
 * <p>用 MockRestServiceServer 断言 RestTemplate 出站形态（路径与方法精确匹配 core UriHelper 拼装），
 * 不发起真实网络。</p>
 *
 * @author surezzzzzz
 */
class DefaultAkskOpenApiClientTest {

    private RestTemplate restTemplate;

    private MockRestServiceServer server;

    private DefaultAkskOpenApiClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new DefaultAkskOpenApiClient(restTemplate, "http://aksk.example.test");
    }

    private void expectJson(HttpMethod m, String path, String bodyJson, String respJson) {
        org.springframework.test.web.client.ResponseActions actions = server.expect(requestTo(path))
                .andExpect(method(m));
        if (respJson == null) {
            actions.andRespond(withStatus(org.springframework.http.HttpStatus.OK));
        } else {
            actions.andRespond(withSuccess(respJson, MediaType.APPLICATION_JSON));
        }
    }

    private String p(String path) {
        return "http://aksk.example.test" + path;
    }

    @Test
    void createClientPostsToClientBase() {
        expectJson(HttpMethod.POST, p("/api/client"),
                null,
                "{\"clientId\":\"AKP1\",\"clientSecret\":\"SK1\",\"type\":\"platform\",\"name\":\"x\"}");
        CreateClientRequest request = new CreateClientRequest();
        request.setType("platform");
        request.setName("x");
        CreateClientResponse response = client.createClient(request);
        assertThat(response.getClientId()).isEqualTo("AKP1");
        assertThat(response.getClientSecret()).isEqualTo("SK1");
        server.verify();
    }

    @Test
    void listClientsSkipsNullQueryParams() {
        ListClientsQuery q = new ListClientsQuery();
        q.setPage(2);
        expectJson(HttpMethod.GET, p("/api/client?page=2"), null,
                "{\"data\":[],\"total\":0,\"page\":2,\"size\":20,\"totalPages\":0}");
        PageResponse<?> page = client.listClients(q);
        assertThat(page.getPage()).isEqualTo(2);
        server.verify();
    }

    @Test
    void listClientsByClientIdsBuildsBatchQuery() {
        expectJson(HttpMethod.GET, p("/api/client?clientIds=a%2Cb"), null,
                "{\"clients\":{\"AKP1\":{\"clientId\":\"AKP1\"}}}");
        BatchClientResponse batch = client.listClientsByClientIds(java.util.Arrays.asList("a", "b"));
        assertThat(batch.getClients().get("AKP1").getClientId()).isEqualTo("AKP1");
        server.verify();
    }

    @Test
    void rotateSecretAppendsCacheParam() {
        expectJson(HttpMethod.PUT, p("/api/client/AKP1/secret?resetAccessTokenCache=false"), null,
                "{\"clientId\":\"AKP1\",\"clientSecret\":\"SK2\"}");
        ResetSecretResponse response = client.rotateSecret("AKP1", false);
        assertThat(response.getClientSecret()).isEqualTo("SK2");
        server.verify();
    }

    @Test
    void createAuthorizationCarriesClientIdQuery() {
        expectJson(HttpMethod.POST, p("/api/application-authorization?clientId=AKP1"), null,
                "{\"clientId\":\"AKP1\",\"admitted\":true,\"authorizationVersion\":1}");
        ApplicationAuthorizationRequest request = new ApplicationAuthorizationRequest();
        request.setAdmitted(true);
        io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.ApplicationAuthorizationResponse response =
                client.createAuthorization("AKP1", request);
        assertThat(response.getAdmitted()).isTrue();
        server.verify();
    }

    @Test
    void tokenStatisticsHitsStatisticsPath() {
        expectJson(HttpMethod.GET, p("/api/token/statistics"), null,
                "{\"totalCount\":10,\"activeCount\":5,\"revokedCount\":3,\"expiredCount\":2,"
                        + "\"mysqlCount\":10,\"redisCount\":5,\"bothCount\":5}");
        TokenStatisticsResponse stats = client.getTokenStatistics();
        assertThat(stats.getTotalCount()).isEqualTo(10);
        assertThat(stats.getActiveCount()).isEqualTo(5);
        server.verify();
    }

    @Test
    void revokeTokensByClientIdDeletesWithQuery() {
        expectJson(HttpMethod.DELETE, p("/api/token?clientId=AKP1"), null,
                "{\"revokedCount\":4}");
        io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.BatchRevokeResponse response =
                client.revokeTokensByClientId("AKP1");
        assertThat(response.getRevokedCount()).isEqualTo(4);
        server.verify();
    }

    @Test
    void deleteExpiredHitsExpiredPath() {
        expectJson(HttpMethod.DELETE, p("/api/token/expired"), null,
                "{\"deletedCount\":7,\"message\":\"ok\"}");
        DeleteExpiredResponse response = client.deleteExpiredTokens();
        assertThat(response.getDeletedCount()).isEqualTo(7);
        server.verify();
    }

    @Test
    void httpErrorMapsToExceptionFamily() {
        server.expect(requestTo(p("/api/client")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.listClients(new ListClientsQuery()))
                .isInstanceOf(AkskOpenApiUnauthorizedException.class);
        server.verify();
    }

    @Test
    void badRequestMapsToBadRequestException() {
        server.expect(requestTo(p("/api/client")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.listClients(null))
                .isInstanceOf(AkskOpenApiBadRequestException.class);
        server.verify();
    }

    @Test
    void transportFailureMapsToTransportException() {
        server.expect(requestTo(p("/api/client")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withException(new java.io.IOException("connection reset")));
        assertThatThrownBy(() -> client.listClients(null))
                .isInstanceOf(AkskOpenApiTransportException.class);
    }

    @Test
    void trimsTrailingSlashOnBaseUrl() {
        DefaultAkskOpenApiClient c = new DefaultAkskOpenApiClient(restTemplate, "http://aksk.example.test/");
        server.expect(requestTo(p("/api/token/statistics")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"totalCount\":0,\"activeCount\":0,\"revokedCount\":0,"
                        + "\"expiredCount\":0,\"mysqlCount\":0,\"redisCount\":0,"
                        + "\"bothCount\":0}", MediaType.APPLICATION_JSON));
        assertThat(c.getTokenStatistics().getTotalCount()).isEqualTo(0);
        server.verify();
    }

    @Test
    void deleteClientToleratesEmptyBody() {
        expectJson(HttpMethod.DELETE, p("/api/client/AKP1"), null, null);
        client.deleteClient("AKP1");
        server.verify();
    }

    @Test
    void revokeTokenToleratesEmptyBody() {
        server.expect(requestTo(p("/api/token/t1/revoke")))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.OK));
        client.revokeToken("t1");
        server.verify();
    }
}
