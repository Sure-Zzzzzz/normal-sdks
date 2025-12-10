package io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterContextAttribute;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterHttpMethod;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterMode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimitExceededException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.strategy.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterExecutor;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.WebContextHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * @author: Sure.
 * @description 智能限流拦截器
 * @Date: 2024/12/XX XX:XX
 */
@SmartRedisLimiterComponent
@Slf4j
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart", name = "enable", havingValue = "true")
public class SmartRedisLimiterInterceptor implements HandlerInterceptor {

    @Autowired
    private SmartRedisLimiterExecutor executor;

    @Autowired
    private SmartRedisLimiterProperties properties;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @PostConstruct
    public void init() {
        log.info("SmartRedisLimiterInterceptor 初始化完成, mode={}, enabled={}",
                properties.getMode(), properties.getEnable());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestUri = getRequestUri(request);
        String requestMethod = request.getMethod();

        log.debug("SmartRedisLimiterInterceptor 处理请求: {} {}", requestMethod, requestUri);

        if (!isInterceptorModeEnabled()) {
            log.debug("拦截器模式未启用，跳过限流");
            return true;
        }

        SmartRedisLimiterProperties.SmartInterceptorRule matchedRule = findMatchedRule(requestUri, requestMethod);

        String keyStrategy;
        List<SmartRedisLimiterProperties.SmartLimitRule> limitRules;

        if (matchedRule != null) {
            log.debug("匹配到规则: {}", matchedRule);
            keyStrategy = matchedRule.getKeyStrategy() != null ?
                    matchedRule.getKeyStrategy() :
                    properties.getInterceptor().getDefaultKeyStrategy();
            limitRules = matchedRule.getLimits().isEmpty() ?
                    properties.getInterceptor().getDefaultLimits() :
                    matchedRule.getLimits();
        } else {
            log.debug("使用默认规则, URI: {}", requestUri);
            keyStrategy = properties.getInterceptor().getDefaultKeyStrategy();
            limitRules = properties.getInterceptor().getDefaultLimits();
        }

        if (limitRules == null || limitRules.isEmpty()) {
            log.debug("无限流规则，放行请求: {}", requestUri);
            return true;
        }

        SmartRedisLimiterContext.SmartRedisLimiterContextBuilder builder = SmartRedisLimiterContext.builder();
        WebContextHelper.fillWebContext(builder, request);

        if (matchedRule != null) {
            builder.attribute(SmartRedisLimiterContextAttribute.MATCHED_PATH_PATTERN,
                    matchedRule.getPathPattern());
        }

        SmartRedisLimiterContext context = builder.build();

        boolean passed = executor.tryAcquire(context, limitRules, keyStrategy);

        if (!passed) {
            long retryAfter = limitRules.stream()
                    .mapToLong(SmartRedisLimiterProperties.SmartLimitRule::getWindowSeconds)
                    .min()
                    .orElse(1L);

            log.warn("拦截器限流触发: {} {}, rules={}, retryAfter={}s",
                    requestMethod, requestUri, limitRules, retryAfter);
            throw new SmartRedisLimitExceededException(requestUri, retryAfter);
        }

        return true;
    }

    private boolean isInterceptorModeEnabled() {
        if (!properties.getEnable()) {
            return false;
        }

        SmartRedisLimiterMode mode = SmartRedisLimiterMode.fromCode(properties.getMode());
        return mode.isInterceptorEnabled();
    }

    private String getRequestUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return uri;
    }

    private SmartRedisLimiterProperties.SmartInterceptorRule findMatchedRule(String requestUri, String requestMethod) {
        List<SmartRedisLimiterProperties.SmartInterceptorRule> rules =
                properties.getInterceptor().getRules();

        for (SmartRedisLimiterProperties.SmartInterceptorRule rule : rules) {
            if (rule.getPathPattern().equals(requestUri) && matchMethod(rule, requestMethod)) {
                return rule;
            }
        }

        for (SmartRedisLimiterProperties.SmartInterceptorRule rule : rules) {
            if (pathMatcher.match(rule.getPathPattern(), requestUri) && matchMethod(rule, requestMethod)) {
                return rule;
            }
        }

        for (SmartRedisLimiterProperties.SmartInterceptorRule rule : rules) {
            if (pathMatcher.match(rule.getPathPattern(), requestUri) &&
                    (rule.getMethod() == null || rule.getMethod().isEmpty())) {
                return rule;
            }
        }

        return null;
    }

    private boolean matchMethod(SmartRedisLimiterProperties.SmartInterceptorRule rule, String requestMethod) {
        String ruleMethod = rule.getMethod();
        SmartRedisLimiterHttpMethod httpMethod = SmartRedisLimiterHttpMethod.fromMethod(ruleMethod);
        return httpMethod.matches(requestMethod);
    }
}
