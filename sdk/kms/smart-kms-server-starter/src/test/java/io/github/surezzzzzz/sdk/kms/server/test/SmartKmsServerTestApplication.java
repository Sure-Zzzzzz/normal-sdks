package io.github.surezzzzzz.sdk.kms.server.test;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.configuration.SimpleAkskResourceServerAutoConfiguration;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataPermissionRequest;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support.DataPermissionFacade;
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
import java.util.Collections;
import java.util.HashSet;

/**
 * Smart KMS Server 测试应用。
 *
 * <p>排除 AKSK 资源服务自动配置：其内省配置缺失时 fail-fast，而本应用验证的是自带
 * resolver 的宿主路径（桥让位），不组合公共层认证。</p>
 *
 * @author surezzzzzz
 */
@SpringBootApplication(exclude = {SecurityAutoConfiguration.class, SimpleAkskResourceServerAutoConfiguration.class})
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
                String ownerPrincipalId = request.getHeader("X-Test-Owner-Principal");
                String principalId = request.getHeader("X-Test-Principal");
                String requestId = request.getHeader("X-Test-Request-Id");
                if (ownerPrincipalId == null || principalId == null || requestId == null) {
                    return null;
                }
                return new KmsRequestContext(new KmsPrincipal(principalId, ownerPrincipalId,
                        new HashSet<String>(Arrays.asList(SmartKmsServerConstant.API_PERMISSION_ME_READ,
                                SmartKmsServerConstant.API_PERMISSION_KEY_READ,
                                SmartKmsServerConstant.SCOPE_MANAGE,
                                SmartKmsServerConstant.API_PERMISSION_KEY_POLICY,
                                SmartKmsServerConstant.API_PERMISSION_KEY_DESTROY,
                                SmartKmsServerConstant.SCOPE_SIGN, SmartKmsServerConstant.SCOPE_VERIFY,
                                SmartKmsServerConstant.SCOPE_ENCRYPT, SmartKmsServerConstant.SCOPE_DECRYPT,
                                SmartKmsServerConstant.SCOPE_READ_PUBLIC_KEY))), requestId);
            }
        };
    }

    /**
     * 宿主 resolver 测试不接入资源层授权快照，显式提供全量计划以只验证 KMS HTTP 契约。
     */
    @Bean
    public DataPermissionFacade dataPermissionFacade() {
        return (resource, action) -> DataAccessPlan.evaluate(new DataGrantDocument(
                SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                Collections.singletonList(new DataGrant(resource, Collections.singletonList(action), true,
                        Collections.emptyList()))), new DataPermissionRequest(resource, action));
    }
}
