package io.github.surezzzzzz.sdk.limiter.redis.smart.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterContextAttribute;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterEventPayload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 限流事件辅助工具类
 * 提供 Interceptor 和 Aspect 共用的事件载荷构建和规则序列化逻辑
 *
 * @author Sure.
 * @Date: 2026-05-09
 */
public class SmartRedisLimiterEventHelper {

    private SmartRedisLimiterEventHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 构建限流事件载荷
     *
     * @param context     限流上下文
     * @param limitRules  限流规则列表
     * @param keyStrategy Key策略
     * @param algorithm   算法
     * @param result      限流结果
     * @param sourceType  来源类型
     * @return 事件载荷
     */
    public static SmartRedisLimiterEventPayload buildEventPayload(SmartRedisLimiterContext context,
                                                                  List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                                                                  String keyStrategy,
                                                                  String algorithm,
                                                                  SmartRedisLimiterResult result,
                                                                  String sourceType) {
        return buildEventPayload(context, limitRules, keyStrategy, algorithm, result, sourceType,
                null, SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL, null);
    }

    /**
     * 构建携带动态策略元数据的限流事件载荷
     *
     * @param context        限流上下文
     * @param limitRules     最终限流规则列表
     * @param keyStrategy    Key 策略
     * @param algorithm      算法
     * @param result         限流结果
     * @param sourceType     来源类型
     * @param resourceCode   稳定资源编码
     * @param policySource   策略来源
     * @param policyRevision 远程策略版本
     * @return 事件载荷
     */
    public static SmartRedisLimiterEventPayload buildEventPayload(SmartRedisLimiterContext context,
                                                                  List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                                                                  String keyStrategy,
                                                                  String algorithm,
                                                                  SmartRedisLimiterResult result,
                                                                  String sourceType,
                                                                  String resourceCode,
                                                                  String policySource,
                                                                  Long policyRevision) {
        String routeKey = firstNonNull(result.getRouteKey(),
                context.getAttribute(SmartRedisLimiterContextAttribute.ROUTE_KEY));
        String limitKey = routeKey;
        Long durationNanos = context.getAttribute(SmartRedisLimiterContextAttribute.DURATION_NANOS);
        String datasourceKey = firstNonNull(result.getDatasourceKey(),
                context.getAttribute(SmartRedisLimiterContextAttribute.DATASOURCE_KEY));
        String redisMode = firstNonNull(result.getRedisMode(),
                context.getAttribute(SmartRedisLimiterContextAttribute.REDIS_MODE));
        String fallbackReason = firstNonNull(result.getFallbackReason(),
                context.getAttribute(SmartRedisLimiterContextAttribute.FALLBACK_REASON));
        if (result.isFallback() && fallbackReason == null) {
            fallbackReason = SmartRedisLimiterConstant.FALLBACK_REASON_UNKNOWN;
        }
        boolean routeRequired = result.isRouteRequired()
                || Boolean.TRUE.equals(context.getAttribute(SmartRedisLimiterContextAttribute.ROUTE_REQUIRED));
        boolean routeResolved = result.isRouteResolved()
                || Boolean.TRUE.equals(context.getAttribute(SmartRedisLimiterContextAttribute.ROUTE_RESOLVED));

        return SmartRedisLimiterEventPayload.builder()
                .limitKey(limitKey)
                .routeKey(routeKey)
                .datasourceKey(datasourceKey)
                .redisMode(redisMode == null ? SmartRedisLimiterConstant.REDIS_MODE_UNKNOWN : redisMode)
                .routeRequired(routeRequired)
                .routeResolved(routeResolved)
                .keyStrategy(keyStrategy)
                .algorithm(algorithm)
                .limitRules(serializeLimitRules(limitRules))
                .passed(result.isPassed())
                .sourceType(sourceType)
                .requestUri(context.getRequestPath())
                .httpMethod(context.getRequestMethod())
                .clientIp(context.getClientIp())
                .matchedPathPattern(context.getMatchedPathPattern())
                .methodName(context.getMethod() == null ? null : context.getMethod().getName())
                .methodQualifiedName(context.getMethod() == null ? null : context.getMethod().toGenericString())
                .attributes(buildEventAttributes(context))
                .limit(result.getLimit())
                .remaining(result.getRemaining())
                .resetAt(result.getResetAt())
                .durationNanos(durationNanos == null ? 0L : durationNanos)
                .fallbackReason(fallbackReason)
                .resourceCode(resourceCode == null || resourceCode.isEmpty() ? null : resourceCode)
                .policySource(policySource)
                .policyRevision(policyRevision)
                .build();
    }

    private static Map<String, Object> buildEventAttributes(SmartRedisLimiterContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : context.getAttributes().entrySet()) {
            if (SmartRedisLimiterContextAttribute.fromCode(entry.getKey()) == null) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static <T> T firstNonNull(T primary, T fallback) {
        return primary == null ? fallback : primary;
    }

    /**
     * 将限流规则列表序列化为字符串（用于事件发布）
     *
     * @param limitRules 限流规则列表
     * @return 序列化后的字符串
     */
    public static String serializeLimitRules(List<SmartRedisLimiterProperties.SmartLimitRule> limitRules) {
        if (limitRules == null || limitRules.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limitRules.size(); i++) {
            if (i > 0) {
                sb.append(SmartRedisLimiterConstant.RULE_SEPARATOR);
            }
            SmartRedisLimiterProperties.SmartLimitRule rule = limitRules.get(i);
            sb.append(String.format(SmartRedisLimiterConstant.TEMPLATE_RULE_FORMAT,
                    rule.getCount(), rule.getWindow(), rule.getUnit().name().toLowerCase()));
        }
        return sb.toString();
    }
}
