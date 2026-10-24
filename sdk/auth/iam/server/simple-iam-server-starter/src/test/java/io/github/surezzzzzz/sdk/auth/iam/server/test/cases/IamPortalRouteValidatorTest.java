package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.service.portal.IamPortalRouteValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Portal 入口路径与业务 origin 的统一边界测试。
 */
class IamPortalRouteValidatorTest {

    @Test
    void rejectsAmbiguousPathsAndTargetOrigins() {
        assertFalse(IamPortalRouteValidator.isValidRoutePrefix("/app/orders/"));
        assertFalse(IamPortalRouteValidator.isValidRoutePrefix("/app//orders"));
        assertTrue(IamPortalRouteValidator.isValidPublicEntry("/app/orders/"));
        assertFalse(IamPortalRouteValidator.isValidPublicEntry("/app/orders", "/app/other/index.html"));
        assertTrue(IamPortalRouteValidator.isValidPublicEntry("/app/orders", "/app/orders/index.html"));
        assertFalse(IamPortalRouteValidator.isValidPublicApiBase("/api//orders"));
        assertFalse(IamPortalRouteValidator.isValidTargetOrigin("http://[fe80::1%25eth0]"));
        assertFalse(IamPortalRouteValidator.isValidTargetOrigin("http://订单.internal"));
        assertTrue(IamPortalRouteValidator.isValidTargetOrigin("http://orders.internal:8080"));
    }
}
