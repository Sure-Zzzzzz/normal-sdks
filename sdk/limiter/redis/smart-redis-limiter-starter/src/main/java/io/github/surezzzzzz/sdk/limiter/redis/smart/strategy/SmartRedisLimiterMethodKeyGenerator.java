package io.github.surezzzzzz.sdk.limiter.redis.smart.strategy;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterKeyStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.reflect.Method;

/**
 * @author: Sure.
 * @description 方法级别Key生成器
 * @Date: 2024/12/XX XX:XX
 */
@SmartRedisLimiterComponent
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart", name = "enable", havingValue = "true")
public class SmartRedisLimiterMethodKeyGenerator implements SmartRedisLimiterKeyGenerator {

    @Override
    public String generate(SmartRedisLimiterContext context) {
        Method method = context.getMethod();
        if (method == null) {
            throw new IllegalArgumentException("Method不能为null");
        }

        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();

        // 从枚举获取前缀
        String prefix = SmartRedisLimiterKeyStrategy.METHOD.getCode();
        return prefix + ":" + className + "." + methodName;
    }
}
