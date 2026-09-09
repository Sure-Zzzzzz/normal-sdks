package io.github.surezzzzzz.sdk.auth.iam.server.dto.dashboard.response;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamSessionEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 管理台活跃会话响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class AdminSessionResponse {

    private String sessionId;

    private Long userId;

    private String username;

    private String clientId;

    private String remoteIp;

    private String userAgent;

    private Instant authTime;

    private Instant lastActiveAt;

    private Instant expiresAt;

    /**
     * 会话实体转响应视图
     */
    public static AdminSessionResponse from(IamSessionEntity session) {
        return new AdminSessionResponse(
                session.getId(),
                session.getUserId(),
                session.getUsername(),
                session.getClientId(),
                session.getRemoteIp(),
                session.getUserAgent(),
                session.getAuthTime(),
                session.getLastActiveAt(),
                session.getExpiresAt()
        );
    }
}
