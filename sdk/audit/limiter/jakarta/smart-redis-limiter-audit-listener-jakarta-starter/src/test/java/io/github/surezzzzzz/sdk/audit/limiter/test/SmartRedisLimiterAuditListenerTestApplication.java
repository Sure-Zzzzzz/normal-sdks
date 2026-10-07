package io.github.surezzzzzz.sdk.audit.limiter.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SmartRedisLimiter 审计监听器测试应用
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class SmartRedisLimiterAuditListenerTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartRedisLimiterAuditListenerTestApplication.class, args);
    }
}
