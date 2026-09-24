package io.github.surezzzzzz.sdk.auth.aksk.server.core.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.constant.AkskOwnerAuthorizationSynchronizationMode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IAM 授权同步模式枚举的规范形态测试。
 */
class AkskOwnerAuthorizationSynchronizationModeTest {

    @Test
    void shouldResolveByCodeIgnoringCase() {
        assertEquals(AkskOwnerAuthorizationSynchronizationMode.EVENTUAL_WITH_LEASE,
                AkskOwnerAuthorizationSynchronizationMode.fromCode("EVENTUAL_WITH_LEASE"));
        assertEquals(AkskOwnerAuthorizationSynchronizationMode.STRICT_ONLINE,
                AkskOwnerAuthorizationSynchronizationMode.fromCode("strict_online"));
        assertNull(AkskOwnerAuthorizationSynchronizationMode.fromCode(null));
        assertNull(AkskOwnerAuthorizationSynchronizationMode.fromCode("offline"));
    }

    /**
     * code 与常量名同形是配置值稳定性的前提：yml 中按常量名配置的既有部署升级后语义不变。
     */
    @Test
    void codeMustStayIdenticalToConstantName() {
        for (AkskOwnerAuthorizationSynchronizationMode mode : AkskOwnerAuthorizationSynchronizationMode.values()) {
            assertEquals(mode.name(), mode.getCode(), "code 必须与常量名同形");
            assertEquals(mode.name(), mode.toString(), "toString 必须返回 code");
        }
        assertEquals(Arrays.asList("EVENTUAL_WITH_LEASE", "STRICT_ONLINE"),
                Arrays.asList(AkskOwnerAuthorizationSynchronizationMode.getAllCodes()));
        assertTrue(AkskOwnerAuthorizationSynchronizationMode.isValid("STRICT_ONLINE"));
        assertFalse(AkskOwnerAuthorizationSynchronizationMode.isValid("eventual"));
    }
}
