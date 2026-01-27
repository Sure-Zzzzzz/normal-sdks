package io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Simple AKSK Security Context 测试应用
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@SpringBootApplication
@EnableAspectJAutoProxy
public class SimpleAkskSecurityContextTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleAkskSecurityContextTestApplication.class, args);
    }
}
