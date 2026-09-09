package io.github.surezzzzzz.sdk.auth.iam.server.event;

import java.time.Instant;

/**
 * IAM Refresh Token 复用检测命中事件（安全信号：已被轮换的旧 token 再次使用，
 * 触发整族撤销）。
 *
 * <p>事件发布先于复用异常抛出；userId / username / clientId 来自被撤销的族记录，
 * 定位不到时可能为空。tokenValue 不携带（复用 token 原文即攻击者输入，不进入事件）。
 *
 * @author surezzzzzz
 */
public class RefreshTokenReuseDetectedEvent extends AbstractTokenEvent {

    /**
     * 被整族撤销的 Refresh Token 族 ID。
     */
    private final String familyId;

    public RefreshTokenReuseDetectedEvent(Object source, String familyId,
                                          String clientId, String userId, String username,
                                          Instant issuedAt, Instant expiresAt) {
        super(source, TokenEventType.REUSE_DETECTED, TokenEventCause.REFRESH_TOKEN_REUSE,
                clientId, null, userId, username,
                null, null, issuedAt, expiresAt);
        this.familyId = familyId;
    }

    /**
     * 被整族撤销的 Refresh Token 族 ID
     */
    public String getFamilyId() {
        return familyId;
    }
}
