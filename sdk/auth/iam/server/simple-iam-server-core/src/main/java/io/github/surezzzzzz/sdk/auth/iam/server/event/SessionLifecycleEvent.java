package io.github.surezzzzzz.sdk.auth.iam.server.event;

import lombok.Getter;

/**
 * IAM 会话生命周期事件（建立 / 撤销）。
 *
 * <p>批量吊销（{@code revokeAllByUserId}）发布单个事件并携带撤销数量，
 * {@code sessionId} 为空、以 {@code userId} 定位；单个会话撤销时
 * {@code revokedCount} 为 null。
 *
 * <p>会话撤销联动清理 OAuth2 授权由 Token 族的 REMOVED 事件覆盖，本事件只表达会话维度。
 *
 * @author surezzzzzz
 */
@Getter
public class SessionLifecycleEvent extends AbstractIamEvent {

    /**
     * 会话动作类型。
     */
    private final SessionEventType eventType;

    /**
     * 撤销来源；CREATED 事件为 {@link SessionEventCause#UNSPECIFIED}。
     */
    private final SessionEventCause cause;

    /**
     * IAM 会话 ID；批量吊销事件为 null。
     */
    private final String sessionId;

    private final Long userId;

    private final String username;

    /**
     * 批量吊销数量；单个会话事件为 null。
     */
    private final Integer revokedCount;

    public SessionLifecycleEvent(Object source, SessionEventType eventType, SessionEventCause cause,
                                 String sessionId, Long userId, String username, Integer revokedCount) {
        super(source);
        this.eventType = eventType;
        this.cause = cause == null ? SessionEventCause.UNSPECIFIED : cause;
        this.sessionId = sessionId;
        this.userId = userId;
        this.username = username;
        this.revokedCount = revokedCount;
    }
}
