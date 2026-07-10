package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ES Persistence 审计监听器测试应用
 *
 * @author surezzzzzz
 */
@SpringBootApplication(scanBasePackages = {
        "io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.test",
        "io.github.surezzzzzz.sdk.audit.persistence.elasticsearch",
        "io.github.surezzzzzz.sdk.elasticsearch.persistence"
})
public class EsPersistenceAuditListenerTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(EsPersistenceAuditListenerTestApplication.class, args);
    }
}
