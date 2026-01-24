package io.github.surezzzzzz.sdk.auth.aksk.core.model;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Client信息领域模型
 * <p>
 * 用于表示OAuth2客户端（AKSK）的基本信息，包括客户端ID、密钥、类型、所属用户等。
 * 支持平台级和用户级两种客户端类型。
 *
 * @author surezzzzzz
 */
@Data
public class ClientInfo {

    /**
     * 客户端ID（AccessKey）
     */
    private String clientId;

    /**
     * 客户端密钥（SecretKey）
     */
    private String clientSecret;

    /**
     * 客户端名称
     */
    private String clientName;

    /**
     * 客户端类型（1=平台级，2=用户级）
     */
    private Integer clientType;

    /**
     * 所属用户ID（用户级客户端必填，平台级客户端为null）
     */
    private String ownerUserId;

    /**
     * 所属用户名（用户级客户端可选）
     */
    private String ownerUsername;

    /**
     * 权限范围列表（如：["read", "write"]）
     */
    private List<String> scopes;

    /**
     * 是否启用（true=启用，false=禁用）
     */
    private boolean enabled;

    /**
     * 客户端ID签发时间
     */
    private Instant clientIdIssuedAt;
}
