package io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.constant;

/**
 * Portal Gateway adaptor core 的固定契约值。
 *
 * @author surezzzzzz
 */
public final class SimpleIamPortalGatewayAdaptorConstant {

    /**
     * HTTP 协议。
     */
    public static final String SCHEME_HTTP = "http";
    /**
     * 应用编码契约；必须与 IAM 可信应用写入校验保持一致。
     */
    public static final String APPLICATION_CODE_PATTERN = "[A-Za-z0-9_-]{1,64}";
    /**
     * 应用名称最大码点数。
     */
    public static final int MAX_APPLICATION_NAME_CODE_POINTS = 128;
    /**
     * Portal 路由前缀最大码点数。
     */
    public static final int MAX_ROUTE_PREFIX_CODE_POINTS = 128;
    /**
     * 公共路径和目标 origin 最大码点数。
     */
    public static final int MAX_PUBLIC_PATH_CODE_POINTS = 512;
    /**
     * 启用状态值。
     */
    public static final int STATUS_ACTIVE = 1;
    /**
     * 默认 HTTP 端口。
     */
    public static final int PORT_HTTP = 80;
    /**
     * 默认事件 schema 版本。
     */
    public static final String EVENT_SCHEMA_VERSION = "iam-portal-gateway-event/1";
    /**
     * 默认快照策略版本。
     */
    public static final String DEFAULT_POLICY_VERSION = "route-policy/1";
    /**
     * 不向业务上游转发调用方 Authorization 请求头。
     */
    public static final String FORWARD_AUTHORIZATION_NONE = "NONE";
    /**
     * 原样转发调用方 Authorization 请求头。
     */
    public static final String FORWARD_AUTHORIZATION_REQUEST = "REQUEST";
    /**
     * 平台保留路径。
     */
    public static final String RESERVED_ROOT = "/";
    /**
     * IAM 平台前缀。
     */
    public static final String RESERVED_IAM = "/iam";
    /**
     * IAM Web 应用前缀。
     */
    public static final String RESERVED_IAM_APP = "/app/iam";
    /**
     * AKSK 内置应用页面前缀。
     */
    public static final String RESERVED_AKSK_APP = "/app/aksk";
    /**
     * AKSK 客户端管理 API 前缀。
     */
    public static final String RESERVED_AKSK_CLIENT_API = "/api/client";
    /**
     * AKSK 个人凭据 API 前缀。
     */
    public static final String RESERVED_AKSK_PERSONAL_CLIENT_API = "/api/me/aksk-clients";
    /**
     * AKSK Token API 前缀。
     */
    public static final String RESERVED_AKSK_TOKEN_API = "/api/token";
    /**
     * AKSK 应用授权 API 前缀。
     */
    public static final String RESERVED_AKSK_APPLICATION_AUTHORIZATION_API = "/api/application-authorization";
    /**
     * OAuth 前缀。
     */
    public static final String RESERVED_OAUTH = "/oauth2";
    /**
     * 登录前缀。
     */
    public static final String RESERVED_LOGIN = "/login";
    /**
     * 健康检查前缀。
     */
    public static final String RESERVED_HEALTH = "/health";
    /**
     * Actuator 前缀。
     */
    public static final String RESERVED_ACTUATOR = "/actuator";
    /**
     * xDS 前缀。
     */
    public static final String RESERVED_XDS = "/xds";
    /**
     * 管理前缀。
     */
    public static final String RESERVED_ADMIN = "/admin";

    private SimpleIamPortalGatewayAdaptorConstant() {
        throw new UnsupportedOperationException("常量类不能实例化");
    }
}
