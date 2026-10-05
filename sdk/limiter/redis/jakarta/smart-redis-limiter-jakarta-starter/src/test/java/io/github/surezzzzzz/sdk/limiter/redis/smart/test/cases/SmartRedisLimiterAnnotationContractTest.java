package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimitRule;
import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiter;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SmartRedisLimiter 注解公共契约单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterAnnotationContractTest {

    /**
     * 验证资源编码保持可选且默认为空字符串
     *
     * @throws NoSuchMethodException 注解方法不存在时抛出
     */
    @Test
    public void testResourceCodeDefault() throws NoSuchMethodException {
        Method method = SmartRedisLimiter.class.getDeclaredMethod("resourceCode");

        log.info("resourceCode 默认值: {}", method.getDefaultValue());
        assertEquals(SmartRedisLimiterStarterConstant.DEFAULT_RESOURCE_CODE,
                method.getDefaultValue(), "resourceCode 默认值应为空字符串");
    }

    /**
     * 验证注解限额字段升级为 long
     *
     * @throws NoSuchMethodException 注解方法不存在时抛出
     */
    @Test
    public void testLimitRuleUsesLong() throws NoSuchMethodException {
        Class<?> countType = SmartRedisLimitRule.class.getDeclaredMethod("count").getReturnType();
        Class<?> windowType = SmartRedisLimitRule.class.getDeclaredMethod("window").getReturnType();

        log.info("注解字段类型: count={}, window={}", countType, windowType);
        assertEquals(Long.TYPE, countType, "count 应使用 long");
        assertEquals(Long.TYPE, windowType, "window 应使用 long");
    }
}
