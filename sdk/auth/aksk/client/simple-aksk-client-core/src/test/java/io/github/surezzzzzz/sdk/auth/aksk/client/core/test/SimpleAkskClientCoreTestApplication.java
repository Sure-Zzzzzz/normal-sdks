package io.github.surezzzzzz.sdk.auth.aksk.client.core.test;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Test Application
 *
 * @author surezzzzzz
 */
@SpringBootApplication
@EnableConfigurationProperties(SimpleAkskClientCoreProperties.class)
public class SimpleAkskClientCoreTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimpleAkskClientCoreTestApplication.class, args);
    }
}
