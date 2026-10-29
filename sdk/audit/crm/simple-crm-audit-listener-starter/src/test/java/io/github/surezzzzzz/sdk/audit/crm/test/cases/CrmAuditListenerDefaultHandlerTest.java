package io.github.surezzzzzz.sdk.audit.crm.test.cases;

import io.github.surezzzzzz.sdk.audit.crm.handler.impl.LogCrmAuditHandler;
import io.github.surezzzzzz.sdk.audit.crm.listener.CrmAuditEventListener;
import io.github.surezzzzzz.sdk.audit.crm.test.CrmAuditListenerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 默认日志处理器装配测试。
 *
 * <p>验证自动配置自身的组件扫描不会在条件判断前被跳过，显式开启后
 * 默认日志处理器和事件监听器必须同时注册。</p>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(
        classes = CrmAuditListenerTestApplication.class,
        properties = "io.github.surezzzzzz.sdk.audit.crm.listener.handler.log.enabled=true",
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CrmAuditListenerDefaultHandlerTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldRegisterDefaultLogHandlerWhenEnabled() {
        LogCrmAuditHandler handler = applicationContext.getBean(LogCrmAuditHandler.class);
        CrmAuditEventListener listener = applicationContext.getBean(CrmAuditEventListener.class);
        log.info("验证默认日志审计处理器与监听器均已装配：handler={}，listener={}",
                handler.getClass().getName(), listener.getClass().getName());
        assertNotNull(handler, "显式开启时默认日志处理器必须注册");
        assertNotNull(listener, "存在默认日志处理器时事件监听器必须注册");
    }
}
