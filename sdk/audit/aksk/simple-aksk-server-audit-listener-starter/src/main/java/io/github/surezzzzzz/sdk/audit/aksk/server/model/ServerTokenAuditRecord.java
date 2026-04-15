package io.github.surezzzzzz.sdk.audit.aksk.server.model;

import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * Server Token 审计记录
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerTokenAuditRecord {
    /**
     * 事件类型：ISSUED / REVOKED / REMOVED / INTROSPECTED
     */
    private TokenEventType eventType;
    /**
     * 事件发生时间
     */
    private Instant eventTime;
    /**
     * 客户端 ID
     */
    private String clientId;
    /**
     * 客户端类型：platform / user
     */
    private String clientType;
    /**
     * 用户 ID（用户级才有）
     */
    private String userId;
    /**
     * 用户名（用户级才有）
     */
    private String username;
    /**
     * 授权范围
     */
    private Set<String> scopes;
    /**
     * Token 值
     */
    private String tokenValue;
    /**
     * Token 颁发时间
     */
    private Instant issuedAt;
    /**
     * Token 过期时间
     */
    private Instant expiresAt;
    /**
     * token 是否有效（仅 INTROSPECTED 事件有效）
     */
    private Boolean active;
}
