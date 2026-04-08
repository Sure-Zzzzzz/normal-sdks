package io.github.surezzzzzz.sdk.auth.aksk.client.demo.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Simple AKSK Client Demo Test Application
 *
 * @author surezzzzzz
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "io.github.surezzzzzz.sdk.auth.aksk.client.demo.test.client")
public class SimpleAkskClientDemoTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleAkskClientDemoTestApplication.class, args);
    }
}
