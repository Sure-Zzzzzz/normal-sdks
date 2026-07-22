package io.github.surezzzzzz.sdk.limiter.redis.smart.management.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * SmartRedisLimiter Management 测试应用
 *
 * @author surezzzzzz
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class SmartRedisLimiterManagementTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartRedisLimiterManagementTestApplication.class, args);
    }
}
