package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.httpsession.client.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.httpsession.client.interceptor.HttpSessionRestTemplateInterceptor;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.httpsession.client.test.RestTemplateTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * RestTemplate HttpSession Client Integration Test
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = RestTemplateTestApplication.class)
class RestTemplateIntegrationTest {

    @Autowired(required = false)
    private TokenManager tokenManager;

    @Autowired(required = false)
    private HttpSessionRestTemplateInterceptor akskHttpSessionRestTemplateInterceptor;

    @Test
    void testTokenManagerBeanShouldExist() {
        assertNotNull(tokenManager, "TokenManager Bean should exist");
    }

    @Test
    void testInterceptorBeanShouldExist() {
        assertNotNull(akskHttpSessionRestTemplateInterceptor, "AkskHttpSessionRestTemplateInterceptor Bean should exist");
    }
}
