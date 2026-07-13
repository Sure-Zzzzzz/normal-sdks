package io.github.surezzzzzz.sdk.lock.redis.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Redis 分布式锁测试应用启动类
 *
 * @author surezzzzzz
 */
@SpringBootApplication
@EnableConfigurationProperties(RedisLockRouteMatrixExpectationProperties.class)
public class SimpleRedisLockTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimpleRedisLockTestApplication.class, args);
    }
}