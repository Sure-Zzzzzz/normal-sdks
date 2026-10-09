package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.support;

import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.constant.SimpleAkskOpenApiClientConstant;
import io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model.ListClientsQuery;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理 OpenAPI 路径与查询参数拼装（单一事实源，防 Feign / RestTemplate 两线漂移）。
 *
 * <p>仅拼非敏感路径与查询参数；不携带认证头、不处理序列化。</p>
 *
 * @author surezzzzzz
 */
public final class AkskOpenApiClientUriHelper {

    private AkskOpenApiClientUriHelper() {
    }

    /**
     * Client 管理资源基路径（/api/client）。
     *
     * @return 路径
     */
    public static String clientBase() {
        return SimpleAkskOpenApiClientConstant.API_BASE_PATH + "/" + SimpleAkskOpenApiClientConstant.RESOURCE_CLIENT;
    }

    /**
     * 单 Client 路径（/api/client/{clientId}）。
     *
     * @param clientId Client 标识
     * @return 路径
     */
    public static String client(String clientId) {
        return clientBase() + "/" + clientId;
    }

    /**
     * Client Secret 轮换路径（/api/client/{clientId}/secret）。
     *
     * @param clientId Client 标识
     * @return 路径
     */
    public static String clientSecret(String clientId) {
        return client(clientId) + "/secret";
    }

    /**
     * 应用授权资源基路径（/api/application-authorization）。
     *
     * @return 路径
     */
    public static String authorizationBase() {
        return SimpleAkskOpenApiClientConstant.API_BASE_PATH
                + "/" + SimpleAkskOpenApiClientConstant.RESOURCE_AUTHORIZATION;
    }

    /**
     * 单 Client 应用授权路径（/api/application-authorization/{clientId}）。
     *
     * @param clientId Client 标识
     * @return 路径
     */
    public static String authorization(String clientId) {
        return authorizationBase() + "/" + clientId;
    }

    /**
     * 应用授权撤销路径（/api/application-authorization/{clientId}/revoke）。
     *
     * @param clientId Client 标识
     * @return 路径
     */
    public static String authorizationRevoke(String clientId) {
        return authorization(clientId) + "/revoke";
    }

    /**
     * Token 管理资源基路径（/api/token）。
     *
     * @return 路径
     */
    public static String tokenBase() {
        return SimpleAkskOpenApiClientConstant.API_BASE_PATH + "/" + SimpleAkskOpenApiClientConstant.RESOURCE_TOKEN;
    }

    /**
     * 单 Token 路径（/api/token/{id}）。
     *
     * @param id Token 标识
     * @return 路径
     */
    public static String token(String id) {
        return tokenBase() + "/" + id;
    }

    /**
     * Token 撤销路径（/api/token/{id}/revoke）。
     *
     * @param id Token 标识
     * @return 路径
     */
    public static String tokenRevoke(String id) {
        return token(id) + "/revoke";
    }

    /**
     * Client 列表查询参数拼装（跳过空值；clientIds 以逗号分隔——与 server List 参数绑定一致）。
     *
     * @param query 查询条件
     * @return 形如 clientIds=a,b&page=1 的查询串（可为空串）
     */
    public static String clientListQuery(ListClientsQuery query) {
        if (query == null) {
            return "";
        }
        List<String> pairs = new ArrayList<>();
        if (query.getOwnerUserId() != null) {
            pairs.add(SimpleAkskOpenApiClientConstant.PARAM_OWNER_USER_ID + "=" + encode(query.getOwnerUserId()));
        }
        if (query.getType() != null) {
            pairs.add(SimpleAkskOpenApiClientConstant.PARAM_TYPE + "=" + encode(query.getType()));
        }
        if (query.getPage() != null) {
            pairs.add(SimpleAkskOpenApiClientConstant.PARAM_PAGE + "=" + query.getPage());
        }
        if (query.getSize() != null) {
            pairs.add(SimpleAkskOpenApiClientConstant.PARAM_SIZE + "=" + query.getSize());
        }
        return String.join("&", pairs);
    }

    /**
     * 批量查询查询串（clientIds 逗号拼接后整体 URL 编码，与分页形态互斥）。
     *
     * @param clientIds Client 标识列表（上限 100）
     * @return 查询串（空列表返回空串）
     */
    public static String clientIdsBatchQuery(java.util.List<String> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) {
            return "";
        }
        return SimpleAkskOpenApiClientConstant.PARAM_CLIENT_IDS + "=" + encode(String.join(",", clientIds));
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // UTF-8 恒在——不可达分支，保底返回原值
            return value;
        }
    }
}
