package io.github.surezzzzzz.sdk.auth.iam.core.spi;

/**
 * 凭证校验型外部身份源认证器。
 *
 * <p>适用于前端直接提交用户名与凭证、由适配器到外部身份源校验的登录方式
 * （LDAP bind、远程密码源等）。实现由独立适配器模块提供，不得依赖 IAM Server。</p>
 *
 * <p>实现契约：</p>
 * <ul>
 *   <li>外部源判定凭据错误时抛出 {@code ErrorCode.EXTERNAL_BAD_CREDENTIALS} 的
 *       {@link io.github.surezzzzzz.sdk.auth.iam.core.exception.IamProtocolException}；</li>
 *   <li>外部源不可达或故障时抛出 {@code ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE}，
 *       不得将不可用伪装为凭据错误；</li>
 *   <li>认证成功必须返回 externalId 稳定唯一的 {@link ExternalIdentity}。</li>
 * </ul>
 *
 * @author surezzzzzz
 */
public interface ExternalCredentialAuthenticator {

    /**
     * 登录方式编码（全局唯一，两个适配器注册相同编码会导致装配失败）。
     *
     * @return 登录方式编码，如 ldap-password
     */
    String providerCode();

    /**
     * 到外部身份源校验用户名与凭证。
     *
     * @param username   用户在外部体系中的账号名
     * @param credential 凭证（如明文密码）
     * @return 认证成功的外部身份
     */
    ExternalIdentity authenticate(String username, String credential);
}
