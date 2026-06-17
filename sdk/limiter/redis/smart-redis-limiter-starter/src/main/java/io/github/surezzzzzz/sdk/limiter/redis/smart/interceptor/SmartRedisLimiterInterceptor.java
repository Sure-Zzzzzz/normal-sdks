package io.github.surezzzzzz.sdk.limiter.redis.smart.interceptor;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterAlgorithm;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterAlgorithmFactory;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterContextAttribute;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterFallbackStrategy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterMode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimitExceededException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.generator.SmartRedisLimiterKeyProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterEventHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterRuleMatchCacheHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterWebContextHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能限流拦截器
 * <p>拦截 HTTP 请求，根据配置的规则执行限流检查并发布事件</p>
 *
 * @author Sure.
 * @Date: 2026-05-08
 */
@SmartRedisLimiterComponent
@Slf4j
@ConditionalOnProperty(prefix = SmartRedisLimiterConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
public class SmartRedisLimiterInterceptor implements HandlerInterceptor {

    /**
     * 限流算法工厂
     */
    @Autowired
    private SmartRedisLimiterAlgorithmFactory algorithmFactory;

    /**
     * 限流器配置
     */
    @Autowired
    private SmartRedisLimiterProperties properties;

    /**
     * 规则匹配缓存
     */
    @Autowired
    private SmartRedisLimiterRuleMatchCacheHelper smartRedisLimiterRuleMatchCache;

    /**
     * 事件发布器
     */
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    /**
     * Spring上下文
     */
    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 自定义 KeyProvider 缓存（key=keyProviderName，value=Bean 实例）
     * <p>启动时校验并缓存，运行时直接读，避免每次请求查 Bean。</p>
     */
    private final Map<String, SmartRedisLimiterKeyProvider> keyProviderCache = new HashMap<>();

    /**
     * 初始化：校验并缓存所有规则中引用的 KeyProvider Bean
     */
    @PostConstruct
    public void init() {
        SmartRedisLimiterMode mode = SmartRedisLimiterMode.fromCode(properties.getMode());
        if (mode.isInterceptorEnabled()) {
            validateAndCacheKeyProviders();
        }
        log.info("SmartRedisLimiterInterceptor 初始化完成, mode={}, enabled={}, keyProviderCount={}",
                properties.getMode(), properties.getEnable(), keyProviderCache.size());
    }

    /**
     * 启动时校验所有 rule.keyProvider 引用的 Bean 存在且类型正确，缓存到 keyProviderCache
     */
    private void validateAndCacheKeyProviders() {
        List<SmartRedisLimiterProperties.SmartInterceptorRule> rules = properties.getInterceptor().getRules();
        if (rules == null || rules.isEmpty()) {
            return;
        }

        for (SmartRedisLimiterProperties.SmartInterceptorRule rule : rules) {
            String keyProviderName = rule.getKeyProvider();
            if (keyProviderName == null || keyProviderName.trim().isEmpty()) {
                continue;
            }
            if (keyProviderCache.containsKey(keyProviderName)) {
                continue;
            }
            try {
                SmartRedisLimiterKeyProvider provider =
                        applicationContext.getBean(keyProviderName, SmartRedisLimiterKeyProvider.class);
                keyProviderCache.put(keyProviderName, provider);
                log.info("SmartRedisLimiter KeyProvider 已注册: name={}, rule.pathPattern={}, rule.method={}",
                        keyProviderName, rule.getPathPattern(), rule.getMethod());
            } catch (Exception e) {
                log.error("SmartRedisLimiter KeyProvider 校验失败: name={}, rule.pathPattern={}, rule.method={}",
                        keyProviderName, rule.getPathPattern(), rule.getMethod(), e);
                throw new IllegalStateException(
                        String.format("KeyProvider Bean 不存在或类型不匹配: name=%s, rule.pathPattern=%s, rule.method=%s",
                                keyProviderName, rule.getPathPattern(), rule.getMethod()), e);
            }
        }
    }

    /**
     * 请求预处理：执行限流检查
     *
     * @param request  HTTP请求
     * @param response HTTP响应
     * @param handler  处理器
     * @return true 放行，false 拒绝
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestUri = SmartRedisLimiterWebContextHelper.getRequestPath(request);
        String requestMethod = request.getMethod();

        log.debug("SmartRedisLimiterInterceptor 处理请求: {} {}", requestMethod, requestUri);

        if (!isInterceptorModeEnabled()) {
            log.debug("拦截器模式未启用，跳过限流");
            return true;
        }

        SmartRedisLimiterProperties.SmartInterceptorRule matchedRule =
                smartRedisLimiterRuleMatchCache.findMatchedRule(requestUri, requestMethod);

        List<SmartRedisLimiterProperties.SmartLimitRule> limitRules;
        String fallbackStrategy;

        if (matchedRule != null) {
            log.debug("匹配到规则: {}", matchedRule);
            limitRules = matchedRule.getLimits().isEmpty() ?
                    properties.getInterceptor().getDefaultLimits() :
                    matchedRule.getLimits();
            fallbackStrategy = determineFallbackStrategy(matchedRule);
        } else {
            log.debug("使用默认规则, URI: {}", requestUri);
            limitRules = properties.getInterceptor().getDefaultLimits();
            fallbackStrategy = determineFallbackStrategy(null);
        }

        if (limitRules == null || limitRules.isEmpty()) {
            log.debug("无限流规则，放行请求: {}", requestUri);
            return true;
        }

        // 先构建上下文（带 web 信息 + matchedPathPattern），供 KeyProvider 使用
        SmartRedisLimiterContext.SmartRedisLimiterContextBuilder builder = SmartRedisLimiterContext.builder();
        SmartRedisLimiterWebContextHelper.fillWebContext(builder, request);

        if (matchedRule != null) {
            builder.attribute(SmartRedisLimiterContextAttribute.MATCHED_PATH_PATTERN,
                    matchedRule.getPathPattern());
        }

        SmartRedisLimiterContext context = builder.build();

        // 解析 keyStrategy / keyProvider —— 优先级：keyProvider > rule.keyStrategy > default-key-strategy
        String keyStrategy = resolveKeyStrategy(matchedRule, request, context, fallbackStrategy, limitRules);
        if (keyStrategy == null) {
            // KeyProvider 抛异常 + fallback=deny → 已抛 SmartRedisLimitExceededException
            // KeyProvider 抛异常 + fallback=allow → 直接放行
            return true;
        }

        String algorithm = determineAlgorithmStrategy(matchedRule);
        SmartRedisLimiterAlgorithm algorithmInstance = algorithmFactory.getAlgorithm(algorithm);
        SmartRedisLimiterResult result = algorithmInstance.tryAcquireWithResult(context, limitRules, keyStrategy, fallbackStrategy);

        // 写入限流响应头
        writeRateLimitHeaders(response, result);

        // 始终发布事件，由监听器侧决定是否处理
        if (!result.isPassed() || Boolean.TRUE.equals(properties.getLogOnPass())) {
            publishLimitEvent(context, limitRules, keyStrategy, algorithm, result, matchedRule);
        }

        if (!result.isPassed()) {
            long retryAfter = limitRules.stream()
                    .mapToLong(SmartRedisLimiterProperties.SmartLimitRule::getWindowSeconds)
                    .min()
                    .orElse(1L);

            log.warn("拦截器限流触发: {} {}, rules={}, retryAfter={}s",
                    requestMethod, requestUri, limitRules, retryAfter);
            throw new SmartRedisLimitExceededException(requestUri, retryAfter,
                    result.getLimit(), result.getRemaining(), result.getResetAt());
        }

        return true;
    }

    /**
     * 解析 keyStrategy（含 keyProvider 优先级处理）
     * <p>优先级：rule.keyProvider > rule.keyStrategy > interceptor.default-key-strategy</p>
     * <ul>
     *   <li>keyProvider 返回非空字符串 → 写入 PRECOMPUTED_KEY_PART，keyStrategy 记为 "custom:" + providerName</li>
     *   <li>keyProvider 返回 null/空 → 回退到 keyStrategy 路径</li>
     *   <li>keyProvider 抛异常 → 按 fallback 处理（allow → 返回 null 表示放行；deny → 抛 SmartRedisLimitExceededException）</li>
     * </ul>
     *
     * @return keyStrategy（用于事件审计与 KeyGenerator 查找）；null 表示 KeyProvider 异常+allow，调用方应直接放行
     */
    private String resolveKeyStrategy(SmartRedisLimiterProperties.SmartInterceptorRule matchedRule,
                                      HttpServletRequest request,
                                      SmartRedisLimiterContext context,
                                      String fallbackStrategy,
                                      List<SmartRedisLimiterProperties.SmartLimitRule> limitRules) {
        if (matchedRule != null) {
            String keyProviderName = matchedRule.getKeyProvider();
            if (keyProviderName != null && !keyProviderName.trim().isEmpty()) {
                SmartRedisLimiterKeyProvider provider = keyProviderCache.get(keyProviderName);
                if (provider == null) {
                    // 启动校验已通过仍取不到，理论上不该发生，作为防御性处理
                    log.error("KeyProvider 缓存未命中（启动校验未覆盖此 rule？）: {}", keyProviderName);
                    throw new IllegalStateException("KeyProvider 缓存未命中: " + keyProviderName);
                }
                String keyPart;
                try {
                    keyPart = provider.provide(request, context);
                } catch (Exception e) {
                    log.warn("KeyProvider 抛异常，按 fallback 处理: name={}, fallback={}",
                            keyProviderName, fallbackStrategy, e);
                    return handleKeyProviderException(keyProviderName, fallbackStrategy, limitRules, request);
                }
                if (keyPart != null && !keyPart.isEmpty()) {
                    context.setAttribute(SmartRedisLimiterContextAttribute.PRECOMPUTED_KEY_PART, keyPart);
                    return SmartRedisLimiterConstant.EVENT_KEY_STRATEGY_CUSTOM_PREFIX + keyProviderName;
                }
                log.debug("KeyProvider 返回 null/空，回退到 keyStrategy: name={}", keyProviderName);
                // fall through to keyStrategy
            }
            if (matchedRule.getKeyStrategy() != null && !matchedRule.getKeyStrategy().trim().isEmpty()) {
                return matchedRule.getKeyStrategy();
            }
        }
        return properties.getInterceptor().getDefaultKeyStrategy();
    }

    /**
     * KeyProvider 抛异常时的 fallback 处理：
     * <ul>
     *   <li>allow → 返回 null（调用方放行请求，不进入算法）</li>
     *   <li>deny → 抛 SmartRedisLimitExceededException（与正常限流触发一致）</li>
     * </ul>
     * <p>不回退 keyStrategy：避免 provider 实现 bug 把限流降级为低区分度的 path 维度，反而弱化保护。</p>
     */
    private String handleKeyProviderException(String keyProviderName,
                                              String fallbackStrategy,
                                              List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                                              HttpServletRequest request) {
        SmartRedisLimiterFallbackStrategy fallback = SmartRedisLimiterFallbackStrategy.fromCode(fallbackStrategy);
        if (fallback == SmartRedisLimiterFallbackStrategy.DENY) {
            long retryAfter = limitRules.stream()
                    .mapToLong(SmartRedisLimiterProperties.SmartLimitRule::getWindowSeconds)
                    .min()
                    .orElse(1L);
            long limit = limitRules.stream()
                    .mapToLong(SmartRedisLimiterProperties.SmartLimitRule::getCount)
                    .min()
                    .orElse(0L);
            log.warn("KeyProvider 异常 + fallback=deny，拒绝请求: provider={}, uri={}",
                    keyProviderName, SmartRedisLimiterWebContextHelper.getRequestPath(request));
            throw new SmartRedisLimitExceededException(
                    SmartRedisLimiterWebContextHelper.getRequestPath(request),
                    retryAfter, limit, 0, System.currentTimeMillis() / SmartRedisLimiterConstant.MILLIS_PER_SECOND + retryAfter);
        }
        log.warn("KeyProvider 异常 + fallback=allow，放行请求: provider={}", keyProviderName);
        return null;
    }

    private void publishLimitEvent(SmartRedisLimiterContext context,
                                   List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                                   String keyStrategy, String algorithm, SmartRedisLimiterResult result,
                                   SmartRedisLimiterProperties.SmartInterceptorRule matchedRule) {
        try {
            String limitKey = SmartRedisLimiterEventHelper.buildLimitKey(
                    context, keyStrategy, properties.getMe(), applicationContext);
            long durationNanos = context.getAttribute(SmartRedisLimiterContextAttribute.DURATION_NANOS) != null
                    ? (long) context.getAttribute(SmartRedisLimiterContextAttribute.DURATION_NANOS) : 0L;
            SmartRedisLimiterEvent event = new SmartRedisLimiterEvent(
                    this,
                    limitKey,
                    keyStrategy,
                    algorithm,
                    SmartRedisLimiterEventHelper.serializeLimitRules(limitRules),
                    result.isPassed(),
                    SmartRedisLimiterConstant.SOURCE_INTERCEPTOR,
                    context.getRequestPath(),
                    context.getRequestMethod(),
                    context.getClientIp(),
                    context.getMatchedPathPattern(),
                    null,
                    null,
                    context.getAttributes(),
                    result.getLimit(),
                    result.getRemaining(),
                    result.getResetAt(),
                    durationNanos);
            applicationEventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("发布限流事件失败", e);
        }
    }

    /**
     * 确定算法策略
     * 优先级：规则级别 > 拦截器默认
     */
    private String determineAlgorithmStrategy(SmartRedisLimiterProperties.SmartInterceptorRule rule) {
        // 1. 规则级别
        if (rule != null && rule.getAlgorithm() != null && !rule.getAlgorithm().isEmpty()) {
            log.debug("使用规则级别算法策略: {}", rule.getAlgorithm());
            return rule.getAlgorithm();
        }

        // 2. 拦截器模式默认值（默认固定窗口）
        log.debug("使用拦截器默认算法策略: {}", SmartRedisLimiterConstant.ALGORITHM_FIXED);
        return SmartRedisLimiterConstant.ALGORITHM_FIXED;
    }

    /**
     * 确定降级策略
     * 优先级：规则级别 > 拦截器默认 > 全局默认
     */
    private String determineFallbackStrategy(SmartRedisLimiterProperties.SmartInterceptorRule rule) {
        // 1. 规则级别
        if (rule != null && rule.getFallback() != null && !rule.getFallback().isEmpty()) {
            log.debug("使用规则级别降级策略: {}", rule.getFallback());
            return rule.getFallback();
        }

        // 2. 拦截器模式默认值
        if (properties.getInterceptor().getDefaultFallback() != null &&
                !properties.getInterceptor().getDefaultFallback().isEmpty()) {
            log.debug("使用拦截器默认降级策略: {}", properties.getInterceptor().getDefaultFallback());
            return properties.getInterceptor().getDefaultFallback();
        }

        // 3. 全局默认值
        log.debug("使用全局降级策略: {}", properties.getFallback().getOnRedisError());
        return properties.getFallback().getOnRedisError();
    }

    /**
     * 判断拦截器模式是否启用
     */
    private boolean isInterceptorModeEnabled() {
        if (!properties.getEnable()) {
            return false;
        }
        SmartRedisLimiterMode mode = SmartRedisLimiterMode.fromCode(properties.getMode());
        return mode.isInterceptorEnabled();
    }

    /**
     * 写入标准限流响应头
     */
    private void writeRateLimitHeaders(HttpServletResponse response, SmartRedisLimiterResult result) {
        try {
            if (result.getLimit() > 0) {
                response.setHeader(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_LIMIT,
                        String.valueOf(result.getLimit()));
            }
            if (result.getRemaining() >= 0 && result.getLimit() > 0) {
                response.setHeader(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_REMAINING,
                        String.valueOf(result.getRemaining()));
            }
            if (result.getResetAt() > 0) {
                response.setHeader(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_RESET,
                        String.valueOf(result.getResetAt()));
            }
        } catch (Exception e) {
            log.debug("写入限流响应头失败", e);
        }
    }
}
