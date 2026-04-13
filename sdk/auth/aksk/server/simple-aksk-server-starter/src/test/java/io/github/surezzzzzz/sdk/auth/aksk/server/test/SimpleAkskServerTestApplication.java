package io.github.surezzzzzz.sdk.auth.aksk.server.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Simple AKSK Server Test Application
 *
 * @author surezzzzzz
 */
@SpringBootApplication(scanBasePackages = "io.github.surezzzzzz.sdk.auth.aksk.server")
public class SimpleAkskServerTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleAkskServerTestApplication.class, args);
    }
}
