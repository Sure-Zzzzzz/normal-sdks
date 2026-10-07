package io.github.surezzzzzz.sdk.kms.server.test.cases;

import io.github.surezzzzzz.sdk.kms.core.exception.KmsValidationException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.server.service.KmsRequestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 旧上下文保持无人员证明，只有可信解析器显式建立人员本人上下文。
 *
 * @author surezzzzzz
 */
@Slf4j
class KmsRequestContextTest {

    /**
     * 原构造器保留 API，不能因人员形态的标识自动提升身份类型。
     */
    @Test
    void shouldKeepLegacyConstructorUnverifiedAndRejectHumanOwnerOverride() {
        KmsPrincipal owner = new KmsPrincipal("iam:example", "iam:example", Collections.singleton("kms.read-public-key"));
        assertFalse(new KmsRequestContext(owner, "context-request-000000001").isVerifiedHumanSubject());
        assertTrue(KmsRequestContext.forVerifiedHuman(owner, "context-request-000000001").isVerifiedHumanSubject());
        KmsPrincipal delegated = new KmsPrincipal("iam:example", "iam:other", Collections.singleton("kms.read-public-key"));
        assertThrows(KmsValidationException.class, () -> KmsRequestContext.forVerifiedHuman(delegated, "context-request-000000001"));
        assertThrows(KmsValidationException.class, () -> KmsRequestContext.forVerifiedHuman(null, "context-request-000000001"));
    }
}
