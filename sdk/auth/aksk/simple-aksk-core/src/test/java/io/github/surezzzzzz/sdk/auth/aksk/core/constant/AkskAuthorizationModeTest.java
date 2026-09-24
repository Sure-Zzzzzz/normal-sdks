package io.github.surezzzzzz.sdk.auth.aksk.core.constant;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 授权模式枚举的协议形态与规范方法测试。
 */
class AkskAuthorizationModeTest {

    @Test
    void shouldResolveByCodeIgnoringCase() {
        assertEquals(AkskAuthorizationMode.STATIC_LEGACY, AkskAuthorizationMode.fromCode("STATIC_LEGACY"));
        assertEquals(AkskAuthorizationMode.OWNER_INHERITED, AkskAuthorizationMode.fromCode("owner_inherited"));
        assertNull(AkskAuthorizationMode.fromCode(null));
        assertNull(AkskAuthorizationMode.fromCode("unknown-mode"));
    }

    @Test
    void shouldValidateAndGetAllCodes() {
        assertTrue(AkskAuthorizationMode.isValid("STATIC_LEGACY"));
        assertTrue(AkskAuthorizationMode.isValid("OWNER_INHERITED"));
        assertFalse(AkskAuthorizationMode.isValid("legacy"));
        assertEquals(Arrays.asList("STATIC_LEGACY", "OWNER_INHERITED"),
                Arrays.asList(AkskAuthorizationMode.getAllCodes()));
    }

    /**
     * code 与常量名同形是协议稳定性的前提：令牌 Claim 与消费方 name() 比较依赖该一致性。
     */
    @Test
    void codeMustStayIdenticalToConstantName() {
        for (AkskAuthorizationMode mode : AkskAuthorizationMode.values()) {
            assertEquals(mode.name(), mode.getCode(), "code 必须与常量名同形");
            assertEquals(mode.name(), mode.toString(), "toString 必须返回 code");
        }
    }

    @Test
    void shouldCarryChineseDescription() {
        assertEquals("本地静态授权", AkskAuthorizationMode.STATIC_LEGACY.getDescription());
        assertEquals("IAM 所属人授权投影", AkskAuthorizationMode.OWNER_INHERITED.getDescription());
    }
}
