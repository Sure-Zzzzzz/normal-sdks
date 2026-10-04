package io.github.surezzzzzz.sdk.kms.feign.client.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.configuration.AkskFeignConfiguration;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.configuration.SimpleAkskFeignRedisClientAutoConfiguration;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor.AkskFeignRequestInterceptor;
import io.github.surezzzzzz.sdk.kms.client.constant.SimpleKmsClientConstant;
import io.github.surezzzzzz.sdk.kms.feign.client.KmsFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KMS Feign 模块装配测试：底座拦截器条件链 + 契约接口元注解挂接形态。
 *
 * @author surezzzzzz
 */
@Slf4j
class KmsFeignAssemblyTest {

    /**
     * 只装载 aksk feign 底座自动配置，令牌链以桩 Bean 提供。
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SimpleAkskFeignRedisClientAutoConfiguration.class)
            .withPropertyValues(
                    "io.github.surezzzzzz.sdk.auth.aksk.client.enable=true",
                    "io.github.surezzzzzz.sdk.kms.client.base-url=https://kms.example.internal");

    private static TokenManager stubTokenManager() {
        return new TokenManager() {
            @Override
            public String getToken() {
                return "test-token";
            }

            @Override
            public void clearToken() {
                // 桩实现无缓存可清
            }
        };
    }

    @Test
    void shouldRegisterBaseInterceptorWhenTokenManagerPresent() {
        contextRunner.withBean(TokenManager.class, () -> stubTokenManager())
                .run(context -> {
                    log.info("底座拦截器 Bean 数量: {}", context.getBeanNamesForType(AkskFeignRequestInterceptor.class).length);
                    assertEquals(1, context.getBeanNamesForType(AkskFeignRequestInterceptor.class).length,
                            "TokenManager 在场时底座必须注册 Feign 认证拦截器");
                });
    }

    @Test
    void shouldStaySilentWithoutTokenManager() {
        contextRunner.run(context ->
                assertEquals(0, context.getBeanNamesForType(AkskFeignRequestInterceptor.class).length,
                        "TokenManager 不在场时底座不装配（本模块为契约接口层，无自有装配面）"));
    }

    @Test
    void shouldCarryAkskAuthConfigurationOnContractInterface() {
        AkskClientFeignClient annotation = KmsFeignClient.class.getAnnotation(AkskClientFeignClient.class);
        assertNotNull(annotation, "契约接口必须标注底座 @AkskClientFeignClient 以自动挂认证");
        assertEquals("kms", annotation.name(), "服务名固定为 kms");
        boolean carriesConfiguration = false;
        for (Class<?> configuration : annotation.configuration()) {
            carriesConfiguration = carriesConfiguration || configuration == AkskFeignConfiguration.class;
        }
        assertTrue(carriesConfiguration, "元注解必须携带底座 AkskFeignConfiguration（Feign 子上下文挂拦截器）");
        assertTrue(annotation.url().contains(SimpleKmsClientConstant.CONFIG_PREFIX),
                "目标地址必须引用 base-url 配置键占位符");
        log.info("契约接口元注解校验通过: name={}, url={}", annotation.name(), annotation.url());
    }
}
