package io.github.surezzzzzz.sdk.audit.iam.server.test;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Simple IAM Audit Listener Test Application
 *
 * <p>集成测试宿主：IAM server 经 spring.factories 自动配置完整装配，
 * 本模块测试组件（TestServerIamAuditHandler）由默认组件扫描注册。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootApplication
public class SimpleIamServerAuditListenerTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleIamServerAuditListenerTestApplication.class, args);
    }
}
