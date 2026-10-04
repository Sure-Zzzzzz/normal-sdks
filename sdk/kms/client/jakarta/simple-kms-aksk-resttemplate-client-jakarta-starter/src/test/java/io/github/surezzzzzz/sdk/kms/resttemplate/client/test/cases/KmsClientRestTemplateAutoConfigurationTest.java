package io.github.surezzzzzz.sdk.kms.resttemplate.client.test.cases;

import io.github.surezzzzzz.sdk.kms.client.KmsClient;
import io.github.surezzzzzz.sdk.kms.client.exception.KmsClientConfigurationException;
import io.github.surezzzzzz.sdk.kms.resttemplate.client.KmsRestTemplateClient;
import io.github.surezzzzzz.sdk.kms.resttemplate.client.configuration.KmsClientRestTemplateAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KMS Client RestTemplate 自动配置测试：装配三态（默认关/缺底座模板响亮失败/正常装配）与发现路径。
 *
 * @author surezzzzzz
 */
@Slf4j
class KmsClientRestTemplateAutoConfigurationTest {

    /**
     * 只装载本模块自动配置，底座 RestTemplate 以同名桩 Bean 提供。
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(KmsClientRestTemplateAutoConfiguration.class);

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
    void shouldStayDisabledByDefault() {
        contextRunner.withBean("akskClientRestTemplate", RestTemplate.class, RestTemplate::new)
                .run(context -> {
                    log.info("默认配置 Bean 数量: {}", context.getBeanDefinitionCount());
                    assertEquals(0, context.getBeanNamesForType(KmsClient.class).length,
                            "enable 未开启时整模块不得装配");
                });
    }

    @Test
    void shouldAssembleClientWhenEnabledWithBaseTemplate() {
        contextRunner.withPropertyValues(
                        "io.github.surezzzzzz.sdk.kms.client.enable=true",
                        "io.github.surezzzzzz.sdk.kms.client.base-url=https://kms.example.internal")
                .withBean("akskClientRestTemplate", RestTemplate.class, RestTemplate::new)
                .run(context -> {
                    log.info("启用后 Bean 数量: {}", context.getBeanDefinitionCount());
                    assertNull(context.getStartupFailure(), "合法配置必须完成自动装配");
                    assertEquals(1, context.getBeanNamesForType(KmsClient.class).length,
                            "enable 开启时必须装配 KmsRestTemplateClient");
                    assertNotNull(context.getBean(KmsRestTemplateClient.class), "客户端必须可按具体类型注入");
                });
    }

    @Test
    void shouldFailFastWithoutBaseTemplate() {
        contextRunner.withPropertyValues(
                        "io.github.surezzzzzz.sdk.kms.client.enable=true",
                        "io.github.surezzzzzz.sdk.kms.client.base-url=https://kms.example.internal")
                .run(context -> {
                    log.info("缺底座模板启动异常类型: {}",
                            context.getStartupFailure() == null ? "无" : context.getStartupFailure().getClass().getName());
                    assertNotNull(context.getStartupFailure(), "底座 RestTemplate 缺失必须启动失败（缺 Bean 响亮报错）");
                    assertTrue(hasCause(context.getStartupFailure(), NoSuchBeanDefinitionException.class),
                            "失败原因必须是底座 RestTemplate Bean 缺失");
                });
    }

    @Test
    void shouldRejectInvalidBaseUrl() {
        contextRunner.withPropertyValues(
                        "io.github.surezzzzzz.sdk.kms.client.enable=true",
                        "io.github.surezzzzzz.sdk.kms.client.base-url=https://kms.example.internal/with-path")
                .withBean("akskClientRestTemplate", RestTemplate.class, RestTemplate::new)
                .run(context -> {
                    log.info("非法 origin 启动异常类型: {}",
                            context.getStartupFailure() == null ? "无" : context.getStartupFailure().getClass().getName());
                    assertNotNull(context.getStartupFailure(), "非法 base-url 必须启动失败");
                    assertTrue(hasCause(context.getStartupFailure(), KmsClientConfigurationException.class),
                            "非法 base-url 必须保留 Client 配置异常类型");
                });
    }
}
