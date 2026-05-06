package io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Simple AKSK Feign Redis Client Test Application
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@SpringBootApplication
@EnableFeignClients
public class SimpleAkskFeignRedisClientTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleAkskFeignRedisClientTestApplication.class, args);
    }
}
