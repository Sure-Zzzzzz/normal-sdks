package io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Console 专属入口装配条件，不以关闭 UI 推导 Portal。
 */
public class SmartRedisLimiterManagementConsoleCondition implements Condition {
    /**
     * 按显式模式判断是否保留历史入口。
     */
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return SmartRedisLimiterManagementConstant.MODE_CONSOLE.equals(context.getEnvironment().getProperty(
                SmartRedisLimiterManagementConstant.CONFIG_PREFIX + ".mode",
                SmartRedisLimiterManagementConstant.MODE_CONSOLE));
    }
}
