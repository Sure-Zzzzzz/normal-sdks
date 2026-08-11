package io.github.surezzzzzz.sdk.ops.middleware.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Middleware Ops Server 测试应用。
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class SmartMiddlewareOpsServerTestApplication {

    /**
     * 启动本地固定端到端测试应用。
     */
    public static void main(String[] args) {
        SpringApplication.run(SmartMiddlewareOpsServerTestApplication.class, args);
    }
}
