package io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OIDC 授权暂存上下文：nonce 与该次授权使用的回调地址（令牌端点校验 redirect_uri 必须一致）
 *
 * @author surezzzzzz
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingAuthorization {

    private String nonce;

    private String callbackUrl;
}
