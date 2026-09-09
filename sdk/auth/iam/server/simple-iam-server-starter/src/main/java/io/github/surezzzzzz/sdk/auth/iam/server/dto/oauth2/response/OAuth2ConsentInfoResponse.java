package io.github.surezzzzzz.sdk.auth.iam.server.dto.oauth2.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * OAuth2 Consent 页面所需数据
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class OAuth2ConsentInfoResponse {

    /**
     * 注册客户端 ID
     */
    private String registeredClientId;

    /**
     * 客户端 ID
     */
    private String clientId;

    /**
     * 客户端展示名称
     */
    private String clientName;

    /**
     * SAS 内部 state（表单回传用）
     */
    private String state;

    /**
     * 本次请求的 scope 列表
     */
    private List<String> requestedScopes;

    /**
     * 用户已批准的 scope 列表（上次授权遗留）
     */
    private List<String> previouslyApprovedScopes;
}
