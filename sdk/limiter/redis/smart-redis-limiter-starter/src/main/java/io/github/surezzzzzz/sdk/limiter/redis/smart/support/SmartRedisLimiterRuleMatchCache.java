package io.github.surezzzzzz.sdk.limiter.redis.smart.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterHttpMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则匹配缓存管理器
 * 使用简单的 ConcurrentHashMap 缓存路径匹配结果，避免重复的 AntPathMatcher 计算
 *
 * @author Sure
 * @since 1.0.2
 */
@Slf4j
@SmartRedisLimiterComponent
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart", name = "enable", havingValue = "true")
public class SmartRedisLimiterRuleMatchCache {

    @Autowired
    private SmartRedisLimiterProperties properties;

    private final ConcurrentHashMap<String, SmartRedisLimiterProperties.SmartInterceptorRule> cache =
            new ConcurrentHashMap<>();

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 未匹配到规则的标记对象（避免缓存穿透）
     */
    private static final SmartRedisLimiterProperties.SmartInterceptorRule MISS_MARKER =
            new SmartRedisLimiterProperties.SmartInterceptorRule();

    /**
     * 查找匹配的规则（带缓存）
     *
     * @param requestUri    请求URI
     * @param requestMethod 请求方法
     * @return 匹配的规则，null 表示无匹配
     */
    public SmartRedisLimiterProperties.SmartInterceptorRule findMatchedRule(
            String requestUri,
            String requestMethod) {

        // 构建缓存Key: "GET:/api/user/123"
        String cacheKey = buildCacheKey(requestMethod, requestUri);

        // 从缓存获取
        SmartRedisLimiterProperties.SmartInterceptorRule cachedRule = cache.get(cacheKey);

        if (cachedRule != null) {
            log.trace("规则缓存命中: {}", cacheKey);
            return cachedRule == MISS_MARKER ? null : cachedRule;
        }

        // 缓存未命中，执行匹配
        log.trace("规则缓存未命中，执行匹配: {}", cacheKey);
        SmartRedisLimiterProperties.SmartInterceptorRule matchedRule =
                doFindMatchedRule(requestUri, requestMethod);

        // 放入缓存（包括未匹配的情况，使用MISS_MARKER避免缓存穿透）
        cache.put(cacheKey, matchedRule != null ? matchedRule : MISS_MARKER);

        return matchedRule;
    }

    /**
     * 实际执行规则匹配
     */
    private SmartRedisLimiterProperties.SmartInterceptorRule doFindMatchedRule(
            String requestUri,
            String requestMethod) {

        List<SmartRedisLimiterProperties.SmartInterceptorRule> rules =
                properties.getInterceptor().getRules();

        // 优先级1: 精确路径 + 精确方法
        for (SmartRedisLimiterProperties.SmartInterceptorRule rule : rules) {
            if (rule.getPathPattern().equals(requestUri) && matchMethod(rule, requestMethod)) {
                log.debug("匹配到精确规则: {}", rule);
                return rule;
            }
        }

        // 优先级2: 模式路径 + 精确方法
        for (SmartRedisLimiterProperties.SmartInterceptorRule rule : rules) {
            if (pathMatcher.match(rule.getPathPattern(), requestUri) && matchMethod(rule, requestMethod)) {
                log.debug("匹配到模式规则: {}", rule);
                return rule;
            }
        }

        // 优先级3: 模式路径 + 任意方法
        for (SmartRedisLimiterProperties.SmartInterceptorRule rule : rules) {
            if (pathMatcher.match(rule.getPathPattern(), requestUri) &&
                    (rule.getMethod() == null || rule.getMethod().isEmpty())) {
                log.debug("匹配到默认规则: {}", rule);
                return rule;
            }
        }

        log.debug("未匹配到任何规则: {} {}", requestMethod, requestUri);
        return null;
    }

    /**
     * 构建缓存Key
     */
    private String buildCacheKey(String method, String uri) {
        return method + ":" + uri;
    }

    /**
     * 匹配HTTP方法
     */
    private boolean matchMethod(SmartRedisLimiterProperties.SmartInterceptorRule rule, String requestMethod) {
        String ruleMethod = rule.getMethod();
        SmartRedisLimiterHttpMethod httpMethod = SmartRedisLimiterHttpMethod.fromMethod(ruleMethod);
        return httpMethod.matches(requestMethod);
    }

    /**
     * 清空缓存（配置变更时调用）
     */
    public void clearCache() {
        cache.clear();
        log.info("SmartRedisLimiter 规则缓存已清空");
    }

    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return cache.size();
    }
}
