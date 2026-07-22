package io.github.surezzzzzz.sdk.limiter.redis.smart.management.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.annotation.RequireExpression;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.aspect.SimpleAkskSecurityAspect;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.exception.SimpleAkskSecurityException;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.provider.SimpleAkskSecurityContextProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 策略 scope 表达式行为测试
 *
 * @author surezzzzzz
 */
@SpringJUnitConfig(SmartRedisLimiterPolicyScopeExpressionTest.TestConfiguration.class)
public class SmartRedisLimiterPolicyScopeExpressionTest {

    @javax.annotation.Resource
    private PolicyScopeEndpoint endpoint;
    @javax.annotation.Resource
    private TestSecurityContextProvider contextProvider;

    @Test
    public void testReadScopeOnlyAllowsSnapshot() {
        contextProvider.setScope(SmartRedisLimiterManagementConstant.POLICY_READ_SCOPE);
        assertDoesNotThrow(() -> endpoint.snapshot());
        assertThrows(SimpleAkskSecurityException.class, () -> endpoint.write());
    }

    @Test
    public void testWriteScopeOnlyAllowsCrud() {
        contextProvider.setScope(SmartRedisLimiterManagementConstant.POLICY_WRITE_SCOPE);
        assertThrows(SimpleAkskSecurityException.class, () -> endpoint.snapshot());
        assertDoesNotThrow(() -> endpoint.write());
    }

    @Test
    public void testMultiScopeAndNearMatchScopesKeepExactBoundaries() {
        contextProvider.setScope("other " + SmartRedisLimiterManagementConstant.POLICY_READ_SCOPE + " more");
        assertDoesNotThrow(() -> endpoint.snapshot());
        assertThrows(SimpleAkskSecurityException.class, () -> endpoint.write());

        contextProvider.setScope(SmartRedisLimiterManagementConstant.POLICY_READ_SCOPE + "-extra");
        assertThrows(SimpleAkskSecurityException.class, () -> endpoint.snapshot());
        assertThrows(SimpleAkskSecurityException.class, () -> endpoint.write());
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfiguration {

        @Bean
        public TestSecurityContextProvider testSecurityContextProvider() {
            return new TestSecurityContextProvider();
        }

        @Bean
        public SimpleAkskSecurityAspect simpleAkskSecurityAspect(TestSecurityContextProvider contextProvider) {
            return new SimpleAkskSecurityAspect(contextProvider);
        }

        @Bean
        public PolicyScopeEndpoint policyScopeEndpoint() {
            return new PolicyScopeEndpoint();
        }
    }

    static class TestSecurityContextProvider implements SimpleAkskSecurityContextProvider {

        private Map<String, String> context = Collections.emptyMap();

        private void setScope(String scope) {
            Map<String, String> values = new HashMap<>();
            values.put("scope", scope);
            context = values;
        }

        @Override
        public Map<String, String> getAll() {
            return context;
        }

        @Override
        public String get(String key) {
            return context.get(key);
        }
    }

    static class PolicyScopeEndpoint {

        @RequireExpression(SmartRedisLimiterManagementConstant.POLICY_READ_EXPRESSION)
        public void snapshot() {
        }

        @RequireExpression(SmartRedisLimiterManagementConstant.POLICY_WRITE_EXPRESSION)
        public void write() {
        }
    }
}
