package io.github.surezzzzzz.sdk.limiter.redis.smart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author: Sure.
 * @description 智能限流器测试应用
 * @Date: 2024/12/XX XX:XX
 */
@SpringBootApplication
public class SmartRedisLimiterApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartRedisLimiterApplication.class, args);
    }
}