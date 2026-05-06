package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.configuration.SimpleAkskRedisTokenManagerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Test Application
 *
 * @author surezzzzzz
 */
@SpringBootApplication
@EnableConfigurationProperties({
        SimpleAkskClientCoreProperties.class,
        SimpleAkskRedisTokenManagerProperties.class
})
public class SimpleAkskRedisTokenManagerTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimpleAkskRedisTokenManagerTestApplication.class, args);
    }
}
