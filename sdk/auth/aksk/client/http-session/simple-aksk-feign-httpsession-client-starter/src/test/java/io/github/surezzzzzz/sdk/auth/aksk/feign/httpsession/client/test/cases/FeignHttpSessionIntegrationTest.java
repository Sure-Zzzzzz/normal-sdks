package io.github.surezzzzzz.sdk.auth.aksk.feign.httpsession.client.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.feign.httpsession.client.interceptor.AkskHttpSessionFeignRequestInterceptor;
import io.github.surezzzzzz.sdk.auth.aksk.feign.httpsession.client.test.SimpleAkskFeignHttpSessionClientTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Feign HttpSession Client Integration Test
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskFeignHttpSessionClientTestApplication.class)
class FeignHttpSessionIntegrationTest {

    @Autowired(required = false)
    private TokenManager tokenManager;

    @Autowired(required = false)
    private AkskHttpSessionFeignRequestInterceptor akskHttpSessionFeignRequestInterceptor;

    @Test
    void testTokenManagerBeanShouldExist() {
        assertNotNull(tokenManager, "TokenManager Bean should exist");
    }

    @Test
    void testInterceptorBeanShouldExist() {
        assertNotNull(akskHttpSessionFeignRequestInterceptor, "AkskHttpSessionFeignRequestInterceptor Bean should exist");
    }
}
