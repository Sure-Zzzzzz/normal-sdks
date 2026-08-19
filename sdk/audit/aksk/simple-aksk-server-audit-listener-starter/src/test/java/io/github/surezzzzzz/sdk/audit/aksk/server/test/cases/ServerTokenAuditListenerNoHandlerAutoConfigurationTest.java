package io.github.surezzzzzz.sdk.audit.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.audit.aksk.server.handler.ServerTokenAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.server.listener.ServerTokenAuditEventListener;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Server Token 审计监听器无处理器自动配置测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = ServerTokenAuditListenerNoHandlerAutoConfigurationTest.NoHandlerConfiguration.class,
        properties = "io.github.surezzzzzz.sdk.audit.aksk.server.listener.handler.log.enabled=false"
)
class ServerTokenAuditListenerNoHandlerAutoConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldNotRegisterListenerWithoutAnyHandler() {
        log.info("验证已发布 Server 完整启动时没有任何审计处理器不会注册审计监听器");
        assertEquals(0, applicationContext.getBeansOfType(ServerTokenAuditHandler.class).size());
        assertEquals(0, applicationContext.getBeansOfType(ServerTokenAuditEventListener.class).size());
    }

    /**
     * 不扫描测试组件，验证没有调用方处理器时的自动配置条件。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class NoHandlerConfiguration {
    }
}
