package io.github.surezzzzzz.sdk.auth.aksk.core.model;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Token信息Model
 *
 * @author surezzzzzz
 */
@Data
public class TokenInfo {

    /**
     * 授权ID
     */
    private String id;

    /**
     * 注册客户端ID（UUID）
     */
    private String registeredClientId;

    /**
     * 客户端ID（AKSK）
     */
    private String clientId;

    /**
     * 客户端名称
     */
    private String clientName;

    /**
     * 客户端类型（1=平台级，2=用户级）
     */
    private Integer clientType;

    /**
     * Token值（完整显示，不脱敏）
     */
    private String tokenValue;

    /**
     * Token签发时间
     */
    private Instant issuedAt;

    /**
     * Token过期时间
     */
    private Instant expiresAt;

    /**
     * 权限范围
     */
    private List<String> scopes;

    /**
     * Token状态
     */
    private TokenStatus status;

    /**
     * 数据源
     */
    private DataSource dataSource;

    /**
     * 所属用户ID（用户级Token）
     */
    private String ownerUserId;

    /**
     * 所属用户名（用户级Token）
     */
    private String ownerUsername;

    /**
     * Token状态枚举
     */
    public enum TokenStatus {
        /**
         * 有效
         */
        ACTIVE,
        /**
         * 已过期
         */
        EXPIRED
    }

    /**
     * 数据源枚举
     */
    public enum DataSource {
        /**
         * 仅MySQL
         */
        MYSQL,
        /**
         * 仅Redis
         */
        REDIS,
        /**
         * MySQL和Redis都有
         */
        BOTH
    }
}
