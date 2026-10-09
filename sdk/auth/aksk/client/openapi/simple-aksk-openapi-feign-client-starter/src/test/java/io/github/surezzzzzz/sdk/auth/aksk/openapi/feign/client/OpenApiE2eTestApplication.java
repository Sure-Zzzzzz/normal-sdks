package io.github.surezzzzzz.sdk.auth.aksk.openapi.feign.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * E2E 测试宿主：注册管理 OpenAPI Feign 客户端，Authorization 头由底座令牌链自动注入；
 * 底座凭据缺失时启动即响亮失败。
 *
 * @author surezzzzzz
 */
@SpringBootApplication
@EnableFeignClients(clients = AkskOpenApiFeignClient.class)
public class OpenApiE2eTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenApiE2eTestApplication.class, args);
    }
}
