package io.github.surezzzzzz.sdk.redis.route.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RedisRouteMatrixExpectationProperties.class)
public class SimpleRedisRouteTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimpleRedisRouteTestApplication.class, args);
    }
}
