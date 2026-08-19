package io.github.surezzzzzz.sdk.audit.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.audit.aksk.server.handler.impl.LogServerTokenAuditHandler;
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
 * Server Token 审计监听器默认自动配置测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = ServerTokenAuditListenerAutoConfigurationTest.DefaultConfiguration.class)
class ServerTokenAuditListenerAutoConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldRegisterDefaultLogHandlerAndListenerByDefault() {
        log.info("验证已发布 Server 完整启动时默认日志处理器和审计监听器均已注册");
        assertEquals(1, applicationContext.getBeansOfType(LogServerTokenAuditHandler.class).size());
        assertEquals(1, applicationContext.getBeansOfType(ServerTokenAuditEventListener.class).size());
    }

    /**
     * 不扫描测试组件，确保仅由已发布 Server 与 listener 自动配置构成测试宿主。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class DefaultConfiguration {
    }
}
