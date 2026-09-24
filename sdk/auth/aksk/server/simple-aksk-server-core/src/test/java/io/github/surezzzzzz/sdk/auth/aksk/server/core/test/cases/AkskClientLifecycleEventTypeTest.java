package io.github.surezzzzzz.sdk.auth.aksk.server.event;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AKU 生命周期动作枚举的规范形态测试。
 */
class AkskClientLifecycleEventTypeTest {

    @Test
    void shouldResolveByCodeIgnoringCase() {
        assertEquals(AkskClientLifecycleEventType.SECRET_ROTATED, AkskClientLifecycleEventType.fromCode("SECRET_ROTATED"));
        assertEquals(AkskClientLifecycleEventType.TERMINATED, AkskClientLifecycleEventType.fromCode("terminated"));
        assertNull(AkskClientLifecycleEventType.fromCode(null));
        assertNull(AkskClientLifecycleEventType.fromCode("unknown"));
    }

    /**
     * code 与常量名同形是审计稳定性的前提：审计记录里的 eventType 值依赖该一致性。
     */
    @Test
    void codeMustStayIdenticalToConstantName() {
        for (AkskClientLifecycleEventType type : AkskClientLifecycleEventType.values()) {
            assertEquals(type.name(), type.getCode(), "code 必须与常量名同形");
            assertEquals(type.name(), type.toString(), "toString 必须返回 code");
        }
        assertEquals(Arrays.asList("CREATED", "RENAMED", "SECRET_ROTATED", "TERMINATED"),
                Arrays.asList(AkskClientLifecycleEventType.getAllCodes()));
        assertTrue(AkskClientLifecycleEventType.isValid("CREATED"));
        assertFalse(AkskClientLifecycleEventType.isValid("create"));
    }
}
