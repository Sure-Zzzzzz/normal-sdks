package io.github.surezzzzzz.sdk.auth.aksk.server.event;

import java.time.Instant;
import java.util.Set;

/**
 * Token 撤销事件。
 *
 * <p>{@link TokenEventCause} 标识撤销来源。
 *
 * @author surezzzzzz
 */
public class TokenRevokedEvent extends AbstractTokenEvent {

    /**
     * 以未指定原因创建撤销事件，保持既有发布方与消费者的兼容。
     */
    public TokenRevokedEvent(Object source,
                             String clientId, String clientType,
                             String userId, String username,
                             String tokenValue, Set<String> scopes,
                             Instant issuedAt, Instant expiresAt) {
        this(source, TokenEventCause.UNSPECIFIED,
                clientId, clientType, userId, username,
                tokenValue, scopes, issuedAt, expiresAt);
    }

    /**
     * 创建带业务撤销来源的事件。
     *
     * <p>事件类型始终为 {@link TokenEventType#REVOKED}；来源仅由 {@code cause} 表达，
     * 不新增事件类型以避免破坏既有统计与分发逻辑。
     */
    public TokenRevokedEvent(Object source, TokenEventCause cause,
                             String clientId, String clientType,
                             String userId, String username,
                             String tokenValue, Set<String> scopes,
                             Instant issuedAt, Instant expiresAt) {
        super(source, TokenEventType.REVOKED, cause,
                clientId, clientType, userId, username,
                tokenValue, scopes, issuedAt, expiresAt);
    }
}
