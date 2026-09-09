package io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Simple IAM OIDC Adapter Test Application
 *
 * <p>复用 iam-server-starter 测试环境（MySQL / Redis / 密钥），叠加 OIDC 适配器自动装配。
 *
 * @author surezzzzzz
 */
@SpringBootApplication(scanBasePackages = "io.github.surezzzzzz.sdk.auth.iam.server")
public class SimpleIamOidcAdapterTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleIamOidcAdapterTestApplication.class, args);
    }
}
