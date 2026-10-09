package io.github.surezzzzzz.sdk.auth.aksk.openapi.resttemplate.client;

import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.AkskOpenApiClient;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.constant.SimpleAkskOpenApiClientConstant;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.*;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.support.AkskOpenApiClientUriHelper;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.support.AkskOpenApiHttpErrorMapper;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.support.AkskOpenApiJsonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * 基于 AKSK 底座 RestTemplate 的管理 OpenAPI 默认实现（20 端点）。
 *
 * <p>出站请求经底座 {@code akskClientRestTemplate}（拦截器自动挂 Bearer 头）；本类不管理令牌、
 * 不重试、不生成幂等键。URI 拼装与错误映射复用 core 的 {@link AkskOpenApiClientUriHelper} 与
 * {@link AkskOpenApiHttpErrorMapper}（HTTP 非 2xx 映射为 core 异常族——与 Feign 形态的裸
 * FeignException 口径不同，README 双线各自写明）。DEBUG 仅记录方法/状态/耗时；
 * clientSecret、Authorization、完整 URL query 不落日志。</p>
 *
 * @author surezzzzzz
 */
public class DefaultAkskOpenApiClient implements AkskOpenApiClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultAkskOpenApiClient.class);

    private final RestTemplate akskClientRestTemplate;

    private final String baseUrl;

    /**
     * 构造默认实现。
     *
     * @param akskClientRestTemplate 底座认证 RestTemplate（拦截器已挂 AKSK 令牌链）
     * @param baseUrl                AKSK Server 基地址（协议主机端口，来自底座 server-url 配置）
     */
    public DefaultAkskOpenApiClient(RestTemplate akskClientRestTemplate, String baseUrl) {
        this.akskClientRestTemplate = akskClientRestTemplate;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    // ==================== Client 管理（7） ====================

    @Override
    public CreateClientResponse createClient(CreateClientRequest request) {
        return exchange(HttpMethod.POST, uri(AkskOpenApiClientUriHelper.clientBase()),
                request, CreateClientResponse.class);
    }

    @Override
    public PageResponse<ClientInfoResponse> listClients(ListClientsQuery query) {
        String qs = AkskOpenApiClientUriHelper.clientListQuery(query);
        URI target = uri(qs.isEmpty() ? AkskOpenApiClientUriHelper.clientBase()
                : AkskOpenApiClientUriHelper.clientBase() + "?" + qs);
        return exchange(HttpMethod.GET, target, null, PageResponse.class);
    }

    @Override
    public BatchClientResponse listClientsByClientIds(List<String> clientIds) {
        String qs = AkskOpenApiClientUriHelper.clientIdsBatchQuery(clientIds);
        URI target = uri(qs.isEmpty() ? AkskOpenApiClientUriHelper.clientBase()
                : AkskOpenApiClientUriHelper.clientBase() + "?" + qs);
        return exchange(HttpMethod.GET, target, null, BatchClientResponse.class);
    }

    @Override
    public ClientInfoResponse getClient(String clientId) {
        return exchange(HttpMethod.GET, uri(AkskOpenApiClientUriHelper.client(clientId)), null,
                ClientInfoResponse.class);
    }

    @Override
    public ApiResponse updateClient(String clientId, UpdateClientRequest request) {
        return exchange(HttpMethod.PATCH, uri(AkskOpenApiClientUriHelper.client(clientId)), request,
                ApiResponse.class);
    }

    @Override
    public ResetSecretResponse rotateSecret(String clientId, boolean resetAccessTokenCache) {
        URI target = UriComponentsBuilder.fromUri(uri(AkskOpenApiClientUriHelper.clientSecret(clientId)))
                .queryParam(SimpleAkskOpenApiClientConstant.PARAM_RESET_ACCESS_TOKEN_CACHE, resetAccessTokenCache)
                .build(true).toUri();
        return exchange(HttpMethod.PUT, target, null, ResetSecretResponse.class);
    }

    @Override
    public void deleteClient(String clientId) {
        exchange(HttpMethod.DELETE, uri(AkskOpenApiClientUriHelper.client(clientId)), null, Void.class);
    }

    @Override
    public SyncScopesResponse syncUserScopes(String ownerUserId) {
        URI target = UriComponentsBuilder.fromUri(uri(AkskOpenApiClientUriHelper.clientBase()))
                .queryParam(SimpleAkskOpenApiClientConstant.PARAM_OWNER_USER_ID, ownerUserId)
                .build(true).toUri();
        return exchange(HttpMethod.PATCH, target, null, SyncScopesResponse.class);
    }

    // ==================== 应用授权（5） ====================

    @Override
    public ApplicationAuthorizationResponse createAuthorization(String clientId,
                                                                ApplicationAuthorizationRequest request) {
        URI target = UriComponentsBuilder.fromUri(uri(AkskOpenApiClientUriHelper.authorizationBase()))
                .queryParam(SimpleAkskOpenApiClientConstant.PARAM_CLIENT_ID, clientId)
                .build(true).toUri();
        return exchange(HttpMethod.POST, target, request, ApplicationAuthorizationResponse.class);
    }

    @Override
    public PageResponse<ApplicationAuthorizationResponse> listAuthorizations(Integer page, Integer size) {
        URI target = UriComponentsBuilder.fromUri(uri(AkskOpenApiClientUriHelper.authorizationBase()))
                .queryParam(SimpleAkskOpenApiClientConstant.PARAM_PAGE, page == null ? 1 : page)
                .queryParam(SimpleAkskOpenApiClientConstant.PARAM_SIZE, size == null ? 20 : size)
                .build(true).toUri();
        return exchange(HttpMethod.GET, target, null, PageResponse.class);
    }

    @Override
    public ApplicationAuthorizationResponse getAuthorization(String clientId) {
        return exchange(HttpMethod.GET, uri(AkskOpenApiClientUriHelper.authorization(clientId)), null,
                ApplicationAuthorizationResponse.class);
    }

    @Override
    public ApplicationAuthorizationResponse replaceAuthorization(String clientId,
                                                                 ApplicationAuthorizationRequest request) {
        return exchange(HttpMethod.PUT, uri(AkskOpenApiClientUriHelper.authorization(clientId)), request,
                ApplicationAuthorizationResponse.class);
    }

    @Override
    public void revokeAuthorization(String clientId) {
        exchange(HttpMethod.POST, uri(AkskOpenApiClientUriHelper.authorizationRevoke(clientId)), null,
                Void.class);
    }

    // ==================== Token 管理（8） ====================

    @Override
    public PageResponse<TokenInfoResponse> listTokens(Integer page, Integer size) {
        return tokenPage(null, page, size);
    }

    @Override
    public PageResponse<TokenInfoResponse> listRedisTokens(Integer page, Integer size) {
        return tokenPage("/redis", page, size);
    }

    @Override
    public TokenInfoResponse getToken(String id) {
        return exchange(HttpMethod.GET, uri(AkskOpenApiClientUriHelper.token(id)), null,
                TokenInfoResponse.class);
    }

    @Override
    public void revokeToken(String id) {
        exchange(HttpMethod.POST, uri(AkskOpenApiClientUriHelper.tokenRevoke(id)), null, Void.class);
    }

    @Override
    public void deleteToken(String id) {
        exchange(HttpMethod.DELETE, uri(AkskOpenApiClientUriHelper.token(id)), null, Void.class);
    }

    @Override
    public DeleteExpiredResponse deleteExpiredTokens() {
        return exchange(HttpMethod.DELETE, uri(AkskOpenApiClientUriHelper.tokenBase() + "/expired"), null,
                DeleteExpiredResponse.class);
    }

    @Override
    public TokenStatisticsResponse getTokenStatistics() {
        return exchange(HttpMethod.GET, uri(AkskOpenApiClientUriHelper.tokenBase() + "/statistics"), null,
                TokenStatisticsResponse.class);
    }

    @Override
    public BatchRevokeResponse revokeTokensByClientId(String clientId) {
        URI target = UriComponentsBuilder.fromUri(uri(AkskOpenApiClientUriHelper.tokenBase()))
                .queryParam(SimpleAkskOpenApiClientConstant.PARAM_CLIENT_ID, clientId)
                .build(true).toUri();
        return exchange(HttpMethod.DELETE, target, null, BatchRevokeResponse.class);
    }

    // ==================== 传输内核 ====================

    private PageResponse<TokenInfoResponse> tokenPage(String suffix, Integer page, Integer size) {
        String path = suffix == null ? AkskOpenApiClientUriHelper.tokenBase()
                : AkskOpenApiClientUriHelper.tokenBase() + suffix;
        URI target = UriComponentsBuilder.fromUri(uri(path))
                .queryParam(SimpleAkskOpenApiClientConstant.PARAM_PAGE, page == null ? 1 : page)
                .queryParam(SimpleAkskOpenApiClientConstant.PARAM_SIZE, size == null ? 20 : size)
                .build(true).toUri();
        return exchange(HttpMethod.GET, target, null, PageResponse.class);
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }

    private <T> T exchange(HttpMethod method, URI target, Object body, Class<T> type) {
        long start = System.currentTimeMillis();
        String path = target.getPath();
        Integer status = null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(body == null ? null : AkskOpenApiJsonCodec.write(body), headers);
            ResponseEntity<String> response = akskClientRestTemplate.exchange(target, method, entity, String.class);
            status = response.getStatusCode().value();
            if (type == null || Void.class.equals(type) || response.getBody() == null) {
                return null;
            }
            return AkskOpenApiJsonCodec.read(response.getBody(), type);
        } catch (HttpStatusCodeException e) {
            status = e.getStatusCode().value();
            throw AkskOpenApiHttpErrorMapper.map(e.getStatusCode().value(), method.name(), path,
                    e.getResponseHeaders() == null ? null
                            : e.getResponseHeaders().getFirst("request-id"));
        } catch (ResourceAccessException e) {
            throw new io.github.surezzzzzz.sdk.auth.aksk.openapi.client.exception.AkskOpenApiTransportException(
                    "AKSK OpenAPI 通信失败：" + method.name() + " " + path, method.name(), path, null, e);
        } finally {
            if (log.isDebugEnabled()) {
                log.debug("AKSK OpenAPI 调用：method={}, path={}, status={}, 耗时={}ms", method.name(), path,
                        status, System.currentTimeMillis() - start);
            }
        }
    }
}
