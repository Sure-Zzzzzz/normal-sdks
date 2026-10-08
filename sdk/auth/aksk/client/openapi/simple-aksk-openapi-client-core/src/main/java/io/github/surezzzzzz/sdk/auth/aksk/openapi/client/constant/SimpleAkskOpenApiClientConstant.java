package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.constant;

/**
 * AKSK Server 管理 OpenAPI 客户端契约常量。
 *
 * <p>本家族零新增配置键：管理 API 与 Token 端点同在 AKSK Server 一个进程上，
 * 连接配置全部复用 AKSK 底座前缀 {@code io.github.surezzzzzz.sdk.auth.aksk.client.*}
 * （enable / client-id / client-secret / server-url）。</p>
 *
 * @author surezzzzzz
 */
public final class SimpleAkskOpenApiClientConstant {

    /**
     * AKSK 底座配置前缀（复用，非本家族自有）。
     */
    public static final String BASE_CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.aksk.client";

    /**
     * 底座 server-url 配置键完整占位（Feign 形态 url 引用；同时是换 Token 与管理 API 的目标）。
     */
    public static final String SERVER_URL_PLACEHOLDER = "${" + BASE_CONFIG_PREFIX + ".server-url}";

    /**
     * 管理面 API 基路径。
     */
    public static final String API_BASE_PATH = "/api";

    /**
     * Client 管理资源段。
     */
    public static final String RESOURCE_CLIENT = "client";

    /**
     * 应用授权资源段。
     */
    public static final String RESOURCE_AUTHORIZATION = "application-authorization";

    /**
     * Token 管理资源段。
     */
    public static final String RESOURCE_TOKEN = "token";

    // ==================== 查询参数名（与 server wire 契约逐字一致） ====================

    /**
     * Client 标识参数名。
     */
    public static final String PARAM_CLIENT_ID = "clientId";

    /**
     * 页码参数名。
     */
    public static final String PARAM_PAGE = "page";

    /**
     * 每页数量参数名。
     */
    public static final String PARAM_SIZE = "size";

    /**
     * 归属用户参数名。
     */
    public static final String PARAM_OWNER_USER_ID = "ownerUserId";

    /**
     * Client 类型参数名。
     */
    public static final String PARAM_TYPE = "type";

    /**
     * Client 标识列表参数名（批量查询，上限 100）。
     */
    public static final String PARAM_CLIENT_IDS = "clientIds";

    /**
     * 轮换 Secret 时是否重置访问令牌缓存参数名。
     */
    public static final String PARAM_RESET_ACCESS_TOKEN_CACHE = "resetAccessTokenCache";

    /**
     * clientIds 单次查询上限（server 契约校验）。
     */
    public static final int MAX_CLIENT_IDS = 100;

    private SimpleAkskOpenApiClientConstant() {
    }
}
