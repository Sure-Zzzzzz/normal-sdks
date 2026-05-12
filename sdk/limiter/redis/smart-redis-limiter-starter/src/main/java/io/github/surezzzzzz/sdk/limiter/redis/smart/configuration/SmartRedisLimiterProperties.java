package io.github.surezzzzzz.sdk.limiter.redis.smart.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 智能Redis限流器配置属性
 *
 * @author Sure.
 * @Date: 2026-05-08
 */
@Data
@SmartRedisLimiterComponent
@ConfigurationProperties(SmartRedisLimiterConstant.CONFIG_PREFIX)
@ConditionalOnProperty(prefix = SmartRedisLimiterConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
@Slf4j
public class SmartRedisLimiterProperties {

    /**
     * 是否启用
     */
    private Boolean enable = SmartRedisLimiterConstant.DEFAULT_ENABLE;

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

    /**
     * 限流通过时是否发布事件
     */
    private Boolean logOnPass = SmartRedisLimiterConstant.DEFAULT_LOG_ON_PASS;

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
        validateBasicConfig();
        validateAnnotationConfig();
        validateInterceptorConfig();
        validateRedisConfig();
        validateFallbackConfig();
    }

    /**
     * 验证基础配置
     */
    private void validateBasicConfig() {
        if (me == null || me.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("配置项 '%s.me' 不能为空，请设置服务标识（如：user-service）",
                            SmartRedisLimiterConstant.CONFIG_PREFIX));
        }

        if (me.length() > SmartRedisLimiterConstant.MAX_ME_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("配置项 'me' 长度不能超过%d个字符，当前长度：%d",
                            SmartRedisLimiterConstant.MAX_ME_LENGTH, me.length()));
        }

        if (mode != null && !mode.isEmpty() && !SmartRedisLimiterMode.isValid(mode)) {
            throw new IllegalArgumentException(
                    String.format("配置项 'mode' 值非法：%s，有效值：%s", mode,
                            Arrays.toString(SmartRedisLimiterMode.getAllCodes())));
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

        validateKeyStrategy(annotation.getDefaultKeyStrategy(), "annotation.defaultKeyStrategy");
        validateLimitRules(annotation.getDefaultLimits(), "annotation.defaultLimits");

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

        validateKeyStrategy(interceptor.getDefaultKeyStrategy(), "interceptor.defaultKeyStrategy");
        validateLimitRules(interceptor.getDefaultLimits(), "interceptor.defaultLimits");

        if (interceptor.getDefaultFallback() != null && !interceptor.getDefaultFallback().isEmpty()) {
            validateFallbackStrategy(interceptor.getDefaultFallback(), "interceptor.defaultFallback");
        }

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

            if (rule.getPathPattern() == null || rule.getPathPattern().trim().isEmpty()) {
                throw new IllegalArgumentException(rulePrefix + ".pathPattern 不能为空");
            }

            if (rule.getKeyStrategy() != null && !rule.getKeyStrategy().isEmpty()) {
                validateKeyStrategy(rule.getKeyStrategy(), rulePrefix + ".keyStrategy");
            }

            if (rule.getAlgorithm() != null && !rule.getAlgorithm().isEmpty()) {
                validateAlgorithm(rule.getAlgorithm(), rulePrefix + ".algorithm");
            }

            if (rule.getLimits() != null && !rule.getLimits().isEmpty()) {
                validateLimitRules(rule.getLimits(), rulePrefix + ".limits");
            }

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

            if (rule.getCount() == null) {
                throw new IllegalArgumentException(rulePrefix + ".count 不能为空");
            }
            if (rule.getCount() <= 0) {
                throw new IllegalArgumentException(
                        String.format("%s.count 必须大于0，当前值：%d", rulePrefix, rule.getCount()));
            }
            if (rule.getCount() > SmartRedisLimiterConstant.MAX_COUNT_WARNING) {
                log.warn("{}.count 值过大（{}），可能导致Redis内存压力", rulePrefix, rule.getCount());
            }

            if (rule.getWindow() == null) {
                throw new IllegalArgumentException(rulePrefix + ".window 不能为空");
            }
            if (rule.getWindow() <= 0) {
                throw new IllegalArgumentException(
                        String.format("%s.window 必须大于0，当前值：%d", rulePrefix, rule.getWindow()));
            }

            if (rule.getUnit() == null) {
                throw new IllegalArgumentException(rulePrefix + ".unit 不能为空");
            }

            long windowSeconds = rule.getWindowSeconds();
            if (windowSeconds > SmartRedisLimiterConstant.MAX_WINDOW_SECONDS_WARNING) {
                log.warn("{} 时间窗口过长（{}秒），可能导致Redis内存占用过高", rulePrefix, windowSeconds);
            }
        }
    }

    /**
     * 验证限流算法
     */
    private void validateAlgorithm(String algorithm, String configPath) {
        if (!SmartRedisLimiterConstant.ALGORITHM_FIXED.equalsIgnoreCase(algorithm)
                && !SmartRedisLimiterConstant.ALGORITHM_SLIDING.equalsIgnoreCase(algorithm)) {
            throw new IllegalArgumentException(
                    String.format("%s 值非法：%s，有效值：%s, %s",
                            configPath, algorithm,
                            SmartRedisLimiterConstant.ALGORITHM_FIXED,
                            SmartRedisLimiterConstant.ALGORITHM_SLIDING));
        }
    }

    /**
     * 验证Key策略
     */
    private void validateKeyStrategy(String strategy, String configPath) {
        if (strategy == null || strategy.isEmpty()) {
            throw new IllegalArgumentException(configPath + " 不能为空");
        }

        if (!SmartRedisLimiterKeyStrategy.isValid(strategy)) {
            throw new IllegalArgumentException(
                    String.format("%s 值非法：%s，有效值：%s",
                            configPath, strategy,
                            Arrays.toString(SmartRedisLimiterKeyStrategy.getAllCodes())));
        }
    }

    /**
     * 验证降级策略
     */
    private void validateFallbackStrategy(String strategy, String configPath) {
        if (strategy == null || strategy.isEmpty()) {
            return;
        }

        if (!SmartRedisLimiterFallbackStrategy.isValid(strategy)) {
            throw new IllegalArgumentException(
                    String.format("%s 值非法：%s，有效值：%s",
                            configPath, strategy,
                            Arrays.toString(SmartRedisLimiterFallbackStrategy.getAllCodes())));
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
                    String.format("redis.commandTimeout 必须大于0，当前值：%d", redis.getCommandTimeout()));
        }

        if (redis.getCommandTimeout() < SmartRedisLimiterConstant.COMMAND_TIMEOUT_MIN_WARNING) {
            log.warn("redis.commandTimeout 值过小（{}ms），可能导致频繁超时", redis.getCommandTimeout());
        }

        if (redis.getCommandTimeout() > SmartRedisLimiterConstant.COMMAND_TIMEOUT_MAX_WARNING) {
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
    @Data
    public static class AnnotationConfig {
        /**
         * 默认Key策略
         */
        private String defaultKeyStrategy = SmartRedisLimiterKeyStrategy.METHOD.getCode();

        /**
         * 默认限流规则
         */
        private List<SmartLimitRule> defaultLimits = new ArrayList<>();

        /**
         * 默认降级策略（不设置则使用全局fallback）
         */
        private String defaultFallback;
    }

    /**
     * 拦截器模式配置
     */
    @Data
    public static class InterceptorConfig {
        /**
         * 是否启用拦截器模式
         */
        private Boolean enabled = SmartRedisLimiterConstant.DEFAULT_INTERCEPTOR_ENABLED;

        /**
         * 默认Key策略
         */
        private String defaultKeyStrategy = SmartRedisLimiterKeyStrategy.METHOD.getCode();

        /**
         * 默认限流规则
         */
        private List<SmartLimitRule> defaultLimits = new ArrayList<>();

        /**
         * 拦截器规则列表
         */
        private List<SmartInterceptorRule> rules = new ArrayList<>();

        /**
         * 排除路径模式
         */
        private List<String> excludePatterns = Arrays.asList(SmartRedisLimiterConstant.DEFAULT_EXCLUDE_PATTERNS);

        /**
         * 默认降级策略（不设置则使用全局fallback）
         */
        private String defaultFallback;
    }

    /**
     * 拦截器规则
     */
    @Data
    public static class SmartInterceptorRule {
        /**
         * 路径模式
         */
        private String pathPattern;

        /**
         * HTTP方法
         */
        private String method;

        /**
         * Key策略
         */
        private String keyStrategy;

        /**
         * 限流算法
         */
        private String algorithm;

        /**
         * 限流规则列表
         */
        private List<SmartLimitRule> limits = new ArrayList<>();

        /**
         * 降级策略（不设置则使用defaultFallback）
         */
        private String fallback;
    }

    /**
     * 限流规则
     */
    @Data
    public static class SmartLimitRule {
        /**
         * 限流阈值
         */
        private Integer count;

        /**
         * 时间窗口
         */
        private Integer window;

        /**
         * 时间单位
         */
        private TimeUnit unit = TimeUnit.SECONDS;

        /**
         * 获取时间窗口秒数
         *
         * @return 窗口秒数
         */
        public long getWindowSeconds() {
            return unit.toSeconds(window);
        }
    }

    /**
     * Redis配置
     */
    @Data
    public static class RedisConfig {
        /**
         * 限流器专用命令超时时间（毫秒）
         */
        private Long commandTimeout = SmartRedisLimiterConstant.DEFAULT_COMMAND_TIMEOUT;
    }

    /**
     * 降级配置
     */
    @Data
    public static class FallbackConfig {
        /**
         * Redis异常时的降级策略
         */
        private String onRedisError = SmartRedisLimiterFallbackStrategy.ALLOW.getCode();
    }

    /**
     * 管理接口配置
     */
    @Data
    public static class ManagementConfig {
        /**
         * 是否启用默认异常处理器
         */
        private Boolean enableDefaultExceptionHandler = SmartRedisLimiterConstant.DEFAULT_EXCEPTION_HANDLER_ENABLED;
    }
}
