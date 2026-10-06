package io.github.surezzzzzz.sdk.audit.aksk.test;

import io.github.surezzzzzz.sdk.audit.aksk.test.config.StubAkskIntrospectorConfiguration;
import io.github.surezzzzzz.sdk.audit.aksk.test.controller.TestResourceController;
import io.github.surezzzzzz.sdk.audit.aksk.test.support.AkskAuditTestSupport.AsyncConfiguration;
import io.github.surezzzzzz.sdk.audit.aksk.test.support.AkskAuditTestSupport.HandlerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 正式 Jakarta 资源组件与审计自动配置的测试宿主，不手工导入监听器。
 *
 * @author surezzzzzz
 */
@SpringBootApplication(scanBasePackageClasses = TestResourceController.class)
@Import({AsyncConfiguration.class, HandlerConfiguration.class, StubAkskIntrospectorConfiguration.class})
public class AkskAuditListenerTestApplication {
    /**
     * 启动测试宿主。
     */
    public static void main(String[] args) {
        SpringApplication.run(AkskAuditListenerTestApplication.class, args);
    }
}
