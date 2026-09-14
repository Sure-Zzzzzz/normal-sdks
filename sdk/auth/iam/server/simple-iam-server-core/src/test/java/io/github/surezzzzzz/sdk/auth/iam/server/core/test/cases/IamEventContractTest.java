package io.github.surezzzzzz.sdk.auth.iam.server.core.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.*;
import io.github.surezzzzzz.sdk.auth.iam.server.event.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IAM Server Core 契约测试（事件字段、安全边界、Portal 菜单常量）。
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

    @Test
    void shouldExposePortalMenuTreeContractAndFolderIcon() {
        assertTrue(TrustedApplicationIcon.isSupported("folder"));
        assertFalse(TrustedApplicationIcon.isSupported("folder-unknown"));
        assertEquals("TRUSTED_APPLICATION_014", ErrorCode.TRUSTED_APPLICATION_MENU_TREE_INVALID);
        assertEquals("TRUSTED_APPLICATION_015", ErrorCode.TRUSTED_APPLICATION_MENU_TREE_LEGACY_CONFLICT);
        assertEquals("TRUSTED_APPLICATION_016", ErrorCode.TRUSTED_APPLICATION_MENU_PERMISSION_REFERENCED);
        assertTrue(ServerErrorMessage.TRUSTED_APPLICATION_MENU_TREE_LEGACY_CONFLICT.contains("menuTree"));
        assertTrue(ServerErrorMessage.TRUSTED_APPLICATION_MENU_PERMISSION_REFERENCED.contains("页面权限"));
    }

    @Test
    void shouldExposePortalDefaultEntryErrorContract() {
        assertEquals("TRUSTED_APPLICATION_017", ErrorCode.TRUSTED_APPLICATION_PORTAL_DEFAULT_ENTRY_INVALID);
        assertEquals("TRUSTED_APPLICATION_018", ErrorCode.TRUSTED_APPLICATION_PORTAL_CONFIGURATION_CONFLICT);
        assertEquals("TRUSTED_APPLICATION_019", ErrorCode.TRUSTED_APPLICATION_PORTAL_LOGIN_LANDING_INVALID);
        assertTrue(ServerErrorMessage.TRUSTED_APPLICATION_PORTAL_DEFAULT_ENTRY_INVALID.contains("默认入口"));
        assertTrue(ServerErrorMessage.TRUSTED_APPLICATION_PORTAL_CONFIGURATION_CONFLICT.contains("刷新后重试"));
        assertTrue(ServerErrorMessage.TRUSTED_APPLICATION_PORTAL_LOGIN_LANDING_INVALID.contains("登录首页"));
    }

    @Test
    void shouldExposePortalApplicationOrderErrorContract() {
        assertEquals("TRUSTED_APPLICATION_020", ErrorCode.TRUSTED_APPLICATION_PORTAL_APPLICATION_ORDER_INVALID);
        assertEquals("TRUSTED_APPLICATION_021", ErrorCode.TRUSTED_APPLICATION_PORTAL_APPLICATION_ORDER_CONFLICT);
        assertTrue(ServerErrorMessage.TRUSTED_APPLICATION_PORTAL_APPLICATION_ORDER_INVALID.contains("应用顺序"));
        assertTrue(ServerErrorMessage.TRUSTED_APPLICATION_PORTAL_APPLICATION_ORDER_CONFLICT.contains("刷新后重试"));
    }

    @Test
    void shouldExposePortalMenuPresentationEnums() {
        assertArrayEquals(new PortalMenuNodeType[]{PortalMenuNodeType.GROUP, PortalMenuNodeType.PAGE},
                PortalMenuNodeType.values());
        assertArrayEquals(new PortalPresentationMode[]{PortalPresentationMode.STANDARD, PortalPresentationMode.IMMERSIVE},
                PortalPresentationMode.values());
    }
}
