package io.github.surezzzzzz.sdk.lock.redis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * @author: Sure.
 * @description: Redis锁测试应用启动类
 * @Date: 2024/12/3 14:00
 */
@SpringBootApplication
@EnableConfigurationProperties(RedisLockRouteMatrixExpectationProperties.class)
public class LockApplication {
    public static void main(String[] args) {
        SpringApplication.run(LockApplication.class, args);
    }
}