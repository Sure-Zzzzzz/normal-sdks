package io.github.surezzzzzz.sdk.b2m.sms.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 测试启动类（手跑联调用例宿主，自动配置由 spring.factories 装配）。
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class SmsTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmsTestApplication.class, args);
    }
}
