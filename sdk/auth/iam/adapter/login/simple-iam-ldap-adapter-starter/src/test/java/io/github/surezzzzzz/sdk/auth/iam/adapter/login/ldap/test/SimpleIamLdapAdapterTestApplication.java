package io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Simple IAM LDAP Adapter Test Application
 *
 * <p>复用 iam-server-starter 测试环境（MySQL / Redis / 密钥），叠加 LDAP 适配器自动装配。
 *
 * @author surezzzzzz
 */
@SpringBootApplication(scanBasePackages = "io.github.surezzzzzz.sdk.auth.iam.server")
public class SimpleIamLdapAdapterTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleIamLdapAdapterTestApplication.class, args);
    }
}
