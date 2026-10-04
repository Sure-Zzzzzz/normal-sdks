package io.github.surezzzzzz.sdk.kms.resttemplate.client.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * KMS Client RestTemplate 测试启动类（桩出底座 akskClientRestTemplate 触发自动装配）。
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class SimpleKmsClientRestTemplateTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleKmsClientRestTemplateTestApplication.class, args);
    }

    /**
     * 测试桩：与底座预配置 RestTemplate 同名的传输 Bean，真实认证由底座自动配置负责。
     */
    @Bean
    public RestTemplate akskClientRestTemplate() {
        return new RestTemplate();
    }
}
