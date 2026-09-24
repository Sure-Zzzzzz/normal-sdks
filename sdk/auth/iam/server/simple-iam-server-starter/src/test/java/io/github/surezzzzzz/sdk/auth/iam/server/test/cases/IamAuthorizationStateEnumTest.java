package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamAkskAuthorizationChangeType;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.TrustedApplicationCleanupOperationState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 授权变更流与清理操作状态枚举的规范形态测试。
 */
class IamAuthorizationStateEnumTest {

    @Test
    void changeTypeShouldResolveByCodeIgnoringCase() {
        assertEquals(IamAkskAuthorizationChangeType.OWNER_STATE,
                IamAkskAuthorizationChangeType.fromCode("OWNER_STATE"));
        assertEquals(IamAkskAuthorizationChangeType.TARGET_APPLICATION_STATE,
                IamAkskAuthorizationChangeType.fromCode("target_application_state"));
        assertNull(IamAkskAuthorizationChangeType.fromCode(null));
        assertNull(IamAkskAuthorizationChangeType.fromCode("unknown"));
        assertTrue(IamAkskAuthorizationChangeType.isValid("OWNER_APPLICATION_PROJECTION"));
        assertFalse(IamAkskAuthorizationChangeType.isValid("owner"));
    }

    /**
     * code 与常量名同形是变更流与持久化稳定性的前提：journal 载荷与数据库列依赖该一致性。
     */
    @Test
    void changeTypeCodeMustStayIdenticalToConstantName() {
        for (IamAkskAuthorizationChangeType type : IamAkskAuthorizationChangeType.values()) {
            assertEquals(type.name(), type.getCode(), "code 必须与常量名同形");
            assertEquals(type.name(), type.toString(), "toString 必须返回 code");
        }
        assertEquals(Arrays.asList("OWNER_STATE", "TARGET_APPLICATION_STATE", "OWNER_APPLICATION_PROJECTION"),
                Arrays.asList(IamAkskAuthorizationChangeType.getAllCodes()));
    }

    @Test
    void cleanupStateShouldResolveByCodeIgnoringCase() {
        assertEquals(TrustedApplicationCleanupOperationState.PENDING,
                TrustedApplicationCleanupOperationState.fromCode("PENDING"));
        assertEquals(TrustedApplicationCleanupOperationState.RETRYING,
                TrustedApplicationCleanupOperationState.fromCode("retrying"));
        assertNull(TrustedApplicationCleanupOperationState.fromCode(null));
        assertNull(TrustedApplicationCleanupOperationState.fromCode("CANCELLED"));
        assertTrue(TrustedApplicationCleanupOperationState.isValid("FAILED"));
        assertFalse(TrustedApplicationCleanupOperationState.isValid("pending-wait"));
    }

    /**
     * code 与常量名同形是清理操作持久化稳定性的前提：操作表状态列依赖该一致性。
     */
    @Test
    void cleanupStateCodeMustStayIdenticalToConstantName() {
        for (TrustedApplicationCleanupOperationState state : TrustedApplicationCleanupOperationState.values()) {
            assertEquals(state.name(), state.getCode(), "code 必须与常量名同形");
            assertEquals(state.name(), state.toString(), "toString 必须返回 code");
        }
        assertEquals(Arrays.asList("PENDING", "RUNNING", "RETRYING", "COMPLETED", "FAILED"),
                Arrays.asList(TrustedApplicationCleanupOperationState.getAllCodes()));
    }
}
