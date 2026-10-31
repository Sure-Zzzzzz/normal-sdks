package io.github.surezzzzzz.sdk.audit.license.test.cases;

import io.github.surezzzzzz.sdk.audit.license.configuration.LicenseAuditListenerAutoConfiguration;
import io.github.surezzzzzz.sdk.audit.license.handler.impl.LogLicenseAuditHandler;
import io.github.surezzzzzz.sdk.audit.license.listener.LicenseAuditEventListener;
import io.github.surezzzzzz.sdk.license.server.event.LicenseAuditEvents;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * License 审计监听器无处理器装配测试：宿主未提供 LicenseAuditHandler 时
 * 不得注册监听器，事件发布无副作用。
 *
 * @author surezzzzzz
 * @since 2.0.0
 */
@Slf4j
@SpringBootTest(classes = LicenseAuditListenerAutoConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LicenseAuditListenerNoHandlerTest {

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Test
    void shouldNotRegisterListenerWithoutHandler() {
        assertTrue(applicationContext.getBeansOfType(LicenseAuditEventListener.class).isEmpty(),
                "无处理器时不得注册审计监听器");
        assertTrue(applicationContext.getBeansOfType(LogLicenseAuditHandler.class).isEmpty(),
                "默认日志处理器保持关闭");

        eventPublisher.publishEvent(new LicenseAuditEvents.GrantCreated(
                "iam:zhang-san", "request-example", "gr-example", "cu-example",
                "prod-example", "PERSON", "ONLINE", null));
        log.info("验证无处理器装配下事件发布无副作用");
    }
}
