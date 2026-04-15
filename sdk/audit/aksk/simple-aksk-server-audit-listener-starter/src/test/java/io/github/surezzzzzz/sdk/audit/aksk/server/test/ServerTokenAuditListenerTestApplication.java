package io.github.surezzzzzz.sdk.audit.aksk.server.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Server Token 审计监听器测试应用
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@SpringBootApplication
public class ServerTokenAuditListenerTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerTokenAuditListenerTestApplication.class, args);
    }
}
