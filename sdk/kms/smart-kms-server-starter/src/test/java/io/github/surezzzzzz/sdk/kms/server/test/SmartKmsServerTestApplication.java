package io.github.surezzzzzz.sdk.kms.server.test;

import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalResolver;
import io.github.surezzzzzz.sdk.kms.server.service.KmsRequestContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Smart KMS Server 测试应用。
 *
 * @author surezzzzzz
 */
@SpringBootApplication(exclude = SecurityAutoConfiguration.class)
public class SmartKmsServerTestApplication {

    /**
     * 启动测试应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SmartKmsServerTestApplication.class, args);
    }

    /**
     * 注册仅用于 HTTP 集成测试的已认证主体解析器。
     *
     * @return 测试认证主体解析器
     */
    @Bean
    public KmsPrincipalResolver kmsPrincipalResolver() {
        return new KmsPrincipalResolver() {
            @Override
            public KmsRequestContext resolve(HttpServletRequest request) {
                String tenantId = request.getHeader("X-Test-Tenant");
                String principalId = request.getHeader("X-Test-Principal");
                String requestId = request.getHeader("X-Test-Request-Id");
                if (tenantId == null || principalId == null || requestId == null) {
                    return null;
                }
                return new KmsRequestContext(new KmsPrincipal(principalId, tenantId,
                        new HashSet<String>(Arrays.asList(SmartKmsServerConstant.SCOPE_MANAGE,
                                SmartKmsServerConstant.SCOPE_SIGN, SmartKmsServerConstant.SCOPE_VERIFY,
                                SmartKmsServerConstant.SCOPE_ENCRYPT, SmartKmsServerConstant.SCOPE_DECRYPT,
                                SmartKmsServerConstant.SCOPE_READ_PUBLIC_KEY))), requestId);
            }
        };
    }
}
