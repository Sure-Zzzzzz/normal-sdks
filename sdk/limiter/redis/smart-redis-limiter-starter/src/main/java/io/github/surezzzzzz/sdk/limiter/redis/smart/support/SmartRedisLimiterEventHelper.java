package io.github.surezzzzzz.sdk.limiter.redis.smart.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterKeyStrategy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRedisKeyConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.generator.SmartRedisLimiterKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.List;

/**
 * 限流事件辅助工具类
 * 提供 Interceptor 和 Aspect 共用的 limitKey 构建和规则序列化逻辑
 *
 * @author Sure.
 * @Date: 2026-05-09
 */
public class SmartRedisLimiterEventHelper {

    private static final Logger log = LoggerFactory.getLogger(SmartRedisLimiterEventHelper.class);

    private SmartRedisLimiterEventHelper() {
    }

    /**
     * 构建限流 Key（用于审计事件）
     */
    public static String buildLimitKey(SmartRedisLimiterContext context,
                                       String keyStrategy,
                                       String me,
                                       ApplicationContext applicationContext) {
        try {
            String beanName = SmartRedisLimiterKeyStrategy.getBeanName(keyStrategy);
            SmartRedisLimiterKeyGenerator generator =
                    applicationContext.getBean(beanName, SmartRedisLimiterKeyGenerator.class);
            String keyPart = generator.generate(context);
            return SmartRedisLimiterRedisKeyConstant.KEY_PREFIX
                    + me
                    + SmartRedisLimiterRedisKeyConstant.KEY_SEPARATOR
                    + keyPart;
        } catch (Exception e) {
            log.warn("构建限流Key失败, keyStrategy={}", keyStrategy, e);
            return null;
        }
    }

    /**
     * 将限流规则列表序列化为字符串（用于审计事件）
     */
    public static String serializeLimitRules(List<SmartRedisLimiterProperties.SmartLimitRule> limitRules) {
        if (limitRules == null || limitRules.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limitRules.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            SmartRedisLimiterProperties.SmartLimitRule rule = limitRules.get(i);
            sb.append(rule.getCount())
                    .append("/")
                    .append(rule.getWindow())
                    .append(rule.getUnit().name().toLowerCase());
        }
        return sb.toString();
    }
}
