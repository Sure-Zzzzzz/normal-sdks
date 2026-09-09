package io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request;

import lombok.Data;

import java.util.List;

/**
 * 创建可信应用客户端请求
 *
 * <p>在指定应用下创建 OAuth2 客户端。客户端类型、PKCE、secret、grantType、redirectUri 等策略
 * 由 {@code TrustedApplicationClientService} 统一校验。
 *
 * @author surezzzzzz
 */
@Data
public class CreateTrustedApplicationClientRequest {

    /**
     * 客户端ID（业务系统标识），应用下唯一
     */
    private String clientId;

    /**
     * 客户端名称（展示名）
     */
    private String clientName;

    /**
     * 客户端类型：PUBLIC / CONFIDENTIAL，未传时按历史兼容规则视为 CONFIDENTIAL
     */
    private String clientType;

    /**
     * 客户端密钥明文：仅机密客户端可传，留空由服务端生成；服务端加密存储，响应中返回一次
     */
    private String clientSecret;

    /**
     * 是否要求用户确认授权范围
     */
    private Boolean requireConsent;

    /**
     * 重定向URI列表：授权码模式必须至少一个，必须是绝对URI且不含通配符
     */
    private List<String> redirectUris;

    /**
     * 授权范围，如 openid / profile / read
     */
    private List<String> scopes;

    /**
     * 授权类型：仅允许 authorization_code，机器凭证请走 aksk-server
     */
    private List<String> grantTypes;

    /**
     * 客户端认证方法，如 client_secret_basic
     */
    private List<String> authenticationMethods;
}
