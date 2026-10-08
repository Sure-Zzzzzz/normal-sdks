package io.github.surezzzzzz.sdk.auth.aksk.openapi.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception.*;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.*;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.support.AkskOpenApiClientUriHelper;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.support.AkskOpenApiHttpErrorMapper;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.support.AkskOpenApiJsonCodec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约形状测试：方法面计数断言（防两线漂移的锚）+ DTO 字段名断言（防重命名漂移）+
 * UriHelper 路径断言 + HttpErrorMapper 映射断言。
 *
 * @author surezzzzzz
 */
class ContractShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void interfaceExposesExactlyTwentyEndpoints() {
        int client = 0;
        int auth = 0;
        int token = 0;
        for (Method m : AkskOpenApiClient.class.getMethods()) {
            String n = m.getName();
            if (Arrays.asList("createClient", "listClients", "getClient", "updateClient",
                    "rotateSecret", "deleteClient", "syncUserScopes").contains(n)) {
                client++;
            } else if (Arrays.asList("createAuthorization", "listAuthorizations", "getAuthorization",
                    "replaceAuthorization", "revokeAuthorization").contains(n)) {
                auth++;
            } else if (Arrays.asList("listTokens", "listRedisTokens", "getToken", "revokeToken",
                    "deleteToken", "deleteExpiredTokens", "getTokenStatistics",
                    "revokeTokensByClientId").contains(n)) {
                token++;
            }
        }
        assertThat(client).as("Client 管理端点").isEqualTo(7);
        assertThat(auth).as("应用授权端点").isEqualTo(5);
        assertThat(token).as("Token 管理端点").isEqualTo(8);
        assertThat(client + auth + token).as("总端点数").isEqualTo(20);
    }

    @Test
    void dtoFieldNamesMatchWireContract() throws Exception {
        assertThat(fieldNames(CreateClientRequest.class)).containsExactly(
                "type", "name", "ownerUserId", "ownerUsername", "scopes");
        assertThat(fieldNames(UpdateClientRequest.class)).containsExactly(
                "enabled", "scopes", "name", "ownerUserId", "ownerUsername");
        assertThat(fieldNames(ListClientsQuery.class)).containsExactly(
                "clientIds", "ownerUserId", "type", "page", "size");
        assertThat(fieldNames(CreateClientResponse.class)).containsExactly(
                "clientId", "clientSecret", "type", "name");
        assertThat(fieldNames(ClientInfoResponse.class)).containsExactly(
                "clientId", "clientSecret", "clientName", "clientType", "ownerUserId",
                "ownerUsername", "scopes", "enabled", "clientIdIssuedAt", "lifecycleVersion",
                "targetApplicationId");
        assertThat(fieldNames(ResetSecretResponse.class)).containsExactly("clientId", "clientSecret");
        assertThat(fieldNames(SyncScopesResponse.class)).containsExactly(
                "ownerUserId", "updatedCount", "message");
        assertThat(fieldNames(ApiResponse.class)).containsExactly("message");
        assertThat(fieldNames(ApplicationAuthorizationRequest.class)).containsExactly(
                "applicationCode", "admitted", "roles", "pagePermissions", "apiPermissions",
                "dataGrantDocument", "manifestVersion", "manifestDigest");
        assertThat(fieldNames(TokenInfoResponse.class)).hasSize(12);
        assertThat(fieldNames(TokenStatisticsResponse.class)).containsExactly(
                "totalCount", "activeCount", "revokedCount", "expiredCount",
                "mysqlCount", "redisCount", "bothCount");
        assertThat(fieldNames(DeleteExpiredResponse.class)).containsExactly("deletedCount", "message");
        assertThat(fieldNames(BatchRevokeResponse.class)).containsExactly("revokedCount");
        assertThat(fieldNames(PageResponse.class)).containsExactly(
                "data", "total", "page", "size", "totalPages");
    }

    @Test
    void jsonRoundTripKeepsWireFieldNames() {
        CreateClientRequest request = new CreateClientRequest();
        request.setType("platform");
        request.setName("x");
        String json = AkskOpenApiJsonCodec.write(request);
        assertThat(json).contains("\"type\":\"platform\"").contains("\"name\":\"x\"");

        String pageJson = "{\"data\":[],\"total\":1,\"page\":1,\"size\":20,\"totalPages\":1}";
        PageResponse<ApiResponse> page = AkskOpenApiJsonCodec.read(pageJson, PageResponse.class);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getTotalPages()).isEqualTo(1);
    }

    @Test
    void uriHelperBuildsContractPaths() {
        assertThat(AkskOpenApiClientUriHelper.clientBase()).isEqualTo("/api/client");
        assertThat(AkskOpenApiClientUriHelper.client("AKP1")).isEqualTo("/api/client/AKP1");
        assertThat(AkskOpenApiClientUriHelper.clientSecret("AKP1")).isEqualTo("/api/client/AKP1/secret");
        assertThat(AkskOpenApiClientUriHelper.authorizationBase()).isEqualTo("/api/application-authorization");
        assertThat(AkskOpenApiClientUriHelper.authorization("AKP1"))
                .isEqualTo("/api/application-authorization/AKP1");
        assertThat(AkskOpenApiClientUriHelper.authorizationRevoke("AKP1"))
                .isEqualTo("/api/application-authorization/AKP1/revoke");
        assertThat(AkskOpenApiClientUriHelper.tokenBase()).isEqualTo("/api/token");
        assertThat(AkskOpenApiClientUriHelper.token("t1")).isEqualTo("/api/token/t1");
        assertThat(AkskOpenApiClientUriHelper.tokenRevoke("t1")).isEqualTo("/api/token/t1/revoke");
    }

    @Test
    void listClientsQuerySkipsNulls() {
        ListClientsQuery q = new ListClientsQuery();
        assertThat(AkskOpenApiClientUriHelper.clientListQuery(q)).isEmpty();
        assertThat(AkskOpenApiClientUriHelper.clientListQuery(null)).isEmpty();

        ListClientsQuery full = new ListClientsQuery();
        full.setClientIds(Arrays.asList("a", "b"));
        full.setOwnerUserId("u1");
        full.setPage(2);
        full.setSize(50);
        String qs = AkskOpenApiClientUriHelper.clientListQuery(full);
        assertThat(qs).contains("clientIds=a%2Cb").contains("ownerUserId=u1")
                .contains("page=2").contains("size=50");
    }

    @Test
    void errorMapperCoversHttpSemantics() {
        assertThat(AkskOpenApiHttpErrorMapper.map(400, "GET", "/api/client", null))
                .isInstanceOf(AkskOpenApiBadRequestException.class);
        assertThat(AkskOpenApiHttpErrorMapper.map(401, "GET", "/api/client", null))
                .isInstanceOf(AkskOpenApiUnauthenticatedException.class);
        assertThat(AkskOpenApiHttpErrorMapper.map(403, "GET", "/api/client", null))
                .isInstanceOf(AkskOpenApiUnauthorizedException.class);
        assertThat(AkskOpenApiHttpErrorMapper.map(404, "GET", "/api/client", null))
                .isInstanceOf(AkskOpenApiNotFoundException.class);
        assertThat(AkskOpenApiHttpErrorMapper.map(409, "PUT", "/api/x", null))
                .isInstanceOf(AkskOpenApiConflictException.class);
        assertThat(AkskOpenApiHttpErrorMapper.map(413, "POST", "/api/x", null))
                .isInstanceOf(AkskOpenApiPayloadTooLargeException.class);
        assertThat(AkskOpenApiHttpErrorMapper.map(422, "POST", "/api/x", null))
                .isInstanceOf(AkskOpenApiUnprocessableException.class);
        assertThat(AkskOpenApiHttpErrorMapper.map(503, "GET", "/api/x", null))
                .isInstanceOf(AkskOpenApiServiceUnavailableException.class);
        assertThat(AkskOpenApiHttpErrorMapper.map(500, "GET", "/api/x", null))
                .isInstanceOf(SimpleAkskOpenApiClientException.class);
    }

    private String[] fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .filter(n -> !n.startsWith("$"))
                .toArray(String[]::new);
    }
}
