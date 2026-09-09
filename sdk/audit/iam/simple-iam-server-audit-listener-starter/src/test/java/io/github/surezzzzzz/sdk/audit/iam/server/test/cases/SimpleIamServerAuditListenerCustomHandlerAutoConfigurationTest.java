package io.github.surezzzzzz.sdk.audit.iam.server.test.cases;

import io.github.surezzzzzz.sdk.audit.iam.server.handler.ServerIamAuditHandler;
import io.github.surezzzzzz.sdk.audit.iam.server.handler.impl.LogServerIamAuditHandler;
import io.github.surezzzzzz.sdk.audit.iam.server.listener.ServerIamAuditEventListener;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IAM 审计监听器自定义处理器自动配置测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleIamServerAuditListenerCustomHandlerAutoConfigurationTest.CustomHandlerConfiguration.class,
        properties = {
                "io.github.surezzzzzz.sdk.auth.iam.server.enable=false",
                "io.github.surezzzzzz.sdk.audit.iam.server.listener.handler.log.enabled=false"
        }
)
class SimpleIamServerAuditListenerCustomHandlerAutoConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldRegisterCustomHandlerWithoutDefaultLogHandlerWhenDisabled() {
        log.info("验证关闭默认日志处理器后仍注册调用方处理器和 IAM 审计监听器");
        assertEquals(0, applicationContext.getBeansOfType(LogServerIamAuditHandler.class).size());
        assertEquals(1, applicationContext.getBeansOfType(ServerIamAuditHandler.class).size());
        assertEquals(1, applicationContext.getBeansOfType(ServerIamAuditEventListener.class).size());
    }

    /**
     * 不扫描测试组件，只注册调用方显式提供的审计处理器。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class CustomHandlerConfiguration {

        @Bean
        ServerIamAuditHandler customServerIamAuditHandler() {
            return record -> {
            };
        }
    }
}
