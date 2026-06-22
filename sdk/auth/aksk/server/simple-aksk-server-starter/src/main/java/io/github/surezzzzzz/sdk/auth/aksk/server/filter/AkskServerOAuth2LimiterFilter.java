package io.github.surezzzzzz.sdk.auth.aksk.server.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.provider.AkskOAuth2ClientIdKeyProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterAlgorithm;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterAlgorithmFactory;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterContextAttribute;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterFallbackStrategy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterEventHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterWebContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AKSK Server OAuth2 Limiter Filter
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
@SimpleAkskServerComponent
@ConditionalOnProperty(
        prefix = SimpleAkskServerConstant.CONFIG_PREFIX + ".limiter.oauth2",
        name = "enable",
        havingValue = "true",
        matchIfMissing = true
)
public class AkskServerOAuth2LimiterFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SimpleAkskServerProperties properties;
    private final SmartRedisLimiterProperties smartLimiterProperties;
    private final SmartRedisLimiterAlgorithmFactory algorithmFactory;
    private final AuthorizationServerSettings authorizationServerSettings;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ApplicationContext applicationContext;
    private final AkskOAuth2ClientIdKeyProvider clientIdKeyProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        SimpleAkskServerProperties.LimiterConfig.EndpointLimitConfig endpointConfig = resolveEndpointConfig(request);
        if (endpointConfig == null) {
            filterChain.doFilter(request, response);
            return;
        }

        List<SmartRedisLimiterProperties.SmartLimitRule> limitRules = convertLimitRules(endpointConfig.getLimits());
        if (limitRules.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        SmartRedisLimiterContext context = buildContext(request);
        String algorithm = endpointConfig.getAlgorithm();
        String keyStrategy = resolveKeyStrategy(request, response, context, endpointConfig, limitRules);
        String fallback = endpointConfig.getFallback();
        if (keyStrategy == null) {
            if (response.getStatus() == SmartRedisLimiterConstant.HTTP_STATUS_TOO_MANY_REQUESTS) {
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        long start = System.nanoTime();
        SmartRedisLimiterAlgorithm algorithmInstance = algorithmFactory.getAlgorithm(algorithm);
        SmartRedisLimiterResult result = algorithmInstance.tryAcquireWithResult(context, limitRules, keyStrategy, fallback);
        context.setAttribute(SmartRedisLimiterContextAttribute.DURATION_NANOS, System.nanoTime() - start);

        writeRateLimitHeaders(response, result);
        if (!result.isPassed() || Boolean.TRUE.equals(smartLimiterProperties.getLogOnPass())) {
            publishLimitEvent(context, limitRules, keyStrategy, algorithm, result);
        }

        if (!result.isPassed()) {
            writeTooManyRequests(response, request, result, limitRules);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private SimpleAkskServerProperties.LimiterConfig.EndpointLimitConfig resolveEndpointConfig(HttpServletRequest request) {
        if (!HttpMethod.POST.name().equals(request.getMethod())) {
            return null;
        }

        String requestPath = SmartRedisLimiterWebContextHelper.getRequestPath(request);
        SimpleAkskServerProperties.LimiterConfig.OAuth2Config oauth2 = properties.getLimiter().getOauth2();

        if (authorizationServerSettings.getTokenEndpoint().equals(requestPath)) {
            return oauth2.getToken();
        }
        if (authorizationServerSettings.getTokenIntrospectionEndpoint().equals(requestPath)) {
            return oauth2.getIntrospect();
        }
        if (authorizationServerSettings.getTokenRevocationEndpoint().equals(requestPath)) {
            return oauth2.getRevoke();
        }
        return null;
    }

    private SmartRedisLimiterContext buildContext(HttpServletRequest request) {
        SmartRedisLimiterContext.SmartRedisLimiterContextBuilder builder = SmartRedisLimiterContext.builder();
        SmartRedisLimiterWebContextHelper.fillWebContext(builder, request);
        builder.attribute(SmartRedisLimiterContextAttribute.MATCHED_PATH_PATTERN,
                SmartRedisLimiterWebContextHelper.getRequestPath(request));
        return builder.build();
    }

    private String resolveKeyStrategy(HttpServletRequest request,
                                      HttpServletResponse response,
                                      SmartRedisLimiterContext context,
                                      SimpleAkskServerProperties.LimiterConfig.EndpointLimitConfig endpointConfig,
                                      List<SmartRedisLimiterProperties.SmartLimitRule> limitRules) throws IOException {
        try {
            String keyPart = clientIdKeyProvider.provide(request, context);
            if (keyPart != null && !keyPart.isEmpty()) {
                context.setAttribute(SmartRedisLimiterContextAttribute.PRECOMPUTED_KEY_PART, keyPart);
                return SmartRedisLimiterConstant.EVENT_KEY_STRATEGY_CUSTOM_PREFIX
                        + AkskOAuth2ClientIdKeyProvider.BEAN_NAME;
            }
            log.debug("OAuth2 limiter clientId provider returned empty key, fallback to keyStrategy={}",
                    endpointConfig.getKeyStrategy());
            return endpointConfig.getKeyStrategy();
        } catch (Exception e) {
            log.warn("OAuth2 limiter clientId provider failed, fallback={}", endpointConfig.getFallback(), e);
            SmartRedisLimiterFallbackStrategy fallbackStrategy =
                    SmartRedisLimiterFallbackStrategy.fromCode(endpointConfig.getFallback());
            if (fallbackStrategy == SmartRedisLimiterFallbackStrategy.DENY) {
                SmartRedisLimiterResult result = SmartRedisLimiterResult.builder()
                        .passed(false)
                        .limit(limitRules.stream()
                                .mapToLong(SmartRedisLimiterProperties.SmartLimitRule::getCount)
                                .min()
                                .orElse(0L))
                        .remaining(0L)
                        .resetAt(0L)
                        .build();
                writeTooManyRequests(response, request, result, limitRules);
                return null;
            }
            return null;
        }
    }

    private List<SmartRedisLimiterProperties.SmartLimitRule> convertLimitRules(
            List<SimpleAkskServerProperties.LimiterConfig.LimitRuleConfig> sourceRules) {
        List<SmartRedisLimiterProperties.SmartLimitRule> targetRules = new ArrayList<>();
        if (sourceRules == null) {
            return targetRules;
        }
        for (SimpleAkskServerProperties.LimiterConfig.LimitRuleConfig sourceRule : sourceRules) {
            SmartRedisLimiterProperties.SmartLimitRule targetRule = new SmartRedisLimiterProperties.SmartLimitRule();
            targetRule.setCount(sourceRule.getCount());
            targetRule.setWindow(sourceRule.getWindow());
            targetRule.setUnit(sourceRule.getUnit());
            targetRules.add(targetRule);
        }
        return targetRules;
    }

    private void writeRateLimitHeaders(HttpServletResponse response, SmartRedisLimiterResult result) {
        if (result.getLimit() > 0) {
            response.setHeader(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_LIMIT, String.valueOf(result.getLimit()));
        }
        if (result.getRemaining() >= 0 && result.getLimit() > 0) {
            response.setHeader(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_REMAINING, String.valueOf(result.getRemaining()));
        }
        if (result.getResetAt() > 0) {
            response.setHeader(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_RESET, String.valueOf(result.getResetAt()));
        }
    }

    private void publishLimitEvent(SmartRedisLimiterContext context,
                                   List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                                   String keyStrategy,
                                   String algorithm,
                                   SmartRedisLimiterResult result) {
        try {
            String limitKey = SmartRedisLimiterEventHelper.buildLimitKey(
                    context, keyStrategy, smartLimiterProperties.getMe(), applicationContext);
            long durationNanos = context.getAttribute(SmartRedisLimiterContextAttribute.DURATION_NANOS) != null
                    ? (long) context.getAttribute(SmartRedisLimiterContextAttribute.DURATION_NANOS) : 0L;
            SmartRedisLimiterEvent event = new SmartRedisLimiterEvent(
                    this,
                    limitKey,
                    keyStrategy,
                    algorithm,
                    SmartRedisLimiterEventHelper.serializeLimitRules(limitRules),
                    result.isPassed(),
                    SimpleAkskServerConstant.LIMITER_SOURCE_OAUTH2_FILTER,
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
            log.warn("发布 OAuth2 限流事件失败", e);
        }
    }

    private void writeTooManyRequests(HttpServletResponse response, HttpServletRequest request,
                                      SmartRedisLimiterResult result,
                                      List<SmartRedisLimiterProperties.SmartLimitRule> limitRules) throws IOException {
        long retryAfter = limitRules.stream()
                .mapToLong(SmartRedisLimiterProperties.SmartLimitRule::getWindowSeconds)
                .min()
                .orElse(1L);

        response.setStatus(SmartRedisLimiterConstant.HTTP_STATUS_TOO_MANY_REQUESTS);
        response.setHeader(SmartRedisLimiterConstant.HEADER_RETRY_AFTER, String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put(SimpleAkskServerConstant.ADMIN_RESPONSE_SUCCESS, false);
        body.put(SimpleAkskServerConstant.ADMIN_RESPONSE_STATUS, SmartRedisLimiterConstant.HTTP_STATUS_TOO_MANY_REQUESTS);
        body.put(SimpleAkskServerConstant.ADMIN_RESPONSE_MESSAGE, SmartRedisLimiterConstant.HTTP_MESSAGE_TOO_MANY_REQUESTS);
        body.put(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_LIMIT, result.getLimit());
        body.put(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_REMAINING, result.getRemaining());
        body.put(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_RESET, result.getResetAt());
        OBJECT_MAPPER.writeValue(response.getWriter(), body);

        log.warn("OAuth2 endpoint rate limited: {} {}, retryAfter={}s",
                request.getMethod(), SmartRedisLimiterWebContextHelper.getRequestPath(request), retryAfter);
    }
}
