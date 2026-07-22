package io.github.surezzzzzz.sdk.limiter.redis.smart.management.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.annotation.RequireExpression;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.SmartRedisLimiterPolicyController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 对外策略 API 鉴权表达式契约测试
 *
 * @author surezzzzzz
 */
public class SmartRedisLimiterPolicyAuthorizationTest {

    @Test
    public void testSnapshotRequiresReadScopeAndCrudRequiresWriteScope() throws Exception {
        assertExpression("getSnapshot", SmartRedisLimiterManagementConstant.POLICY_READ_EXPRESSION,
                String.class, String.class);
        assertExpression("create", SmartRedisLimiterManagementConstant.POLICY_WRITE_EXPRESSION,
                io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyCreateRequest.class);
        assertExpression("get", SmartRedisLimiterManagementConstant.POLICY_WRITE_EXPRESSION, long.class);
        assertExpression("query", SmartRedisLimiterManagementConstant.POLICY_WRITE_EXPRESSION,
                String.class, String.class, String.class, Boolean.class, Integer.class, Integer.class);
        assertExpression("update", SmartRedisLimiterManagementConstant.POLICY_WRITE_EXPRESSION,
                long.class, io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyUpdateRequest.class);
        assertExpression("updateState", SmartRedisLimiterManagementConstant.POLICY_WRITE_EXPRESSION,
                long.class, io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyStateRequest.class);
        assertExpression("delete", SmartRedisLimiterManagementConstant.POLICY_WRITE_EXPRESSION,
                long.class, long.class);
    }

    private void assertExpression(String methodName, String expectedExpression, Class<?>... parameterTypes)
            throws Exception {
        RequireExpression annotation = SmartRedisLimiterPolicyController.class
                .getMethod(methodName, parameterTypes)
                .getAnnotation(RequireExpression.class);
        assertEquals(expectedExpression, annotation.value(), methodName + " 必须声明正确 scope 表达式");
    }
}
