package io.github.surezzzzzz.sdk.auth.aksk.server.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.Set;

/**
 * Token 生命周期事件抽象基类。
 *
 * <p>事件类型描述发生的生命周期动作，业务原因描述动作的触发来源，二者保持正交，
 * 以保证既有按事件类型消费的实现无需因新增原因而改变分支。
 *
 * @author surezzzzzz
 */
@Getter
public abstract class AbstractTokenEvent extends ApplicationEvent {

    /**
     * 稳定的生命周期动作类型。
     */
    private final TokenEventType eventType;

    /**
     * 触发事件的业务原因；缺失时归一化为 {@link TokenEventCause#UNSPECIFIED}，兼容旧构造器。
     */
    private final TokenEventCause cause;

    /**
     * 事件对象创建时间，不等同于 Token 的签发时间。
     */
    private final Instant eventTime;

    // ==================== 客户端信息 ====================

    private final String clientId;
    private final String clientType;
    private final String userId;
    private final String username;

    // ==================== Token 信息 ====================

    /**
     * 仅供 Server 生命周期事件内部使用的 Token 原文；下游审计处理器不得接收、记录或重建该值。
     */
    private final String tokenValue;
    private final Set<String> scopes;

    /**
     * Token 的签发时间，与 {@link #eventTime} 的事件创建时间独立。
     */
    private final Instant issuedAt;

    /**
     * Token 的预定过期时间，不表示事件发生时的即时有效性结论。
     */
    private final Instant expiresAt;

    /**
     * 保持旧事件子类的构造方式，未携带业务原因时统一标识为未指定。
     */
    protected AbstractTokenEvent(Object source, TokenEventType eventType,
                                 String clientId, String clientType,
                                 String userId, String username,
                                 String tokenValue, Set<String> scopes,
                                 Instant issuedAt, Instant expiresAt) {
        this(source, eventType, TokenEventCause.UNSPECIFIED,
                clientId, clientType, userId, username,
                tokenValue, scopes, issuedAt, expiresAt);
    }

    /**
     * 创建携带明确业务原因的生命周期事件。
     *
     * <p>允许 {@code cause} 为空以兼容上游扩展，空值会归一化为未指定原因而不会向消费者暴露空语义。
     */
    protected AbstractTokenEvent(Object source, TokenEventType eventType, TokenEventCause cause,
                                 String clientId, String clientType,
                                 String userId, String username,
                                 String tokenValue, Set<String> scopes,
                                 Instant issuedAt, Instant expiresAt) {
        super(source);
        this.eventType = eventType;
        this.cause = cause == null ? TokenEventCause.UNSPECIFIED : cause;
        this.eventTime = Instant.now();
        this.clientId = clientId;
        this.clientType = clientType;
        this.userId = userId;
        this.username = username;
        this.tokenValue = tokenValue;
        this.scopes = scopes;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }
}
