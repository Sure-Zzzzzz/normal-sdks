package io.github.surezzzzzz.sdk.auth.aksk.feign.httpsession.client.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Simple AKSK Feign HttpSession Client Test Application
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@SpringBootApplication
@EnableFeignClients
public class SimpleAkskFeignHttpSessionClientTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleAkskFeignHttpSessionClientTestApplication.class, args);
    }
}
