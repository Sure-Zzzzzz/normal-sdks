package io.github.surezzzzzz.sdk.audit.http.xff.test.cases;

import io.github.surezzzzzz.sdk.audit.http.xff.configuration.SimpleXffCaptureAuditListenerConfiguration;
import io.github.surezzzzzz.sdk.audit.http.xff.configuration.XffCaptureAuditPropertiesValidator;
import io.github.surezzzzzz.sdk.audit.http.xff.constant.SimpleXffCaptureAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.http.xff.context.XffCaptureAuditContext;
import io.github.surezzzzzz.sdk.audit.http.xff.context.XffCaptureAuditContextProvider;
import io.github.surezzzzzz.sdk.audit.http.xff.factory.XffCaptureAuditDocumentFactory;
import io.github.surezzzzzz.sdk.audit.http.xff.listener.XffCaptureAuditEventListener;
import io.github.surezzzzzz.sdk.audit.http.xff.provider.LoggingXffCaptureAuditPersistenceProvider;
import io.github.surezzzzzz.sdk.audit.http.xff.provider.XffCaptureAuditPersistenceProvider;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.SpringFactoriesLoader;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XFF Capture Audit Listener 自动配置测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class XffCaptureAuditAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleXffCaptureAuditListenerConfiguration.class))
            .withPropertyValues(
                    SimpleXffCaptureAuditListenerConstant.CONFIG_PREFIX + ".enable=true",
                    "spring.application.name=auto-config-test-service"
            );

    @Test
    void shouldDeclareSpringFactoriesAutoConfiguration() {
        List<String> configurations = SpringFactoriesLoader.loadFactoryNames(
                EnableAutoConfiguration.class,
                getClass().getClassLoader());

        log.info("Spring factories 自动配置声明数量：{}", configurations.size());
        assertTrue(configurations.contains(SimpleXffCaptureAuditListenerConfiguration.class.getName()),
                "spring.factories 必须声明 Listener 自动配置");
    }

    @Test
    void shouldRegisterListenerExecutorAndDefaultProviderWithoutPersistence() {
        contextRunner.run(context -> {
            log.info("无 Persistence 时自动配置 Bean：listener={}，executor={}，loggingProvider={}",
                    context.getBeansOfType(XffCaptureAuditEventListener.class).size(),
                    context.containsBean(SimpleXffCaptureAuditListenerConstant.EXECUTOR_BEAN_NAME),
                    context.getBeansOfType(LoggingXffCaptureAuditPersistenceProvider.class).size());
            assertEquals(1, context.getBeansOfType(XffCaptureAuditEventListener.class).size(),
                    "应注册一个 Listener");
            assertEquals(1, context.getBeansOfType(XffCaptureAuditDocumentFactory.class).size(),
                    "应注册一个文档 Factory");
            assertEquals(1, context.getBeansOfType(XffCaptureAuditPropertiesValidator.class).size(),
                    "应注册一个配置校验器");
            assertEquals(1, context.getBeansOfType(LoggingXffCaptureAuditPersistenceProvider.class).size(),
                    "应注册一个默认日志 Provider");
            assertTrue(context.containsBean(SimpleXffCaptureAuditListenerConstant.LOGGING_PROVIDER_BEAN_NAME),
                    "默认日志 Provider 必须使用固定 Bean 名称");
            assertTrue(context.containsBean(SimpleXffCaptureAuditListenerConstant.EXECUTOR_BEAN_NAME),
                    "应注册专用执行器");
            assertFalse(context.getStartupFailure() != null, "合法配置不应启动失败");
        });
    }

    @Test
    void shouldKeepDefaultLoggingProviderAlongsideCustomProvider() {
        contextRunner.withUserConfiguration(CustomProviderConfiguration.class)
                .run(context -> {
                    log.info("Provider 数量：logging={}，all={}",
                            context.getBeansOfType(LoggingXffCaptureAuditPersistenceProvider.class).size(),
                            context.getBeansOfType(XffCaptureAuditPersistenceProvider.class).size());
                    assertEquals(1,
                            context.getBeansOfType(LoggingXffCaptureAuditPersistenceProvider.class).size(),
                            "业务 Provider 存在时默认日志 Provider 仍必须保留");
                    assertEquals(2,
                            context.getBeansOfType(XffCaptureAuditPersistenceProvider.class).size(),
                            "默认日志 Provider 与业务 Provider 必须共同参与广播");
                });
    }

    @Test
    void shouldNotRegisterWhenDisabled() {
        contextRunner.withPropertyValues(
                        SimpleXffCaptureAuditListenerConstant.CONFIG_PREFIX + ".enable=false")
                .run(context -> {
                    log.info("关闭时 Listener 数量：{}",
                            context.getBeansOfType(XffCaptureAuditEventListener.class).size());
                    assertTrue(context.getBeansOfType(XffCaptureAuditEventListener.class).isEmpty(),
                            "关闭时不应注册 Listener");
                    assertFalse(context.containsBean(SimpleXffCaptureAuditListenerConstant.EXECUTOR_BEAN_NAME),
                            "关闭时不应注册执行器");
                    assertTrue(context.getBeansOfType(LoggingXffCaptureAuditPersistenceProvider.class).isEmpty(),
                            "关闭时不应注册默认日志 Provider");
                });
    }

    @Test
    void shouldFailFastWhenMultipleContextProvidersExist() {
        contextRunner.withUserConfiguration(MultipleContextProviderConfiguration.class)
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    log.info("多个上下文 Provider 启动异常类型：{}",
                            failure == null ? null : failure.getClass().getName());
                    assertNotNull(failure, "多个 Context Provider 必须因来源歧义启动失败");
                });
    }

    @Test
    void shouldRejectSameNamedDefaultLoggingProvider() {
        contextRunner.withUserConfiguration(SameNamedLoggingProviderConfiguration.class)
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    log.info("同名默认日志 Provider 启动异常类型：{}",
                            failure == null ? null : failure.getClass().getName());
                    assertNotNull(failure, "业务不能覆盖固定名称的默认日志 Provider");
                });
    }

    @Test
    void shouldFailFastWhenApplicationNameMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleXffCaptureAuditListenerConfiguration.class))
                .withPropertyValues(SimpleXffCaptureAuditListenerConstant.CONFIG_PREFIX + ".enable=true")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    log.info("应用名缺失启动异常类型：{}",
                            failure == null ? null : failure.getClass().getName());
                    assertNotNull(failure, "应用名缺失时必须启动失败");
                });
    }

    @Test
    void shouldValidatePropertiesEvenWhenExecutorIsOverridden() {
        contextRunner.withUserConfiguration(CustomExecutorConfiguration.class)
                .withPropertyValues(
                        SimpleXffCaptureAuditListenerConstant.CONFIG_PREFIX + ".executor.core-size=0")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    log.info("覆盖执行器后的非法配置异常类型：{}",
                            failure == null ? null : failure.getClass().getName());
                    assertNotNull(failure, "覆盖执行器也不能绕过 Properties 校验");
                });
    }

    @Configuration
    static class MultipleContextProviderConfiguration {

        @Bean
        XffCaptureAuditContextProvider firstContextProvider() {
            return () -> new XffCaptureAuditContext("request-1", "trace-1");
        }

        @Bean
        XffCaptureAuditContextProvider secondContextProvider() {
            return () -> new XffCaptureAuditContext("request-2", "trace-2");
        }
    }

    @Configuration
    static class CustomProviderConfiguration {

        @Bean
        XffCaptureAuditPersistenceProvider customProvider() {
            return document -> {
            };
        }
    }

    @Configuration
    static class SameNamedLoggingProviderConfiguration {

        @Bean(name = SimpleXffCaptureAuditListenerConstant.LOGGING_PROVIDER_BEAN_NAME)
        Object sameNamedLoggingProvider() {
            return new Object();
        }
    }

    @Configuration
    static class CustomExecutorConfiguration {

        @Bean(name = SimpleXffCaptureAuditListenerConstant.EXECUTOR_BEAN_NAME)
        Executor customExecutor() {
            return Runnable::run;
        }
    }
}
