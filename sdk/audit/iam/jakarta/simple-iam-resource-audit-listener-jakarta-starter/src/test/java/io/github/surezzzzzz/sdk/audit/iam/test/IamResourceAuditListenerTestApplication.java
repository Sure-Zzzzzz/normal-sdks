package io.github.surezzzzzz.sdk.audit.iam.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * IAM 资源审计监听器测试应用（jakarta 线）。
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class IamResourceAuditListenerTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(IamResourceAuditListenerTestApplication.class, args);
    }
}
