package io.github.surezzzzzz.sdk.kms.server.test.cases;

import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerAutoConfiguration;
import io.github.surezzzzzz.sdk.kms.server.controller.KmsCryptoController;
import io.github.surezzzzzz.sdk.kms.server.controller.KmsKeyController;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalResolver;
import io.github.surezzzzzz.sdk.kms.server.service.KmsServerEngine;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;

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
