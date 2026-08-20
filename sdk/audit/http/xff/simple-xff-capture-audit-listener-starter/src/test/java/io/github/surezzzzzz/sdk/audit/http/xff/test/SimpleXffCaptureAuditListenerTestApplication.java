package io.github.surezzzzzz.sdk.audit.http.xff.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Simple XFF Capture Audit Listener 测试启动类。
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class SimpleXffCaptureAuditListenerTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleXffCaptureAuditListenerTestApplication.class, args);
    }
}
