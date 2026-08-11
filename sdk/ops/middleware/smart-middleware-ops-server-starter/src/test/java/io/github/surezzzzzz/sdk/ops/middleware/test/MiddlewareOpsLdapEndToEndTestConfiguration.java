package io.github.surezzzzzz.sdk.ops.middleware.test;

import io.github.surezzzzzz.sdk.ops.middleware.authentication.MiddlewareOpsIdentity;
import io.github.surezzzzzz.sdk.ops.middleware.authentication.MiddlewareOpsIdentityResolver;
import io.github.surezzzzzz.sdk.ops.middleware.authentication.SpringSecurityMiddlewareOpsIdentityResolver;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * LDAP 端到端测试专用认证 Bean。
 *
 * @author surezzzzzz
 */
@TestConfiguration
public class MiddlewareOpsLdapEndToEndTestConfiguration {

    /**
     * 注册记录真实 Ops 身份解析结果的测试替身。
     */
    @Bean
    @Primary
    public RecordingIdentityResolver middlewareOpsIdentityResolver() {
        return new RecordingIdentityResolver();
    }


    /**
     * 记录 Ops 身份解析器的真实输出。
     */
    public static class RecordingIdentityResolver implements MiddlewareOpsIdentityResolver {

        private final SpringSecurityMiddlewareOpsIdentityResolver delegate = new SpringSecurityMiddlewareOpsIdentityResolver();
        private volatile MiddlewareOpsIdentity identity;

        @Override
        public MiddlewareOpsIdentity resolve() {
            identity = delegate.resolve();
            return identity;
        }

        public MiddlewareOpsIdentity getIdentity() {
            return identity;
        }
    }
}
