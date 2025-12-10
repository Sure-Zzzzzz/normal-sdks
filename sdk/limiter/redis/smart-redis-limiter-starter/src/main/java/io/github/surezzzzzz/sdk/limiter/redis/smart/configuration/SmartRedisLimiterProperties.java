package io.github.surezzzzzz.sdk.limiter.redis.smart.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterFallbackStrategy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterKeyStrategy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterMode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author: Sure.
 * @description 智能Redis限流器配置属性
 * @Date: 2024/12/XX XX:XX
 */
@Getter
@Setter
@SmartRedisLimiterComponent
@ConfigurationProperties("io.github.surezzzzzz.sdk.limiter.redis.smart")
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart", name = "enable", havingValue = "true")
@Slf4j
public class SmartRedisLimiterProperties {

    /**
     * 是否启用
     */
    private Boolean enable = false;

    /**
     * 服务标识（必填）
     */
    private String me;

    /**
     * 限流模式：annotation、interceptor、both
     */
    private String mode = SmartRedisLimiterMode.BOTH.getCode();

    /**
     * 注解模式配置
     */
    private AnnotationConfig annotation = new AnnotationConfig();

    /**
     * 拦截器模式配置
     */
    private InterceptorConfig interceptor = new InterceptorConfig();

    /**
     * Redis配置
     */
    private RedisConfig redis = new RedisConfig();

    /**
     * 降级配置
     */
    private FallbackConfig fallback = new FallbackConfig();

    /**
     * 管理接口配置
     */
    private ManagementConfig management = new ManagementConfig();

    @PostConstruct
    public void init() {
        log.info("SmartRedisLimiter 初始化: enable={}, me={}, mode={}", enable, me, mode);
    }

    /**
     * 注解模式配置
     */
    @Getter
    @Setter
    public static class AnnotationConfig {
        private String defaultKeyStrategy = SmartRedisLimiterKeyStrategy.METHOD.getCode();
        private List<SmartLimitRule> defaultLimits = new ArrayList<>();
    }

    /**
     * 拦截器模式配置
     */
    @Getter
    @Setter
    public static class InterceptorConfig {
        private Boolean enabled = true;
        private String defaultKeyStrategy = SmartRedisLimiterKeyStrategy.METHOD.getCode();
        private List<SmartLimitRule> defaultLimits = new ArrayList<>();
        private List<SmartInterceptorRule> rules = new ArrayList<>();
        private List<String> excludePatterns = new ArrayList<>();
    }

    /**
     * 拦截器规则
     */
    @Getter
    @Setter
    public static class SmartInterceptorRule {
        private String pathPattern;
        private String method;
        private String keyStrategy;
        private List<SmartLimitRule> limits = new ArrayList<>();

        @Override
        public String toString() {
            return String.format("[%s %s -> %s]",
                    method != null ? method : "*",
                    pathPattern,
                    limits);
        }
    }

    /**
     * 限流规则
     */
    @Getter
    @Setter
    public static class SmartLimitRule {
        private Integer count;
        private Integer window;
        private TimeUnit unit = TimeUnit.SECONDS;

        public long getWindowSeconds() {
            return unit.toSeconds(window);
        }

        @Override
        public String toString() {
            return String.format("%d requests/%d%s", count, window, formatTimeUnit(unit));
        }

        private String formatTimeUnit(TimeUnit unit) {
            switch (unit) {
                case SECONDS:
                    return "s";
                case MINUTES:
                    return "m";
                case HOURS:
                    return "h";
                case DAYS:
                    return "d";
                default:
                    return unit.name().toLowerCase();
            }
        }
    }

    @Getter
    @Setter
    public static class RedisConfig {

        /**
         * 限流器专用命令超时时间（毫秒）
         */
        private Long commandTimeout = 3000L;

    }

    /**
     * 降级配置
     */
    @Getter
    @Setter
    public static class FallbackConfig {
        private String onRedisError = SmartRedisLimiterFallbackStrategy.ALLOW.getCode();
    }

    /**
     * 管理接口配置
     */
    @Getter
    @Setter
    public static class ManagementConfig {
        private Boolean enableDefaultExceptionHandler = true;
    }
}
