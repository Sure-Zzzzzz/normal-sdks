package io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.constant;

/**
 * Simple IAM OIDC Adapter 常量
 *
 * @author surezzzzzz
 */
public final class SimpleIamOidcAdapterConstant {

    /**
     * 配置前缀（包名层级，与仓库其他 starter 一致）
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc";
    /**
     * 登录方式编码（对应登录契约 LoginProvider.type = sso）
     */
    public static final String PROVIDER_CODE = "oidc";
    /**
     * 默认授权 scope
     */
    public static final String DEFAULT_SCOPES = "openid,profile,email";
    /**
     * 授权暂存上下文的 Redis key 前缀（sure-auth-iam 前缀经 redis-route 落 default 数据源）
     */
    public static final String PENDING_KEY_PREFIX = "sure-auth-iam:oidc-pending:";
    /**
     * 授权暂存上下文的保留时长（分钟）：给足用户在 IdP 页面停留的往返预算
     */
    public static final int PENDING_TTL_MINUTES = 10;
    /**
     * IdP 连接超时（毫秒）：token 端点与 JWKS 拉取共用
     */
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5000;
    /**
     * IdP 读超时（毫秒）：token 端点与 JWKS 拉取共用
     */
    public static final int DEFAULT_READ_TIMEOUT_MILLIS = 10000;
    /**
     * 错误码：已引入但配置不完整
     */
    public static final String ERROR_CONFIG_INCOMPLETE = "OIDC_ADAPTER_001";
    /**
     * 错误码：授权暂存上下文存取失败（序列化 / Redis 异常）
     */
    public static final String ERROR_PENDING_STATE_IO = "OIDC_ADAPTER_002";
    /**
     * 模板：已引入但配置不完整
     * 参数: 配置前缀 CONFIG_PREFIX
     */
    public static final String TEMPLATE_CONFIG_INCOMPLETE = "%s 已引入但配置不完整："
            + "issuer / authorization-uri / token-uri / jwk-set-uri / "
            + "client-id / client-secret 均为必填";

    private SimpleIamOidcAdapterConstant() {
        throw new UnsupportedOperationException("常量类不允许实例化");
    }
}
