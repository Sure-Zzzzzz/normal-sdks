package io.github.surezzzzzz.sdk.audit.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.audit.aksk.server.handler.ServerClientLifecycleAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.server.listener.ServerClientLifecycleAuditEventListener;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 关闭默认日志 Handler 且业务不提供 Handler 时，生命周期审计监听器不得装配。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = ServerClientLifecycleAuditListenerNoHandlerAutoConfigurationTest.EmptyConfiguration.class,
        properties = "io.github.surezzzzzz.sdk.audit.aksk.server.listener.handler.log.enabled=false")
class ServerClientLifecycleAuditListenerNoHandlerAutoConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldNotRegisterLifecycleListenerWithoutAnyHandler() {
        assertEquals(0, applicationContext.getBeansOfType(ServerClientLifecycleAuditHandler.class).size());
        assertEquals(0, applicationContext.getBeansOfType(ServerClientLifecycleAuditEventListener.class).size());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class EmptyConfiguration {
    }
}
