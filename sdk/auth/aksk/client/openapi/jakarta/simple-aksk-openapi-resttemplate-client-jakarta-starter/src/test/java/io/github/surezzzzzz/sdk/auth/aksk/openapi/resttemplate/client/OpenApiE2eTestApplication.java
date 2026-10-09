package io.github.surezzzzzz.sdk.auth.aksk.openapi.resttemplate.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * E2E 测试宿主：不重复装配客户端 Bean——由 starter 自动配置注册 akskOpenApiClient，
 * 认证 RestTemplate 取底座 akskClientRestTemplate（真实令牌链）；底座缺失时启动即响亮失败。
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class OpenApiE2eTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenApiE2eTestApplication.class, args);
    }
}
