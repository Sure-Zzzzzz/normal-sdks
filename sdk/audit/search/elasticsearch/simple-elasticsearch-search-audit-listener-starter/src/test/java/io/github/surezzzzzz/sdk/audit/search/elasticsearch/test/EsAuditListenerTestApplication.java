package io.github.surezzzzzz.sdk.audit.search.elasticsearch.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ES 审计监听器测试应用
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {
        "io.github.surezzzzzz.sdk.audit.search.elasticsearch.test",
        "io.github.surezzzzzz.sdk.audit.search.elasticsearch"
})
public class EsAuditListenerTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(EsAuditListenerTestApplication.class, args);
    }
}
