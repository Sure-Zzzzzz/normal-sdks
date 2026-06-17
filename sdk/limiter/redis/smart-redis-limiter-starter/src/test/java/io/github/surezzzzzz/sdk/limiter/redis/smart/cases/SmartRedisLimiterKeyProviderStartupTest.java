package io.github.surezzzzzz.sdk.limiter.redis.smart.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterMode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.generator.SmartRedisLimiterKeyProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor.SmartRedisLimiterInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 1.1.4 KeyProvider 启动校验测试
 * <p>验证：拦截器 @PostConstruct 中遍历 rules，发现 keyProvider 引用的 Bean 不存在 / 类型不匹配 时立即抛错。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterKeyProviderStartupTest {

    @Test
    public void testInitFailsWhenKeyProviderBeanMissing() {
        SmartRedisLimiterInterceptor interceptor = new SmartRedisLimiterInterceptor();

        SmartRedisLimiterProperties properties = new SmartRedisLimiterProperties();
        properties.setEnable(true);
        properties.setMe("startup-test");
        properties.setMode(SmartRedisLimiterMode.INTERCEPTOR.getCode());

        SmartRedisLimiterProperties.SmartInterceptorRule rule =
                new SmartRedisLimiterProperties.SmartInterceptorRule();
        rule.setPathPattern("/api/missing-bean");
        rule.setMethod("GET");
        rule.setKeyProvider("nonExistentKeyProvider_FOR_TEST");
        properties.getInterceptor().setRules(Collections.singletonList(rule));

        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBean(eq("nonExistentKeyProvider_FOR_TEST"), eq(SmartRedisLimiterKeyProvider.class)))
                .thenThrow(new NoSuchBeanDefinitionException("nonExistentKeyProvider_FOR_TEST"));

        ReflectionTestUtils.setField(interceptor, "properties", properties);
        ReflectionTestUtils.setField(interceptor, "applicationContext", ctx);

        IllegalStateException ex = assertThrows(IllegalStateException.class, interceptor::init);
        assertTrue(ex.getMessage().contains("nonExistentKeyProvider_FOR_TEST"),
                "异常信息应包含 keyProviderName，实际: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("/api/missing-bean"),
                "异常信息应包含 rule.pathPattern，实际: " + ex.getMessage());
    }

    @Test
    public void testInitSucceedsWhenKeyProviderBeanPresent() {
        SmartRedisLimiterInterceptor interceptor = new SmartRedisLimiterInterceptor();

        SmartRedisLimiterProperties properties = new SmartRedisLimiterProperties();
        properties.setEnable(true);
        properties.setMe("startup-test");
        properties.setMode(SmartRedisLimiterMode.INTERCEPTOR.getCode());

        SmartRedisLimiterProperties.SmartInterceptorRule rule =
                new SmartRedisLimiterProperties.SmartInterceptorRule();
        rule.setPathPattern("/api/ok");
        rule.setMethod("GET");
        rule.setKeyProvider("validKeyProvider");
        properties.getInterceptor().setRules(Collections.singletonList(rule));

        SmartRedisLimiterKeyProvider provider = (req, c) -> "ok";
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBean(eq("validKeyProvider"), eq(SmartRedisLimiterKeyProvider.class)))
                .thenReturn(provider);

        ReflectionTestUtils.setField(interceptor, "properties", properties);
        ReflectionTestUtils.setField(interceptor, "applicationContext", ctx);

        interceptor.init();

        @SuppressWarnings("unchecked")
        java.util.Map<String, SmartRedisLimiterKeyProvider> cache =
                (java.util.Map<String, SmartRedisLimiterKeyProvider>)
                        ReflectionTestUtils.getField(interceptor, "keyProviderCache");
        assertEquals(1, cache.size(), "校验通过的 provider 应缓存一项");
        assertEquals(provider, cache.get("validKeyProvider"));
    }

    @Test
    public void testInitSkipsValidationWhenInterceptorDisabled() {
        // mode=annotation 时拦截器不参与限流，不应触发 keyProvider 校验
        SmartRedisLimiterInterceptor interceptor = new SmartRedisLimiterInterceptor();

        SmartRedisLimiterProperties properties = new SmartRedisLimiterProperties();
        properties.setEnable(true);
        properties.setMe("startup-test");
        properties.setMode(SmartRedisLimiterMode.ANNOTATION.getCode());

        SmartRedisLimiterProperties.SmartInterceptorRule rule =
                new SmartRedisLimiterProperties.SmartInterceptorRule();
        rule.setPathPattern("/api/ignored");
        rule.setMethod("GET");
        rule.setKeyProvider("nonExistent_should_be_ignored");
        properties.getInterceptor().setRules(Collections.singletonList(rule));

        ApplicationContext ctx = mock(ApplicationContext.class);
        // 不打 stub —— mode=annotation 下不应调用 ctx.getBean

        ReflectionTestUtils.setField(interceptor, "properties", properties);
        ReflectionTestUtils.setField(interceptor, "applicationContext", ctx);

        interceptor.init(); // 不抛异常即通过
    }

    @Test
    public void testInitDeduplicatesSharedKeyProvider() {
        // 多条 rule 引用同一 keyProviderName，缓存里只保留一项
        SmartRedisLimiterInterceptor interceptor = new SmartRedisLimiterInterceptor();

        SmartRedisLimiterProperties properties = new SmartRedisLimiterProperties();
        properties.setEnable(true);
        properties.setMe("startup-test");
        properties.setMode(SmartRedisLimiterMode.INTERCEPTOR.getCode());

        SmartRedisLimiterProperties.SmartInterceptorRule r1 =
                new SmartRedisLimiterProperties.SmartInterceptorRule();
        r1.setPathPattern("/a");
        r1.setKeyProvider("sharedProvider");

        SmartRedisLimiterProperties.SmartInterceptorRule r2 =
                new SmartRedisLimiterProperties.SmartInterceptorRule();
        r2.setPathPattern("/b");
        r2.setKeyProvider("sharedProvider");

        properties.getInterceptor().setRules(java.util.Arrays.asList(r1, r2));

        SmartRedisLimiterKeyProvider provider = (req, c) -> "x";
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBean(eq("sharedProvider"), eq(SmartRedisLimiterKeyProvider.class)))
                .thenReturn(provider);

        ReflectionTestUtils.setField(interceptor, "properties", properties);
        ReflectionTestUtils.setField(interceptor, "applicationContext", ctx);

        interceptor.init();

        @SuppressWarnings("unchecked")
        java.util.Map<String, SmartRedisLimiterKeyProvider> cache =
                (java.util.Map<String, SmartRedisLimiterKeyProvider>)
                        ReflectionTestUtils.getField(interceptor, "keyProviderCache");
        assertEquals(1, cache.size(), "同名 provider 应去重，缓存仅保留一项");
    }
}
