package io.github.surezzzzzz.sdk.audit.crm.test;

import io.github.surezzzzzz.sdk.audit.crm.configuration.SimpleCrmAuditListenerAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * CRM 审计监听器测试应用
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(SimpleCrmAuditListenerAutoConfiguration.class)
public class CrmAuditListenerTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmAuditListenerTestApplication.class, args);
    }
}
