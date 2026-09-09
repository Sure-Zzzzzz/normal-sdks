package io.github.surezzzzzz.sdk.auth.iam.server.core.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.event.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IAM 事件族契约测试（构造字段保持 + 安全边界）。
 *
 * @author surezzzzzz
 */
class IamEventContractTest {

    @Test
    void shouldRetainAdminActionFields() {
        AdminActionEvent event = new AdminActionEvent(this,
                AdminActionType.SECRET_ROTATED, AdminSubjectType.VERIFICATION_CLIENT,
                "vc-001", "vc-001", "admin", "applicationId=1");

        assertEquals(AdminActionType.SECRET_ROTATED, event.getAction());
        assertEquals(AdminSubjectType.VERIFICATION_CLIENT, event.getSubjectType());
        assertEquals("vc-001", event.getSubjectId());
        assertEquals("admin", event.getOperator());
        assertEquals("applicationId=1", event.getDetail());
        assertNotNull(event.getEventTime());
    }

    @Test
    void shouldDefaultUnspecifiedCauseAndCarryBatchRevokedCount() {
        SessionLifecycleEvent event = new SessionLifecycleEvent(this,
                SessionEventType.REVOKED, null,
                null, 7L, "batch-user", 3);

        assertEquals(SessionEventType.REVOKED, event.getEventType());
        assertEquals(SessionEventCause.UNSPECIFIED, event.getCause());
        assertNull(event.getSessionId(), "批量吊销事件 sessionId 必须为空");
        assertEquals(Integer.valueOf(3), event.getRevokedCount());
    }

    @Test
    void shouldNotCarryTokenValueOnReuseDetection() {
        RefreshTokenReuseDetectedEvent event = new RefreshTokenReuseDetectedEvent(this,
                "family-1", "client-1", "7", "reuse-user",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T01:00:00Z"));

        assertEquals(TokenEventType.REUSE_DETECTED, event.getEventType());
        assertEquals(TokenEventCause.REFRESH_TOKEN_REUSE, event.getCause());
        assertEquals("family-1", event.getFamilyId());
        assertNull(event.getTokenValue(), "复用检测事件不得携带攻击者输入的 token 原文");
        assertEquals("reuse-user", event.getUsername());
    }
}
