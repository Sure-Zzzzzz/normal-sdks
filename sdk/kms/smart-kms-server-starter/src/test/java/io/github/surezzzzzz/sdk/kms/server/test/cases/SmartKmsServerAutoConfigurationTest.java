package io.github.surezzzzzz.sdk.kms.server.test.cases;

import io.github.surezzzzzz.sdk.kms.core.repository.KmsKeyVersionRepository;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerAutoConfiguration;
import io.github.surezzzzzz.sdk.kms.server.controller.*;
import io.github.surezzzzzz.sdk.kms.server.repository.KmsKeyDestructionQueryRepository;
import io.github.surezzzzzz.sdk.kms.server.service.KmsMyPublicKeyService;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalResolver;
import io.github.surezzzzzz.sdk.kms.server.service.KmsServerEngine;
import io.github.surezzzzzz.sdk.kms.server.support.KmsMyKeyHttpMessageConverter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * KMS Server 自动配置替换边界测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class SmartKmsServerAutoConfigurationTest {

    /**
     * 验证调用方提供完整 engine 后，默认 HTTP、JDBC、JCA 和 worker 链路均不会注册。
     */
    @Test
    void shouldDisableEntireDefaultChainWhenCustomEngineExists() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SmartKmsServerAutoConfiguration.class))
                .withUserConfiguration(CustomEngineConfiguration.class);

        contextRunner.run(context -> {
            log.info("自定义 KMS Server engine 上下文已启动，Bean 数量: {}", context.getBeanDefinitionCount());
            assertFalse(context.containsBean("kmsServerEngine"), "默认 engine 不得覆盖调用方提供的完整实现");
            assertFalse(context.containsBean("kmsKeyController"), "完整替换时不得注册默认管理控制器");
            assertFalse(context.containsBean("kmsCryptoController"), "完整替换时不得注册默认密码学控制器");
            assertFalse(context.getBeansOfType(KmsKeyController.class).size() > 0,
                    "完整替换时不得保留默认管理控制器类型");
            assertFalse(context.getBeansOfType(KmsCryptoController.class).size() > 0,
                    "完整替换时不得保留默认密码学控制器类型");
            assertFalse(context.getBeansOfType(KmsMyKeyManagementController.class).size() > 0,
                    "完整替换时不得遗留本人生命周期路由");
            assertFalse(context.getBeansOfType(KmsMyKeyHttpMessageConverter.class).size() > 0,
                    "完整替换时不得遗留本人类型转换器");
            assertTrue(context.getBeansOfType(KmsMyPublicKeyController.class).isEmpty());
            assertTrue(context.getBeansOfType(KmsKeyDestructionQueryController.class).isEmpty());
        });
    }

    /**
     * 没有主体解析器时不开放孤立的默认本人路由。
     */
    @Test
    void shouldNotRegisterMyKeyRoutesWithoutPrincipalResolver() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(SmartKmsServerAutoConfiguration.class))
                .run(context -> {
                    assertFalse(context.containsBean("kmsMyKeyManagementController"));
                    assertFalse(context.containsBean("kmsMyKeyHttpMessageConverter"));
                    assertFalse(context.containsBean("kmsMyKeyWebMvcConfigurer"));
                    assertTrue(context.getBeansOfType(KmsMyPublicKeyController.class).isEmpty());
                    assertTrue(context.getBeansOfType(KmsKeyDestructionQueryController.class).isEmpty());
                });
    }

    private ApplicationContextRunner defaultRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SmartKmsServerAutoConfiguration.class))
                .withBean(KmsPrincipalResolver.class, () -> request -> null)
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(NamedParameterJdbcTemplate.class, () -> mock(NamedParameterJdbcTemplate.class))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withPropertyValues("io.github.surezzzzzz.sdk.kms.server.worker.enabled=false");
    }

    /**
     * 默认存储能够装配新增查询，宿主按类型替换新端口不会被覆盖。
     */
    @Test
    void shouldRegisterDefaultQueriesAndAllowPortReplacement() {
        defaultRunner().run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(KmsMyPublicKeyService.class).size());
            assertEquals(1, context.getBeansOfType(KmsMyPublicKeyController.class).size());
            assertEquals(1, context.getBeansOfType(KmsKeyDestructionQueryRepository.class).size());
            assertEquals(1, context.getBeansOfType(KmsKeyDestructionQueryController.class).size());
        });
        KmsMyPublicKeyService publicKeys = mock(KmsMyPublicKeyService.class);
        KmsKeyDestructionQueryRepository details = mock(KmsKeyDestructionQueryRepository.class);
        defaultRunner().withBean("customMyPublicKeys", KmsMyPublicKeyService.class, () -> publicKeys)
                .withBean("customDestructionDetails", KmsKeyDestructionQueryRepository.class, () -> details).run(context -> {
                    assertNull(context.getStartupFailure());
                    assertSame(publicKeys, context.getBean(KmsMyPublicKeyService.class));
                    assertSame(details, context.getBean(KmsKeyDestructionQueryRepository.class));
                    assertFalse(context.containsBean("kmsMyPublicKeyService"));
                    assertFalse(context.containsBean("kmsKeyDestructionQueryRepository"));
                    assertEquals(1, context.getBeansOfType(KmsKeyDestructionQueryController.class).size());
                });
    }

    /**
     * 版本存储由宿主替换后，不产生与实际存储脱节的默认 JDBC 明细。
     */
    @Test
    void shouldNotReadShadowJdbcDataWithCustomVersionStorage() {
        defaultRunner().withBean(KmsKeyVersionRepository.class, () -> mock(KmsKeyVersionRepository.class))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.getBeansOfType(KmsKeyDestructionQueryRepository.class).isEmpty());
                    assertTrue(context.getBeansOfType(KmsKeyDestructionQueryController.class).isEmpty());
                    assertEquals(1, context.getBeansOfType(KmsMyPublicKeyController.class).size());
                    assertEquals(1, context.getBeansOfType(KmsMyKeyManagementController.class).size());
                });
    }

    /**
     * 自定义完整 engine 与认证主体解析器测试配置。
     */
    @Configuration
    static class CustomEngineConfiguration {

        /**
         * 注册调用方完整 KMS Server engine。
         *
         * @return 调用方完整 KMS Server engine
         */
        @Bean
        public KmsServerEngine customKmsServerEngine() {
            return new KmsServerEngine() {
            };
        }

        /**
         * 注册调用方认证主体解析器占位实现。
         *
         * @return 调用方认证主体解析器
         */
        @Bean
        public KmsPrincipalResolver kmsPrincipalResolver() {
            return request -> null;
        }
    }
}
