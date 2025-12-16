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
        log.info("========== SmartRedisLimiter 配置验证开始 ==========");

        try {
            validateConfiguration();
            log.info("========== SmartRedisLimiter 配置验证通过 ✓ ==========");
            printConfigSummary();
        } catch (IllegalArgumentException e) {
            log.error("========== SmartRedisLimiter 配置验证失败 ✗ ==========");
            log.error("错误详情: {}", e.getMessage());
            throw new IllegalStateException("SmartRedisLimiter 配置验证失败，请检查配置文件", e);
        }
    }

    /**
     * 验证配置
     */
    private void validateConfiguration() {
        // 1. 验证基础配置
        validateBasicConfig();

        // 2. 验证注解模式配置
        validateAnnotationConfig();

        // 3. 验证拦截器模式配置
        validateInterceptorConfig();

        // 4. 验证Redis配置
        validateRedisConfig();

        // 5. 验证降级配置
        validateFallbackConfig();
    }

    /**
     * 验证基础配置
     */
    private void validateBasicConfig() {
        // 验证服务标识
        if (me == null || me.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "配置项 'io.github.surezzzzzz.sdk.limiter.redis.smart.me' 不能为空，请设置服务标识（如：user-service）");
        }

        if (me.length() > 50) {
            throw new IllegalArgumentException(
                    "配置项 'me' 长度不能超过50个字符，当前长度：" + me.length());
        }

        // 验证模式
        if (mode != null && !mode.isEmpty()) {
            boolean validMode = false;
            for (SmartRedisLimiterMode modeEnum : SmartRedisLimiterMode.values()) {
                if (modeEnum.getCode().equalsIgnoreCase(mode)) {
                    validMode = true;
                    break;
                }
            }
            if (!validMode) {
                throw new IllegalArgumentException(
                        "配置项 'mode' 值非法：" + mode + "，有效值：annotation, interceptor, both");
            }
        }
    }

    /**
     * 验证注解模式配置
     */
    private void validateAnnotationConfig() {
        SmartRedisLimiterMode parsedMode = SmartRedisLimiterMode.fromCode(mode);
        if (!parsedMode.isAnnotationEnabled()) {
            return;
        }

        // 验证默认Key策略
        validateKeyStrategy(annotation.getDefaultKeyStrategy(), "annotation.defaultKeyStrategy");

        // 验证默认限流规则
        validateLimitRules(annotation.getDefaultLimits(), "annotation.defaultLimits");

        // 验证降级策略
        if (annotation.getDefaultFallback() != null && !annotation.getDefaultFallback().isEmpty()) {
            validateFallbackStrategy(annotation.getDefaultFallback(), "annotation.defaultFallback");
        }
    }

    /**
     * 验证拦截器模式配置
     */
    private void validateInterceptorConfig() {
        SmartRedisLimiterMode parsedMode = SmartRedisLimiterMode.fromCode(mode);
        if (!parsedMode.isInterceptorEnabled()) {
            return;
        }

        // 验证默认Key策略
        validateKeyStrategy(interceptor.getDefaultKeyStrategy(), "interceptor.defaultKeyStrategy");

        // 验证默认限流规则
        validateLimitRules(interceptor.getDefaultLimits(), "interceptor.defaultLimits");

        // 验证降级策略
        if (interceptor.getDefaultFallback() != null && !interceptor.getDefaultFallback().isEmpty()) {
            validateFallbackStrategy(interceptor.getDefaultFallback(), "interceptor.defaultFallback");
        }

        // 验证拦截器规则
        validateInterceptorRules(interceptor.getRules());
    }

    /**
     * 验证拦截器规则列表
     */
    private void validateInterceptorRules(List<SmartInterceptorRule> rules) {
        if (rules == null || rules.isEmpty()) {
            log.debug("未配置拦截器规则，将使用默认规则");
            return;
        }

        for (int i = 0; i < rules.size(); i++) {
            SmartInterceptorRule rule = rules.get(i);
            String rulePrefix = "interceptor.rules[" + i + "]";

            // 验证路径模式
            if (rule.getPathPattern() == null || rule.getPathPattern().trim().isEmpty()) {
                throw new IllegalArgumentException(rulePrefix + ".pathPattern 不能为空");
            }

            // 验证Key策略
            if (rule.getKeyStrategy() != null && !rule.getKeyStrategy().isEmpty()) {
                validateKeyStrategy(rule.getKeyStrategy(), rulePrefix + ".keyStrategy");
            }

            // 验证限流规则
            if (rule.getLimits() != null && !rule.getLimits().isEmpty()) {
                validateLimitRules(rule.getLimits(), rulePrefix + ".limits");
            }

            // 验证降级策略
            if (rule.getFallback() != null && !rule.getFallback().isEmpty()) {
                validateFallbackStrategy(rule.getFallback(), rulePrefix + ".fallback");
            }
        }
    }

    /**
     * 验证限流规则列表
     */
    private void validateLimitRules(List<SmartLimitRule> rules, String configPath) {
        if (rules == null || rules.isEmpty()) {
            return;
        }

        for (int i = 0; i < rules.size(); i++) {
            SmartLimitRule rule = rules.get(i);
            String rulePrefix = configPath + "[" + i + "]";

            // 验证count
            if (rule.getCount() == null) {
                throw new IllegalArgumentException(rulePrefix + ".count 不能为空");
            }
            if (rule.getCount() <= 0) {
                throw new IllegalArgumentException(
                        rulePrefix + ".count 必须大于0，当前值：" + rule.getCount());
            }
            if (rule.getCount() > 1000000) {
                log.warn("{}.count 值过大（{}），可能导致Redis内存压力", rulePrefix, rule.getCount());
            }

            // 验证window
            if (rule.getWindow() == null) {
                throw new IllegalArgumentException(rulePrefix + ".window 不能为空");
            }
            if (rule.getWindow() <= 0) {
                throw new IllegalArgumentException(
                        rulePrefix + ".window 必须大于0，当前值：" + rule.getWindow());
            }

            // 验证时间单位
            if (rule.getUnit() == null) {
                throw new IllegalArgumentException(rulePrefix + ".unit 不能为空");
            }

            // 检查时间窗口是否过长
            long windowSeconds = rule.getWindowSeconds();
            if (windowSeconds > 86400) {
                log.warn("{} 时间窗口过长（{}秒），可能导致Redis内存占用过高", rulePrefix, windowSeconds);
            }
        }
    }

    /**
     * 验证Key策略
     */
    private void validateKeyStrategy(String strategy, String configPath) {
        if (strategy == null || strategy.isEmpty()) {
            throw new IllegalArgumentException(configPath + " 不能为空");
        }

        // fromCode() 对于无效值返回 null
        SmartRedisLimiterKeyStrategy result = SmartRedisLimiterKeyStrategy.fromCode(strategy);
        if (result == null) {
            throw new IllegalArgumentException(
                    configPath + " 值非法：" + strategy + "，有效值：method, path, path-pattern, ip");
        }
    }

    /**
     * 验证降级策略
     */
    private void validateFallbackStrategy(String strategy, String configPath) {
        if (strategy == null || strategy.isEmpty()) {
            return;
        }

        // fromCode() 对于无效值返回默认值 ALLOW，需要手动检查
        boolean validFallback = false;
        for (SmartRedisLimiterFallbackStrategy fallbackEnum : SmartRedisLimiterFallbackStrategy.values()) {
            if (fallbackEnum.getCode().equalsIgnoreCase(strategy)) {
                validFallback = true;
                break;
            }
        }
        if (!validFallback) {
            throw new IllegalArgumentException(
                    configPath + " 值非法：" + strategy + "，有效值：allow, deny");
        }
    }

    /**
     * 验证Redis配置
     */
    private void validateRedisConfig() {
        if (redis.getCommandTimeout() == null) {
            throw new IllegalArgumentException("redis.commandTimeout 不能为空");
        }

        if (redis.getCommandTimeout() <= 0) {
            throw new IllegalArgumentException(
                    "redis.commandTimeout 必须大于0，当前值：" + redis.getCommandTimeout());
        }

        if (redis.getCommandTimeout() < 100) {
            log.warn("redis.commandTimeout 值过小（{}ms），可能导致频繁超时", redis.getCommandTimeout());
        }

        if (redis.getCommandTimeout() > 10000) {
            log.warn("redis.commandTimeout 值过大（{}ms），可能影响系统响应速度", redis.getCommandTimeout());
        }
    }

    /**
     * 验证降级配置
     */
    private void validateFallbackConfig() {
        validateFallbackStrategy(fallback.getOnRedisError(), "fallback.onRedisError");
    }

    /**
     * 打印配置摘要
     */
    private void printConfigSummary() {
        log.info("配置摘要:");
        log.info("  - 服务标识: {}", me);
        log.info("  - 限流模式: {}", SmartRedisLimiterMode.fromCode(mode).getDesc());
        log.info("  - Redis超时: {}ms", redis.getCommandTimeout());
        log.info("  - 降级策略: {}",
                SmartRedisLimiterFallbackStrategy.fromCode(fallback.getOnRedisError()).getDesc());

        SmartRedisLimiterMode parsedMode = SmartRedisLimiterMode.fromCode(mode);
        if (parsedMode.isInterceptorEnabled()) {
            log.info("  - 拦截器规则数: {}", interceptor.getRules().size());
        }
    }

    /**
     * 注解模式配置
     */
    @Getter
    @Setter
    public static class AnnotationConfig {
        private String defaultKeyStrategy = SmartRedisLimiterKeyStrategy.METHOD.getCode();
        private List<SmartLimitRule> defaultLimits = new ArrayList<>();
        private String defaultFallback;  // 不设置则使用全局fallback
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
        private String defaultFallback;  // 不设置则使用全局fallback
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
        private String fallback;  // 不设置则使用defaultFallback

        @Override
        public String toString() {
            return String.format("[%s %s -> %s, fallback=%s]",
                    method != null ? method : "*",
                    pathPattern,
                    limits,
                    fallback != null ? fallback : "default");
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
