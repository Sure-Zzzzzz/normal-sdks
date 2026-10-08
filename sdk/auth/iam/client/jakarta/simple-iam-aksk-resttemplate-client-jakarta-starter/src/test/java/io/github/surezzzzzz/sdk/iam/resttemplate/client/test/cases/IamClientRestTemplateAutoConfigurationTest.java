package io.github.surezzzzzz.sdk.iam.resttemplate.client.test.cases;

import io.github.surezzzzzz.sdk.iam.client.IamDepartmentClient;
import io.github.surezzzzzz.sdk.iam.client.IamOpenRoleClient;
import io.github.surezzzzzz.sdk.iam.client.IamUserClient;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.IamUserRestTemplateClient;
import io.github.surezzzzzz.sdk.iam.resttemplate.client.configuration.SimpleIamClientRestTemplateAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IAM Client RestTemplate 装配三态测试：默认关/正常装配/缺底座模板响亮失败/非法 origin。
 *
 * @author surezzzzzz
 */
@Slf4j
class IamClientRestTemplateAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SimpleIamClientRestTemplateAutoConfiguration.class, BaseTemplateConfiguration.class);

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
        contextRunner.run(context -> {
            assertEquals(0, context.getBeanNamesForType(IamUserClient.class).length, "enable 未开启时整模块不得装配");
        });
    }

    @Test
    void shouldAssembleThreeClientsWhenEnabled() {
        contextRunner.withPropertyValues(
                        "io.github.surezzzzzz.sdk.iam.client.enable=true",
                        "io.github.surezzzzzz.sdk.iam.client.base-url=https://iam.example.internal")
                .run(context -> {
                    assertNull(context.getStartupFailure(), "合法配置必须完成装配");
                    assertEquals(1, context.getBeanNamesForType(IamUserClient.class).length, "用户族客户端");
                    assertEquals(1, context.getBeanNamesForType(IamDepartmentClient.class).length, "部门族客户端");
                    assertEquals(1, context.getBeanNamesForType(IamOpenRoleClient.class).length, "受委托角色族客户端");
                    assertNotNull(context.getBean(IamUserRestTemplateClient.class), "具体类型可注入");
                });
    }

    @Test
    void shouldFailFastWithoutBaseTemplate() {
        new ApplicationContextRunner()
                .withUserConfiguration(SimpleIamClientRestTemplateAutoConfiguration.class)
                .withPropertyValues(
                        "io.github.surezzzzzz.sdk.iam.client.enable=true",
                        "io.github.surezzzzzz.sdk.iam.client.base-url=https://iam.example.internal")
                .run(context -> {
                    assertNotNull(context.getStartupFailure(), "缺底座模板必须启动失败");
                    assertTrue(hasCause(context.getStartupFailure(), NoSuchBeanDefinitionException.class),
                            "失败原因=底座 RestTemplate Bean 缺失");
                });
    }

    @Test
    void shouldRejectInvalidBaseUrl() {
        contextRunner.withPropertyValues(
                        "io.github.surezzzzzz.sdk.iam.client.enable=true",
                        "io.github.surezzzzzz.sdk.iam.client.base-url=https://iam.example.internal/with-path")
                .run(context -> {
                    assertNotNull(context.getStartupFailure(), "base-url 带 path 必须失败");
                    assertTrue(hasCause(context.getStartupFailure(),
                                    io.github.surezzzzzz.sdk.iam.client.exception.IamClientConfigurationException.class),
                            "失败保留配置异常类型");
                });
    }

    /**
     * 桩出底座同名模板 Bean。
     */
    @Configuration
    static class BaseTemplateConfiguration {

        @Bean
        public RestTemplate akskClientRestTemplate() {
            return new RestTemplate();
        }
    }
}
