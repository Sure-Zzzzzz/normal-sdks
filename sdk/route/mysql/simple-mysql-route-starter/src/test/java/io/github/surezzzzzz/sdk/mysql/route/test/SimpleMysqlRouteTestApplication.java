package io.github.surezzzzzz.sdk.mysql.route.test;

import io.github.surezzzzzz.sdk.mysql.route.credential.MySqlRouteCredentialResolver;
import io.github.surezzzzzz.sdk.mysql.route.model.MySqlRouteCredential;
import io.github.surezzzzzz.sdk.mysql.route.support.MySqlRouteStringHelper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * MySQL Route 测试应用。
 *
 * @author surezzzzzz
 */
@SpringBootApplication
public class SimpleMysqlRouteTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleMysqlRouteTestApplication.class, args);
    }

    @Bean
    public MySqlRouteCredentialResolver mySqlRouteCredentialResolver(
            org.springframework.core.env.Environment environment) {
        return credentialRef -> {
            if (!"test-mysql57-reader".equals(credentialRef) && !"test-mysql84-reader".equals(credentialRef)) {
                throw new IllegalArgumentException("未知 MySQL Route 测试凭据引用");
            }
            String username = environment.getRequiredProperty("test.mysql.username");
            String password = environment.getRequiredProperty("test.mysql.password");
            if (!MySqlRouteStringHelper.hasText(username) || !MySqlRouteStringHelper.hasText(password)) {
                throw new IllegalStateException("MySQL Route 端到端测试凭据不能为空");
            }
            return new MySqlRouteCredential(username, password);
        };
    }
}
