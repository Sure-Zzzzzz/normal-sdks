package io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request;

import lombok.Data;

import java.util.List;

/**
 * 更新可信应用客户端请求
 *
 * <p>仅更新可维护字段：名称、redirect_uri、scope、consent 策略。
 * {@code clientId}、{@code clientType}、授权类型和认证方法均不可变更。
 *
 * @author surezzzzzz
 */
@Data
public class UpdateTrustedApplicationClientRequest {

    /**
     * 客户端名称
     */
    private String clientName;

    /**
     * 是否要求用户确认授权范围
     */
    private Boolean requireConsent;

    /**
     * 重定向URI列表
     */
    private List<String> redirectUris;

    /**
     * 授权范围
     */
    private List<String> scopes;
}
