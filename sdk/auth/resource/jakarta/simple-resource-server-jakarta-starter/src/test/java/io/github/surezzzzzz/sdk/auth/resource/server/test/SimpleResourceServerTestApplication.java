package io.github.surezzzzzz.sdk.auth.resource.server.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 通用资源服务Starter测试应用。
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class SimpleResourceServerTestApplication {

    /**
     * 启动测试应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SimpleResourceServerTestApplication.class, args);
    }
}
