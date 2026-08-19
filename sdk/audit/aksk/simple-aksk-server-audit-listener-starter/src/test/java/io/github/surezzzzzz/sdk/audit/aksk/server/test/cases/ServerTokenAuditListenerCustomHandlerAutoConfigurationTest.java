package io.github.surezzzzzz.sdk.audit.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.audit.aksk.server.handler.ServerTokenAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.server.handler.impl.LogServerTokenAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.server.listener.ServerTokenAuditEventListener;
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
 * Server Token 审计监听器自定义处理器自动配置测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = ServerTokenAuditListenerCustomHandlerAutoConfigurationTest.CustomHandlerConfiguration.class,
        properties = "io.github.surezzzzzz.sdk.audit.aksk.server.listener.handler.log.enabled=false"
)
class ServerTokenAuditListenerCustomHandlerAutoConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldRegisterCustomHandlerWithoutDefaultLogHandlerWhenDisabled() {
        log.info("验证已发布 Server 完整启动时关闭默认日志处理器后仍注册调用方处理器和审计监听器");
        assertEquals(0, applicationContext.getBeansOfType(LogServerTokenAuditHandler.class).size());
        assertEquals(1, applicationContext.getBeansOfType(ServerTokenAuditHandler.class).size());
        assertEquals(1, applicationContext.getBeansOfType(ServerTokenAuditEventListener.class).size());
    }

    /**
     * 不扫描测试组件，只注册调用方显式提供的审计处理器。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class CustomHandlerConfiguration {

        @Bean
        ServerTokenAuditHandler customServerTokenAuditHandler() {
            return record -> {
            };
        }
    }
}
