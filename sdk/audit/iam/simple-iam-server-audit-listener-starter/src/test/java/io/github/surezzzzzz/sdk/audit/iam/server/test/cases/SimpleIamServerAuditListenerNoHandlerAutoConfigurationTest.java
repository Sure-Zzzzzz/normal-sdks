package io.github.surezzzzzz.sdk.audit.iam.server.test.cases;

import io.github.surezzzzzz.sdk.audit.iam.server.handler.ServerIamAuditHandler;
import io.github.surezzzzzz.sdk.audit.iam.server.listener.ServerIamAuditEventListener;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IAM 审计监听器无处理器自动配置测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleIamServerAuditListenerNoHandlerAutoConfigurationTest.NoHandlerConfiguration.class,
        properties = {
                "io.github.surezzzzzz.sdk.auth.iam.server.enable=false",
                "io.github.surezzzzzz.sdk.audit.iam.server.listener.handler.log.enabled=false"
        }
)
class SimpleIamServerAuditListenerNoHandlerAutoConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldNotRegisterListenerWithoutAnyHandler() {
        log.info("验证没有任何审计处理器时不会注册 IAM 审计监听器");
        assertEquals(0, applicationContext.getBeansOfType(ServerIamAuditHandler.class).size());
        assertEquals(0, applicationContext.getBeansOfType(ServerIamAuditEventListener.class).size());
    }

    /**
     * 不扫描测试组件，验证没有调用方处理器时的自动配置条件。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class NoHandlerConfiguration {
    }
}
