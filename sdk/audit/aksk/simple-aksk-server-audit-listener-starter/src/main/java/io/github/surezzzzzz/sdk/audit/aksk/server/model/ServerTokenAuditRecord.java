package io.github.surezzzzzz.sdk.audit.aksk.server.model;

import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenEventCause;
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
     * 稳定的生命周期动作类型；业务来源由 {@link #cause} 独立表达。
     */
    private TokenEventType eventType;
    /**
     * 触发该动作的业务来源；旧事件会归一化为未指定原因。
     */
    private TokenEventCause cause;
    /**
     * 事件对象创建时间，不等同于 Token 的签发或过期时间。
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
     * 历史兼容字段；3.0 起监听器始终传递 {@code null}。处理器不得依赖、记录或重新构造 Token 原文。
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
     * 本次自省请求的即时有效性结论；仅 {@code INTROSPECTED} 事件有值，其他类型保持 {@code null}。
     */
    private Boolean active;
}
