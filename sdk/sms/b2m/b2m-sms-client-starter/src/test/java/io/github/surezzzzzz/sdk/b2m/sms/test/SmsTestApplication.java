package io.github.surezzzzzz.sdk.b2m.sms.test;

import io.github.surezzzzzz.sdk.b2m.sms.configuration.SmsComponent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(includeFilters = @ComponentScan.Filter(SmsComponent.class))
public class SmsTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmsTestApplication.class, args);
    }
}
