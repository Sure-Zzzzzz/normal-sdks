package io.github.surezzzzzz.sdk.redis.route.test;

import io.github.surezzzzzz.sdk.redis.route.factory.RedisConnectionFactoryFactory;
import io.github.surezzzzzz.sdk.redis.route.test.factory.TopologyDnsRedisConnectionFactoryFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SimpleRedisRouteTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleRedisRouteTestApplication.class, args);
    }

    @Bean
    RedisConnectionFactoryFactory redisConnectionFactoryFactory() {
        return new TopologyDnsRedisConnectionFactoryFactory();
    }
}
