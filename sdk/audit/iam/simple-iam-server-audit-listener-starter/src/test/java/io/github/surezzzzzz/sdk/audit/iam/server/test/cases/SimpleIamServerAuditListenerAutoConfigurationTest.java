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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IAM 审计监听器默认自动配置测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleIamServerAuditListenerAutoConfigurationTest.DefaultConfiguration.class,
        properties = "io.github.surezzzzzz.sdk.auth.iam.server.enable=false"
)
class SimpleIamServerAuditListenerAutoConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldRegisterDefaultLogHandlerAndListenerByDefault() {
        log.info("验证默认日志处理器和 IAM 审计监听器均已注册");
        assertEquals(1, applicationContext.getBeansOfType(LogServerIamAuditHandler.class).size());
        assertEquals(1, applicationContext.getBeansOfType(ServerIamAuditHandler.class).size());
        assertEquals(1, applicationContext.getBeansOfType(ServerIamAuditEventListener.class).size());
    }

    /**
     * 关闭 IAM server 自动配置，仅由 listener 自动配置构成轻量测试宿主。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class DefaultConfiguration {
    }
}
