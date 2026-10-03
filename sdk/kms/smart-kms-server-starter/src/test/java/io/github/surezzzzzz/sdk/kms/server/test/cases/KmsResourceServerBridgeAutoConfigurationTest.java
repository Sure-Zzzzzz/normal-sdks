package io.github.surezzzzzz.sdk.kms.server.test.cases;

import io.github.surezzzzzz.sdk.kms.server.configuration.KmsResourceServerBridgeAutoConfiguration;
import io.github.surezzzzzz.sdk.kms.server.configuration.SmartKmsServerAutoConfiguration;
import io.github.surezzzzzz.sdk.kms.server.controller.KmsMeController;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalResolver;
import io.github.surezzzzzz.sdk.kms.server.service.KmsResourceServerPrincipalResolver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 组合式 Resource Server 认证桥装配矩阵测试：时序、让位与 fail-closed。
 *
 * @author surezzzzzz
 */
@Slf4j
class KmsResourceServerBridgeAutoConfigurationTest {

    private static boolean hasNoUniqueResolverFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof org.springframework.beans.factory.NoUniqueBeanDefinitionException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withBean(JdbcTemplate.class, () -> Mockito.mock(JdbcTemplate.class))
                .withBean(NamedParameterJdbcTemplate.class, () -> Mockito.mock(NamedParameterJdbcTemplate.class))
                .withBean(PlatformTransactionManager.class, () -> Mockito.mock(PlatformTransactionManager.class))
                .withConfiguration(AutoConfigurations.of(SmartKmsServerAutoConfiguration.class,
                        KmsResourceServerBridgeAutoConfiguration.class));
    }

    /**
     * 验证无宿主 resolver 且公共层在 classpath 时，桥先于主配置类级条件可见，
     * KMS 完整链与桥同时装配（桥唯一必需场景）。
     */
    @Test
    void shouldRegisterBridgeAndAssembleKmsWhenNoHostResolver() {
        contextRunner().run(context -> {
            log.info("桥装配上下文启动结果: {}", context);
            assertTrue(context.containsBean("kmsResourceServerPrincipalResolver"), "桥必须注册");
            assertTrue(context.containsBean("kmsKeyController"), "主配置类级条件必须能看到桥，KMS 完整链必须装配");
            assertTrue(context.containsBean("kmsMeController"), "认证桥实际注册时必须提供门户自省端点");
            assertEquals(1, context.getBeansOfType(KmsPrincipalResolver.class).size(),
                    "容器内必须恰好一个 resolver（桥）");
            assertTrue(context.getBean(KmsPrincipalResolver.class) instanceof KmsResourceServerPrincipalResolver,
                    "唯一 resolver 必须是桥");
        });
    }

    /**
     * 验证宿主自带 resolver 时桥让位且正常启动。
     */
    @Test
    void shouldBackOffWhenHostResolverProvided() {
        contextRunner().withUserConfiguration(HostResolverConfiguration.class).run(context -> {
            log.info("宿主 resolver 让位上下文启动结果: {}", context);
            assertFalse(context.containsBean("kmsResourceServerPrincipalResolver"), "桥必须让位");
            assertEquals(1, context.getBeansOfType(KmsPrincipalResolver.class).size(),
                    "容器内必须只剩宿主 resolver");
            assertTrue(context.getBean(KmsPrincipalResolver.class) instanceof HostResolver,
                    "唯一 resolver 必须是宿主实现");
            assertTrue(context.containsBean("kmsKeyController"), "让位是正常路径，KMS 必须照常装配");
            assertTrue(context.containsBean("kmsMeController"), "自省端点只依赖通用 resolver，宿主实现也应可装配");
            assertEquals(1, context.getBeansOfType(KmsMeController.class).size(), "宿主 resolver 场景必须创建自省控制器");
        });
    }

    /**
     * 验证宿主自注两个 resolver 时启动失败（禁止按顺序消歧）。
     */
    @Test
    void shouldFailFastWhenTwoHostResolversProvided() {
        contextRunner().withUserConfiguration(TwoHostResolversConfiguration.class).run(context -> {
            log.info("双宿主 resolver 启动结果: {}", context);
            assertTrue(context.getStartupFailure() != null, "双 resolver 必须启动失败");
            assertTrue(hasNoUniqueResolverFailure(context.getStartupFailure()),
                    "失败原因必须是 resolver 唯一性冲突，而不是静默按顺序消歧");
        });
    }

    /**
     * 宿主单 resolver 测试配置。
     */
    @Configuration
    static class HostResolverConfiguration {

        /**
         * 注册宿主自定义 resolver。
         *
         * @return 宿主 resolver
         */
        @Bean
        public KmsPrincipalResolver hostKmsPrincipalResolver() {
            return new HostResolver();
        }
    }

    /**
     * 宿主 resolver 占位实现。
     *
     * @author surezzzzzz
     */
    static class HostResolver implements KmsPrincipalResolver {

        @Override
        public io.github.surezzzzzz.sdk.kms.server.service.KmsRequestContext resolve(
                javax.servlet.http.HttpServletRequest request) {
            return null;
        }
    }

    /**
     * 宿主双 resolver 测试配置。
     */
    @Configuration
    static class TwoHostResolversConfiguration {

        /**
         * 注册宿主 resolver 一。
         *
         * @return 宿主 resolver
         */
        @Bean
        public KmsPrincipalResolver firstHostResolver() {
            return new HostResolver();
        }

        /**
         * 注册宿主 resolver 二。
         *
         * @return 宿主 resolver
         */
        @Bean
        public KmsPrincipalResolver secondHostResolver() {
            return new HostResolver();
        }
    }
}
