package io.github.surezzzzzz.sdk.auth.iam.adapter.captcha.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Simple IAM Captcha Adapter Test Application
 *
 * <p>叠加 captcha 适配器与通用 captcha 模块自动装配（真实 Redis fixture）。
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class SimpleIamCaptchaAdapterTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleIamCaptchaAdapterTestApplication.class, args);
    }
}
