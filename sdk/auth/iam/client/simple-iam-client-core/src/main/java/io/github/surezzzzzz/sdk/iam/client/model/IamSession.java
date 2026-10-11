package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * 在线会话投影（sessions 族响应模型，1.3.7）。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamSession {

    /**
     * 会话标识（踢会话用）。
     */
    String sessionId;

    /**
     * 收件人公开主体（会话属主）。
     */
    String subjectId;

    /**
     * 用户名。
     */
    String username;

    /**
     * 客户端 ID。
     */
    String clientId;

    /**
     * 来源 IP。
     */
    String remoteIp;

    /**
     * User-Agent。
     */
    String userAgent;

    /**
     * 认证时间。
     */
    Instant authTime;

    /**
     * 最近活跃时间。
     */
    Instant lastActiveAt;
}
