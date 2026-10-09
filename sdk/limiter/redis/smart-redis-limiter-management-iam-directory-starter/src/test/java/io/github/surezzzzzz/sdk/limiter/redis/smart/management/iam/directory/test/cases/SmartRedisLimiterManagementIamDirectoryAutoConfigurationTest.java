package io.github.surezzzzzz.sdk.limiter.redis.smart.management.iam.directory.test.cases;

import io.github.surezzzzzz.sdk.iam.client.IamUserClient;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterDirectoryObject;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterDirectoryProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterServiceDeclaration;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.iam.directory.IamUserSmartRedisLimiterDirectoryProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.iam.directory.configuration.SmartRedisLimiterManagementIamDirectoryAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IAM 用户目录适配件装配测试（adaptor 形态：引用即装配，装饰器注册）。
 *
 * <p>容器内每个目录提供方 Bean 初始化后被包装为适配件（USER 维度走 IAM，其余转发原实现）；
 * IamUserClient 缺失时装饰阶段响亮失败。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
class SmartRedisLimiterManagementIamDirectoryAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SmartRedisLimiterManagementIamDirectoryAutoConfiguration.class))
            .withUserConfiguration(DirectoryBeanConfiguration.class, StubClientConfiguration.class);

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> expectedType) {
        Throwable current = throwable;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Test
    void shouldDecorateDirectoryBeanByReference() {
        contextRunner.run(context -> {
            assertEquals(1, context.getBeanNamesForType(SmartRedisLimiterDirectoryProvider.class).length,
                    "目录提供方仍恰好一个（装饰不增量）");
            assertTrue(context.getBean(SmartRedisLimiterDirectoryProvider.class)
                            instanceof IamUserSmartRedisLimiterDirectoryProvider,
                    "引用即装配：目录 Bean 已被装饰为适配件");
            log.info("引用即装饰验证通过：USER 维度由 IAM 接管");
        });
    }

    @Test
    void shouldFailFastWhenClientMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SmartRedisLimiterManagementIamDirectoryAutoConfiguration.class))
                .withUserConfiguration(DirectoryBeanConfiguration.class)
                .run(context -> {
                    assertNotNull(context.getStartupFailure(), "客户端缺失必须启动失败（响亮失败）");
                    assertTrue(hasCause(context.getStartupFailure(), NoSuchBeanDefinitionException.class),
                            "失败原因=IamUserClient Bean 缺失");
                    log.info("缺客户端响亮失败验证通过");
                });
    }

    /**
     * 模拟宿主侧已有的目录提供方（管理件配置式或自有实现）。
     */
    @Configuration
    static class DirectoryBeanConfiguration {

        @Bean
        public SmartRedisLimiterDirectoryProvider hostDirectoryProvider() {
            return new HostDirectoryProvider();
        }
    }

    static class HostDirectoryProvider implements SmartRedisLimiterDirectoryProvider {

        @Override
        public List<SmartRedisLimiterServiceDeclaration> listServices() {
            return Collections.emptyList();
        }

        @Override
        public SmartRedisLimiterServiceDeclaration findService(String serviceCode) {
            return null;
        }

        @Override
        public List<SmartRedisLimiterDirectoryObject> listObjects(String serviceCode, String dimension,
                                                                  String customType, String keyword, int limit) {
            return Collections.emptyList();
        }
    }

    /**
     * 桩 IAM 用户客户端（表达 iam.client 装配链已就绪的事实；装配测试不调用业务方法）。
     */
    @Configuration
    static class StubClientConfiguration {

        @Bean
        public IamUserClient iamUserClient() {
            return org.mockito.Mockito.mock(IamUserClient.class);
        }
    }
}
