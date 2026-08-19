package io.github.surezzzzzz.sdk.auth.aksk.server.controller.response;

import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Token 管理响应。
 *
 * @author surezzzzzz
 */
@Data
public class TokenInfoResponse {

    /**
     * 授权标识。
     */
    private String id;

    /**
     * 注册 Client 标识。
     */
    private String registeredClientId;

    /**
     * Client 标识。
     */
    private String clientId;

    /**
     * Client 名称。
     */
    private String clientName;

    /**
     * Client 类型。
     */
    private Integer clientType;

    /**
     * 签发时间。
     */
    private Instant issuedAt;

    /**
     * 过期时间。
     */
    private Instant expiresAt;

    /**
     * OAuth Scope。
     */
    private List<String> scopes;

    /**
     * Token 状态。
     */
    private TokenInfo.TokenStatus status;

    /**
     * 数据来源。
     */
    private TokenInfo.DataSource dataSource;

    /**
     * 所属用户标识。
     */
    private String ownerUserId;

    /**
     * 所属用户名。
     */
    private String ownerUsername;
}
