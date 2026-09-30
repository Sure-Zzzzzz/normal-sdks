package io.github.surezzzzzz.sdk.audit.iam.test.cases;

import io.github.surezzzzzz.sdk.audit.iam.resource.handler.IamResourceAuditHandler;
import io.github.surezzzzzz.sdk.audit.iam.resource.listener.IamResourceAuditEventListener;
import io.github.surezzzzzz.sdk.audit.iam.resource.model.IamResourceAuditRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 总开关 enable 的装配反证：enable=false 时监听器与 Handler 全部不装配。
 */
@SpringBootTest(classes = IamResourceAuditListenerEnableSwitchTest.DisabledConfiguration.class,
        properties = "io.github.surezzzzzz.sdk.audit.iam.resource.enable=false")
class IamResourceAuditListenerEnableSwitchTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldNotRegisterAnyAuditComponentWhenDisabled() {
        // 业务自注册的 handler Bean 不受开关影响（计数 1 来自本配置类的 businessHandler）；
        // 开关挡的是模块自带组件：默认日志 Handler 与监听器
        assertEquals(1, applicationContext.getBeansOfType(IamResourceAuditHandler.class).size(),
                "仅业务自有 handler，模块默认 LogHandler 不应装配");
        assertEquals(0, applicationContext.getBeansOfType(
                        io.github.surezzzzzz.sdk.audit.iam.resource.handler.impl.LogIamResourceAuditHandler.class).size(),
                "默认日志 Handler 不应装配");
        assertEquals(0, applicationContext.getBeansOfType(IamResourceAuditEventListener.class).size(),
                "审计监听器不应装配");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class DisabledConfiguration {

        @Bean
        IamResourceAuditHandler businessHandler() {
            return new IamResourceAuditHandler() {
                @Override
                public void handle(IamResourceAuditRecord record) {
                    // 空实现：本用例只断言开关关闭后全组件不装配，业务 Bean 存在与否不影响该结论
                }
            };
        }
    }
}
