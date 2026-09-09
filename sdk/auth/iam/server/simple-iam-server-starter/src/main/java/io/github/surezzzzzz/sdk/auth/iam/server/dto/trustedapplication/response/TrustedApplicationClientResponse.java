package io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * 可信应用客户端响应
 *
 * <p>列表 / 详情都不回显 secret 明文；创建时由独立响应返回明文一次。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class TrustedApplicationClientResponse {

    /**
     * 客户端主键ID（SAS 内部 UUID）
     */
    private String id;

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 客户端名称
     */
    private String clientName;

    /**
     * 客户端类型：PUBLIC / CONFIDENTIAL
     */
    private String clientType;

    /**
     * 是否要求用户确认授权范围
     */
    private Boolean requireConsent;

    /**
     * 是否强制 PKCE
     */
    private Boolean requireProofKey;

    /**
     * 重定向URI列表
     */
    private List<String> redirectUris;

    /**
     * 授权范围
     */
    private List<String> scopes;

    /**
     * 授权类型
     */
    private List<String> grantTypes;

    /**
     * 客户端认证方法
     */
    private List<String> authenticationMethods;

    /**
     * 客户端ID签发时间
     */
    private Instant clientIdIssuedAt;

    /**
     * 是否存在 secret（不返回明文）
     */
    private Boolean secretPresent;
}
