package io.github.surezzzzzz.sdk.limiter.redis.smart.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

/**
 * 智能限流器测试应用
 *
 * @author Sure.
 * @Date: 2024/12/XX XX:XX
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        SecurityAutoConfiguration.class
})
public class SmartRedisLimiterTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartRedisLimiterTestApplication.class, args);
    }
}
