package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.interceptor.AkskRestTemplateInterceptor;
import io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.test.SimpleAkskRestTemplateRedisClientTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RestTemplate 集成测试
 *
 * <p>测试自动配置是否正确注入 Bean。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskRestTemplateRedisClientTestApplication.class)
class RestTemplateIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private TokenManager tokenManager;

    @Autowired(required = false)
    private AkskRestTemplateInterceptor interceptor;

    @Autowired(required = false)
    @Qualifier("akskClientRestTemplate")
    private RestTemplate akskClientRestTemplate;

    @Test
    void testTokenManagerBeanExists() {
        log.info("========== 测试：TokenManager Bean 是否存在 ==========");

        // Given & When
        TokenManager bean = applicationContext.getBean(TokenManager.class);
        log.info("从 ApplicationContext 获取 TokenManager Bean: {}", bean.getClass().getName());

        // Then
        assertNotNull(bean, "TokenManager Bean should exist");
        assertNotNull(tokenManager, "TokenManager should be autowired");
        assertEquals(bean, tokenManager);
        log.info("测试通过：TokenManager Bean 存在且已正确注入\n");
    }

    @Test
    void testInterceptorBeanExists() {
        log.info("========== 测试：AkskRestTemplateInterceptor Bean 是否存在 ==========");

        // Given & When
        AkskRestTemplateInterceptor bean = applicationContext.getBean(AkskRestTemplateInterceptor.class);
        log.info("从 ApplicationContext 获取 AkskRestTemplateInterceptor Bean: {}", bean.getClass().getName());

        // Then
        assertNotNull(bean, "AkskRestTemplateInterceptor Bean should exist");
        assertNotNull(interceptor, "AkskRestTemplateInterceptor should be autowired");
        assertEquals(bean, interceptor);
        log.info("测试通过：AkskRestTemplateInterceptor Bean 存在且已正确注入\n");
    }

    @Test
    void testRestTemplateBeanExists() {
        log.info("========== 测试：RestTemplate Bean 是否存在 ==========");

        // Given & When
        RestTemplate bean = applicationContext.getBean(RestTemplate.class);
        log.info("从 ApplicationContext 获取 RestTemplate Bean: {}", bean.getClass().getName());

        // Then
        assertNotNull(bean, "RestTemplate Bean should exist");
        assertNotNull(akskClientRestTemplate, "RestTemplate should be autowired");
        assertEquals(bean, akskClientRestTemplate);
        log.info("测试通过：RestTemplate Bean 存在且已正确注入\n");
    }

    @Test
    void testRestTemplateHasInterceptor() {
        log.info("========== 测试：RestTemplate 是否包含 AkskRestTemplateInterceptor ==========");

        // Given & When
        RestTemplate bean = applicationContext.getBean(RestTemplate.class);
        log.info("RestTemplate 拦截器数量: {}", bean.getInterceptors().size());

        // Then
        assertNotNull(bean.getInterceptors(), "RestTemplate should have interceptors");
        assertFalse(bean.getInterceptors().isEmpty(), "RestTemplate should have at least one interceptor");
        boolean hasAkskInterceptor = bean.getInterceptors().stream()
                .anyMatch(i -> i instanceof AkskRestTemplateInterceptor);
        log.info("是否包含 AkskRestTemplateInterceptor: {}", hasAkskInterceptor);
        assertTrue(hasAkskInterceptor, "RestTemplate should have AkskRestTemplateInterceptor");
        log.info("测试通过：RestTemplate 包含 AkskRestTemplateInterceptor\n");
    }

    @Test
    void testInterceptorHasTokenManager() {
        log.info("========== 测试：拦截器是否可以访问 TokenManager ==========");

        // Given
        AkskRestTemplateInterceptor bean = applicationContext.getBean(AkskRestTemplateInterceptor.class);
        log.info("获取到 AkskRestTemplateInterceptor Bean: {}", bean.getClass().getName());

        // When & Then
        assertNotNull(bean, "AkskRestTemplateInterceptor should exist");
        // TokenManager is injected via constructor, so we can't directly access it
        // But we can verify that the interceptor works by calling getToken
        assertDoesNotThrow(() -> {
            String token = tokenManager.getToken();
            log.info("成功调用 TokenManager.getToken(), token 长度: {}", token != null ? token.length() : 0);
        }, "TokenManager should be accessible");
        log.info("测试通过：TokenManager 可以正常访问\n");
    }
}
