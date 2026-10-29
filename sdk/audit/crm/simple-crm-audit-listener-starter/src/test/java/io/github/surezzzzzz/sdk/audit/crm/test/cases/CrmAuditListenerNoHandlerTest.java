package io.github.surezzzzzz.sdk.audit.crm.test.cases;

import io.github.surezzzzzz.sdk.audit.crm.handler.impl.LogCrmAuditHandler;
import io.github.surezzzzzz.sdk.audit.crm.listener.CrmAuditEventListener;
import io.github.surezzzzzz.sdk.audit.crm.test.CrmAuditListenerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRM 审计监听器无处理器装配测试：宿主未提供 CrmAuditHandler 时
 * 不得注册监听器，事件发布无副作用。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = CrmAuditListenerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CrmAuditListenerNoHandlerTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldNotRegisterListenerWithoutHandler() {
        log.info("验证无处理器时监听器和默认日志处理器均不装配");
        assertTrue(applicationContext.getBeansOfType(CrmAuditEventListener.class).isEmpty(),
                "无处理器时不得注册审计监听器");
        assertTrue(applicationContext.getBeansOfType(LogCrmAuditHandler.class).isEmpty(),
                "默认日志处理器保持关闭");
    }
}
