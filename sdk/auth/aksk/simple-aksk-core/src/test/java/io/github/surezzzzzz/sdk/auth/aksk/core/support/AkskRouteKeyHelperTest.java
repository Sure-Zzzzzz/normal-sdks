package io.github.surezzzzzz.sdk.auth.aksk.core.support;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.AkskConstant;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.JwtClaimConstant;
import io.github.surezzzzzz.sdk.auth.aksk.core.exception.AkskException;
import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AkskRouteKeyHelperTest {

    @Test
    void shouldRoundTripValidRouteKey() {
        String keyId = "key_01.release-test";
        String routeKey = AkskRouteKeyHelper.createRouteKey(keyId);

        assertEquals("aksk/key_01.release-test", routeKey);
        assertEquals(keyId, AkskRouteKeyHelper.extractKeyId(routeKey));
        assertTrue(AkskRouteKeyHelper.isAkskRouteKey(routeKey));
    }

    @Test
    void shouldRejectInvalidKeyIdWhenCreatingRouteKey() {
        List<String> invalidKeyIds = Arrays.asList(
                null,
                "",
                " leading",
                "trailing ",
                "key/id",
                "key*id",
                "a".repeat(AkskConstant.MAX_ROUTE_KEY_ID_CODE_POINT_COUNT + 1));

        invalidKeyIds.forEach(keyId ->
                assertThrows(AkskException.class, () -> AkskRouteKeyHelper.createRouteKey(keyId)));
    }

    @Test
    void shouldRejectNonAkskOrMalformedRouteKeyWhenExtracting() {
        List<String> invalidRouteKeys = Arrays.asList(
                null,
                "",
                "iam/key-01",
                "aksk/",
                "aksk/ leading",
                "aksk/trailing ",
                "aksk/key/id",
                "aksk/key*id");

        invalidRouteKeys.forEach(routeKey -> {
            assertNull(AkskRouteKeyHelper.extractKeyId(routeKey));
            assertFalse(AkskRouteKeyHelper.isAkskRouteKey(routeKey));
        });
    }

    @Test
    void shouldKeepPublicAuthorizationContractValues() {
        assertEquals("aksk_authorization", JwtClaimConstant.APPLICATION_AUTHORIZATION);
        assertEquals(Arrays.asList(
                        TokenInfo.DataSource.MYSQL,
                        TokenInfo.DataSource.REDIS,
                        TokenInfo.DataSource.BOTH),
                Arrays.asList(TokenInfo.DataSource.values()));
    }
}
