package io.github.surezzzzzz.sdk.auth.iam.core.spi;

import java.util.Map;

/**
 * 跳转型外部身份源登录提供方。
 *
 * <p>适用于浏览器先跳转外部 IdP、再携带授权结果回调 IAM 的登录方式
 * （OIDC、CAS、SAML 等）。实现由独立适配器模块提供，不得依赖 IAM Server；
 * 回调参数的换取与校验（如 code 换 token、ID token 验签、ticket 校验）由实现负责。</p>
 *
 * <p>交互假设：授权发起与回调均为浏览器 front-channel GET 请求（参数走查询串）。
 * 仅支持 back-channel 或 POST 绑定（如 SAML Artifact / HTTP-POST binding）的协议
 * 需先演进本 SPI 的回调入口形态，当前契约无法直接承载。</p>
 *
 * <p>实现契约：</p>
 * <ul>
 *   <li>回调参数缺失、state/nonce 不符或换取身份失败时抛出
 *       {@code ErrorCode.EXTERNAL_CALLBACK_INVALID} 的
 *       {@link io.github.surezzzzzz.sdk.auth.iam.core.exception.IamProtocolException}；</li>
 *   <li>外部 IdP 不可达时抛出 {@code ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE}；</li>
 *   <li>认证成功必须返回 externalId 稳定唯一的 {@link ExternalIdentity}。</li>
 * </ul>
 *
 * @author surezzzzzz
 */
public interface ExternalBrowserLoginProvider {

    /**
     * 登录方式编码（全局唯一，两个适配器注册相同编码会导致装配失败）。
     *
     * @return 登录方式编码，如 enterprise-sso
     */
    String providerCode();

    /**
     * 构造外部 IdP 跳转地址。
     *
     * @param callbackUrl IAM 回调端点完整地址（由调用方生成并传入）
     * @param state       调用方生成的防重放 state，实现必须原样携带给外部 IdP
     * @return 外部 IdP 跳转地址
     */
    String buildAuthorizeUrl(String callbackUrl, String state);

    /**
     * 消费外部 IdP 回调参数并换取已校验的外部身份。
     *
     * @param callbackParams 回调查询参数（如 code、state、error）
     * @return 认证成功的外部身份
     */
    ExternalIdentity consumeCallback(Map<String, String> callbackParams);
}
