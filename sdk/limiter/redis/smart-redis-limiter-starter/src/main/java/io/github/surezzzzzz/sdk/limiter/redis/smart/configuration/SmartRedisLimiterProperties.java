package io.github.surezzzzzz.sdk.limiter.redis.smart.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.*;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterConfigurationException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterPolicyValidationHelper;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * 智能 Redis 限流器配置属性
 *
 * @author Sure.
 * @Date: 2026-05-08
 */
@Data
@ConfigurationProperties(SmartRedisLimiterConstant.CONFIG_PREFIX)
@Slf4j
public class SmartRedisLimiterProperties {

    /**
     * 是否启用
     */
    private Boolean enable = SmartRedisLimiterConstant.DEFAULT_ENABLE;

    /**
     * 服务标识（必填），同时作为动态策略 serviceCode
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
     * Redis 配置
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
     * 远程动态策略配置
     */
    private RemotePolicyConfig remotePolicy = new RemotePolicyConfig();

    /**
     * 限流通过时是否发布事件
     */
    private Boolean logOnPass = SmartRedisLimiterConstant.DEFAULT_LOG_ON_PASS;

    /**
     * 初始化并验证配置
     *
     * @throws SmartRedisLimiterConfigurationException 配置非法时抛出
     */
    @PostConstruct
    public void init() {
        log.info("========== SmartRedisLimiter 配置验证开始 ==========");
        try {
            validateConfiguration();
            log.info("========== SmartRedisLimiter 配置验证通过 ==========");
            printConfigSummary();
        } catch (SmartRedisLimiterConfigurationException e) {
            log.error("========== SmartRedisLimiter 配置验证失败 ==========");
            log.error("错误详情: {}", e.getMessage());
            throw e;
        }
    }

    private SmartRedisLimiterConfigurationException configException(String message) {
        return new SmartRedisLimiterConfigurationException(
                ErrorCode.CONFIG_VALIDATION_FAILED,
                String.format(ErrorMessage.CONFIG_VALIDATION_FAILED, message));
    }

    private void validateConfiguration() {
        validateBasicConfig();
        validateAnnotationConfig();
        validateInterceptorConfig();
        validateRedisConfig();
        validateFallbackConfig();
        validateRemotePolicyConfig();
    }

    private void validateBasicConfig() {
        try {
            me = SmartRedisLimiterPolicyValidationHelper.normalizeServiceCode(me);
        } catch (SmartRedisLimiterException ex) {
            String message = me == null || me.trim().isEmpty()
                    ? String.format(ErrorMessage.CONFIG_ME_REQUIRED,
                    SmartRedisLimiterStarterConstant.CONFIG_PATH_ME)
                    : String.format(ErrorMessage.CONFIG_ME_INVALID,
                    SmartRedisLimiterStarterConstant.CONFIG_PATH_ME, ex.getMessage());
            throw configException(message);
        }

        if (!SmartRedisLimiterMode.isValid(mode)) {
            throw configException(String.format(ErrorMessage.CONFIG_MODE_INVALID,
                    SmartRedisLimiterStarterConstant.CONFIG_PATH_MODE, mode,
                    Arrays.toString(SmartRedisLimiterMode.getAllCodes())));
        }
    }

    private void validateAnnotationConfig() {
        SmartRedisLimiterMode parsedMode = SmartRedisLimiterMode.fromCode(mode);
        if (!parsedMode.isAnnotationEnabled()) {
            return;
        }
        requireConfig(annotation, SmartRedisLimiterStarterConstant.CONFIG_PATH_ANNOTATION_DEFAULT_LIMITS);
        validateKeyStrategy(annotation.getDefaultKeyStrategy(),
                SmartRedisLimiterStarterConstant.CONFIG_PATH_ANNOTATION_DEFAULT_KEY_STRATEGY);
        validateLimitRules(annotation.getDefaultLimits(),
                SmartRedisLimiterStarterConstant.CONFIG_PATH_ANNOTATION_DEFAULT_LIMITS);
        validateFallbackStrategy(annotation.getDefaultFallback(),
                SmartRedisLimiterStarterConstant.CONFIG_PATH_ANNOTATION_DEFAULT_FALLBACK);
    }

    private void validateInterceptorConfig() {
        SmartRedisLimiterMode parsedMode = SmartRedisLimiterMode.fromCode(mode);
        if (!parsedMode.isInterceptorEnabled()) {
            return;
        }
        requireConfig(interceptor, SmartRedisLimiterStarterConstant.CONFIG_PATH_INTERCEPTOR_RULES);
        validateKeyStrategy(interceptor.getDefaultKeyStrategy(),
                SmartRedisLimiterStarterConstant.CONFIG_PATH_INTERCEPTOR_DEFAULT_KEY_STRATEGY);
        validateLimitRules(interceptor.getDefaultLimits(),
                SmartRedisLimiterStarterConstant.CONFIG_PATH_INTERCEPTOR_DEFAULT_LIMITS);
        validateFallbackStrategy(interceptor.getDefaultFallback(),
                SmartRedisLimiterStarterConstant.CONFIG_PATH_INTERCEPTOR_DEFAULT_FALLBACK);
        validateInterceptorRules(interceptor.getRules());
    }

    private void validateInterceptorRules(List<SmartInterceptorRule> rules) {
        if (rules == null || rules.isEmpty()) {
            log.debug("未配置拦截器规则，将使用默认规则");
            return;
        }
        for (int i = 0; i < rules.size(); i++) {
            String rulePrefix = indexedPath(SmartRedisLimiterStarterConstant.CONFIG_PATH_INTERCEPTOR_RULES, i);
            SmartInterceptorRule rule = rules.get(i);
            requireConfig(rule, rulePrefix);
            requireText(rule.getPathPattern(), childPath(rulePrefix,
                    SmartRedisLimiterStarterConstant.CONFIG_FIELD_PATH_PATTERN));
            rule.setResourceCode(validateOptionalResourceCode(rule.getResourceCode(), childPath(rulePrefix,
                    SmartRedisLimiterStarterConstant.CONFIG_FIELD_RESOURCE_CODE)));
            if (hasText(rule.getKeyStrategy())) {
                validateKeyStrategy(rule.getKeyStrategy(), childPath(rulePrefix,
                        SmartRedisLimiterStarterConstant.CONFIG_FIELD_KEY_STRATEGY));
            }
            if (hasText(rule.getAlgorithm())) {
                validateAlgorithm(rule.getAlgorithm(), childPath(rulePrefix,
                        SmartRedisLimiterStarterConstant.CONFIG_FIELD_ALGORITHM));
            }
            if (rule.getLimits() != null && !rule.getLimits().isEmpty()) {
                validateLimitRules(rule.getLimits(), childPath(rulePrefix,
                        SmartRedisLimiterStarterConstant.CONFIG_FIELD_LIMITS));
            }
            validateFallbackStrategy(rule.getFallback(), childPath(rulePrefix,
                    SmartRedisLimiterStarterConstant.CONFIG_FIELD_FALLBACK));
        }
    }

    private void validateLimitRules(List<SmartLimitRule> rules, String configPath) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        if (rules.size() > SmartRedisLimiterConstant.MAX_LIMITS_PER_POLICY) {
            throw configException(String.format(ErrorMessage.CONFIG_LIMIT_COUNT_EXCEEDED,
                    configPath, SmartRedisLimiterConstant.MAX_LIMITS_PER_POLICY, rules.size()));
        }

        Set<Long> windowSecondsSet = new HashSet<>();
        List<SmartRedisLimiterLimit> coreLimits = new ArrayList<>(rules.size());
        for (int i = 0; i < rules.size(); i++) {
            String rulePrefix = indexedPath(configPath, i);
            SmartLimitRule rule = rules.get(i);
            requireConfig(rule, rulePrefix);
            SmartRedisLimiterLimit coreLimit = toCoreLimit(rule, rulePrefix);
            validateLuaSafeInteger(coreLimit.getCount(), childPath(rulePrefix,
                    SmartRedisLimiterStarterConstant.CONFIG_FIELD_COUNT));
            validateLuaSafeInteger(coreLimit.getWindowSeconds(), childPath(rulePrefix,
                    SmartRedisLimiterStarterConstant.CONFIG_FIELD_WINDOW));
            validateWindowMicros(coreLimit.getWindowSeconds(), childPath(rulePrefix,
                    SmartRedisLimiterStarterConstant.CONFIG_FIELD_WINDOW));
            if (!windowSecondsSet.add(coreLimit.getWindowSeconds())) {
                throw configException(String.format(ErrorMessage.CONFIG_LIMIT_DUPLICATE_WINDOW,
                        configPath, coreLimit.getWindowSeconds()));
            }
            coreLimits.add(coreLimit);
            if (coreLimit.getCount() > SmartRedisLimiterConstant.MAX_COUNT_WARNING) {
                log.warn("{}.count 值过大（{}），可能导致 Redis 内存压力", rulePrefix, coreLimit.getCount());
            }
            if (coreLimit.getWindowSeconds() > SmartRedisLimiterConstant.MAX_WINDOW_SECONDS_WARNING) {
                log.warn("{} 时间窗口过长（{}秒），可能导致 Redis 内存占用过高",
                        rulePrefix, coreLimit.getWindowSeconds());
            }
        }
        coreLimits.sort(Comparator.comparingLong(SmartRedisLimiterLimit::getWindowSeconds));
        for (SmartRedisLimiterLimit coreLimit : coreLimits) {
            validateLuaEpochBoundary(coreLimit.getWindowSeconds(), configPath);
        }
    }

    private SmartRedisLimiterLimit toCoreLimit(SmartLimitRule rule, String configPath) {
        String countPath = childPath(configPath, SmartRedisLimiterStarterConstant.CONFIG_FIELD_COUNT);
        String windowPath = childPath(configPath, SmartRedisLimiterStarterConstant.CONFIG_FIELD_WINDOW);
        String unitPath = childPath(configPath, SmartRedisLimiterStarterConstant.CONFIG_FIELD_UNIT);
        requireConfig(rule.getCount(), countPath);
        requireConfig(rule.getWindow(), windowPath);
        requireConfig(rule.getUnit(), unitPath);
        try {
            return new SmartRedisLimiterLimit(rule.getCount(), rule.getWindow(), rule.getUnit());
        } catch (SmartRedisLimiterException ex) {
            throw configException(String.format(ErrorMessage.CONFIG_LIMIT_INVALID, configPath, ex.getMessage()));
        }
    }


    private void validateLuaSafeInteger(long value, String configPath) {
        if (value > SmartRedisLimiterStarterConstant.LUA_MAX_SAFE_INTEGER) {
            throw configException(String.format(ErrorMessage.CONFIG_LUA_SAFE_INTEGER_EXCEEDED,
                    configPath, SmartRedisLimiterStarterConstant.LUA_MAX_SAFE_INTEGER, value));
        }
    }

    private void validateWindowMicros(long windowSeconds, String configPath) {
        if (windowSeconds > SmartRedisLimiterStarterConstant.LUA_MAX_SAFE_INTEGER
                / SmartRedisLimiterStarterConstant.MICROSECONDS_PER_SECOND) {
            throw configException(String.format(ErrorMessage.CONFIG_LUA_SAFE_INTEGER_EXCEEDED,
                    configPath, SmartRedisLimiterStarterConstant.LUA_MAX_SAFE_INTEGER, windowSeconds));
        }
    }

    private void validateLuaEpochBoundary(long windowSeconds, String configPath) {
        long currentEpochMicros;
        long windowMicros;
        try {
            currentEpochMicros = Math.multiplyExact(
                    System.currentTimeMillis(),
                    SmartRedisLimiterStarterConstant.MICROSECONDS_PER_MILLISECOND);
            windowMicros = Math.multiplyExact(
                    windowSeconds,
                    SmartRedisLimiterStarterConstant.MICROSECONDS_PER_SECOND);
        } catch (ArithmeticException ex) {
            throw configException(String.format(ErrorMessage.CONFIG_LIMIT_INVALID,
                    configPath, ex.getMessage()));
        }
        validateLuaSafeInteger(currentEpochMicros, configPath);
        if (currentEpochMicros > SmartRedisLimiterStarterConstant.LUA_MAX_SAFE_INTEGER - windowMicros) {
            throw configException(String.format(ErrorMessage.CONFIG_LUA_SAFE_INTEGER_EXCEEDED,
                    configPath, SmartRedisLimiterStarterConstant.LUA_MAX_SAFE_INTEGER,
                    currentEpochMicros + windowMicros));
        }
    }

    private void validateAlgorithm(String algorithm, String configPath) {
        if (!SmartRedisLimiterConstant.ALGORITHM_FIXED.equalsIgnoreCase(algorithm)
                && !SmartRedisLimiterConstant.ALGORITHM_SLIDING.equalsIgnoreCase(algorithm)) {
            throw configException(String.format(ErrorMessage.CONFIG_ALGORITHM_INVALID,
                    configPath, algorithm,
                    SmartRedisLimiterConstant.ALGORITHM_FIXED,
                    SmartRedisLimiterConstant.ALGORITHM_SLIDING));
        }
    }

    private void validateKeyStrategy(String strategy, String configPath) {
        requireText(strategy, configPath);
        if (!SmartRedisLimiterKeyStrategy.isValid(strategy)) {
            throw configException(String.format(ErrorMessage.CONFIG_ENUM_VALUE_INVALID,
                    configPath, strategy, Arrays.toString(SmartRedisLimiterKeyStrategy.getAllCodes())));
        }
    }

    private void validateFallbackStrategy(String strategy, String configPath) {
        if (!hasText(strategy)) {
            return;
        }
        if (!SmartRedisLimiterFallbackStrategy.isValid(strategy)) {
            throw configException(String.format(ErrorMessage.CONFIG_ENUM_VALUE_INVALID,
                    configPath, strategy, Arrays.toString(SmartRedisLimiterFallbackStrategy.getAllCodes())));
        }
    }

    private String validateOptionalResourceCode(String resourceCode, String configPath) {
        if (!hasText(resourceCode)) {
            return SmartRedisLimiterStarterConstant.DEFAULT_RESOURCE_CODE;
        }
        try {
            return SmartRedisLimiterPolicyValidationHelper.normalizeResourceCode(resourceCode);
        } catch (SmartRedisLimiterException ex) {
            throw configException(String.format(ErrorMessage.CONFIG_RESOURCE_CODE_INVALID,
                    configPath, ex.getMessage()));
        }
    }

    private void validateRedisConfig() {
        requireConfig(redis, SmartRedisLimiterStarterConstant.CONFIG_PATH_REDIS_COMMAND_TIMEOUT);
        if (redis.getCommandTimeout() == null) {
            throw configException(String.format(ErrorMessage.CONFIG_COMMAND_TIMEOUT_REQUIRED,
                    SmartRedisLimiterStarterConstant.CONFIG_PATH_REDIS_COMMAND_TIMEOUT));
        }
        if (redis.getCommandTimeout() <= 0) {
            throw configException(String.format(ErrorMessage.CONFIG_COMMAND_TIMEOUT_MUST_BE_POSITIVE,
                    SmartRedisLimiterStarterConstant.CONFIG_PATH_REDIS_COMMAND_TIMEOUT,
                    redis.getCommandTimeout()));
        }
        if (redis.getCommandTimeout() < SmartRedisLimiterConstant.COMMAND_TIMEOUT_MIN_WARNING) {
            log.warn("{} 值过小（{}ms），可能导致频繁超时",
                    SmartRedisLimiterStarterConstant.CONFIG_PATH_REDIS_COMMAND_TIMEOUT,
                    redis.getCommandTimeout());
        }
        if (redis.getCommandTimeout() > SmartRedisLimiterConstant.COMMAND_TIMEOUT_MAX_WARNING) {
            log.warn("{} 值过大（{}ms），可能影响系统响应速度",
                    SmartRedisLimiterStarterConstant.CONFIG_PATH_REDIS_COMMAND_TIMEOUT,
                    redis.getCommandTimeout());
        }
        if (redis.getTimeoutExecutorThreads() == null || redis.getTimeoutExecutorThreads() <= 0) {
            throw configException(String.format(
                    ErrorMessage.CONFIG_TIMEOUT_EXECUTOR_THREADS_MUST_BE_POSITIVE,
                    SmartRedisLimiterStarterConstant.CONFIG_PATH_REDIS_TIMEOUT_EXECUTOR_THREADS));
        }
        if (redis.getTimeoutExecutorQueueCapacity() == null
                || redis.getTimeoutExecutorQueueCapacity() <= 0) {
            throw configException(String.format(
                    ErrorMessage.CONFIG_TIMEOUT_EXECUTOR_QUEUE_CAPACITY_MUST_BE_POSITIVE,
                    SmartRedisLimiterStarterConstant.CONFIG_PATH_REDIS_TIMEOUT_EXECUTOR_QUEUE_CAPACITY));
        }
    }

    private void validateFallbackConfig() {
        requireConfig(fallback, SmartRedisLimiterStarterConstant.CONFIG_PATH_FALLBACK_ON_REDIS_ERROR);
        validateFallbackStrategy(fallback.getOnRedisError(),
                SmartRedisLimiterStarterConstant.CONFIG_PATH_FALLBACK_ON_REDIS_ERROR);
    }

    private void validateRemotePolicyConfig() {
        requireConfig(remotePolicy, SmartRedisLimiterStarterConstant.CONFIG_PATH_REMOTE_POLICY_ENABLE);
        requireConfig(remotePolicy.getEnable(), SmartRedisLimiterStarterConstant.CONFIG_PATH_REMOTE_POLICY_ENABLE);
        if (!remotePolicy.getEnable()) {
            return;
        }
        remotePolicy.setSnapshotUrl(validateSnapshotUrl(remotePolicy.getSnapshotUrl()));
        validatePositive(remotePolicy.getRefreshIntervalMillis(),
                SmartRedisLimiterStarterConstant.CONFIG_PATH_REMOTE_POLICY_REFRESH_INTERVAL_MILLIS);
        validatePositive(remotePolicy.getConnectTimeoutMillis(),
                SmartRedisLimiterStarterConstant.CONFIG_PATH_REMOTE_POLICY_CONNECT_TIMEOUT_MILLIS);
        validatePositive(remotePolicy.getReadTimeoutMillis(),
                SmartRedisLimiterStarterConstant.CONFIG_PATH_REMOTE_POLICY_READ_TIMEOUT_MILLIS);
        validateMaximum(remotePolicy.getConnectTimeoutMillis(),
                SmartRedisLimiterStarterConstant.MAX_HTTP_TIMEOUT_MILLIS,
                SmartRedisLimiterStarterConstant.CONFIG_PATH_REMOTE_POLICY_CONNECT_TIMEOUT_MILLIS);
        validateMaximum(remotePolicy.getReadTimeoutMillis(),
                SmartRedisLimiterStarterConstant.MAX_HTTP_TIMEOUT_MILLIS,
                SmartRedisLimiterStarterConstant.CONFIG_PATH_REMOTE_POLICY_READ_TIMEOUT_MILLIS);
        validatePositive(remotePolicy.getMaxPolicyCount(),
                SmartRedisLimiterStarterConstant.CONFIG_PATH_REMOTE_POLICY_MAX_POLICY_COUNT);
        validatePositive(remotePolicy.getMaxLimitsPerPolicy(),
                SmartRedisLimiterStarterConstant.CONFIG_PATH_REMOTE_POLICY_MAX_LIMITS_PER_POLICY);
        if (remotePolicy.getMaxLimitsPerPolicy() > SmartRedisLimiterConstant.MAX_LIMITS_PER_POLICY) {
            throw configException(String.format(ErrorMessage.CONFIG_REMOTE_POLICY_MAX_LIMITS_EXCEEDED,
                    SmartRedisLimiterStarterConstant.CONFIG_PATH_REMOTE_POLICY_MAX_LIMITS_PER_POLICY,
                    SmartRedisLimiterConstant.MAX_LIMITS_PER_POLICY,
                    remotePolicy.getMaxLimitsPerPolicy()));
        }
        validatePositive(remotePolicy.getMaxResponseBytes(),
                SmartRedisLimiterStarterConstant.CONFIG_PATH_REMOTE_POLICY_MAX_RESPONSE_BYTES);
    }

    private String validateSnapshotUrl(String snapshotUrl) {
        if (!hasText(snapshotUrl)) {
            throw configException(String.format(ErrorMessage.CONFIG_ITEM_REQUIRED,
                    SmartRedisLimiterStarterConstant.CONFIG_PATH_REMOTE_POLICY_SNAPSHOT_URL));
        }
        String normalizedSnapshotUrl = snapshotUrl.trim();
        try {
            URI uri = new URI(normalizedSnapshotUrl);
            String scheme = uri.getScheme();
            int port = uri.getPort();
            boolean validScheme = SmartRedisLimiterStarterConstant.HTTP_SCHEME_HTTP.equalsIgnoreCase(scheme)
                    || SmartRedisLimiterStarterConstant.HTTP_SCHEME_HTTPS.equalsIgnoreCase(scheme);
            boolean validPort = port == SmartRedisLimiterStarterConstant.URI_UNSPECIFIED_PORT
                    || port >= SmartRedisLimiterStarterConstant.MIN_VALID_PORT
                    && port <= SmartRedisLimiterStarterConstant.MAX_VALID_PORT;
            if (!uri.isAbsolute() || !validScheme || !hasText(uri.getHost()) || !validPort
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                throw invalidSnapshotUrl(snapshotUrl);
            }
            return normalizedSnapshotUrl;
        } catch (URISyntaxException ex) {
            throw invalidSnapshotUrl(snapshotUrl);
        }
    }

    private SmartRedisLimiterConfigurationException invalidSnapshotUrl(String snapshotUrl) {
        return configException(String.format(ErrorMessage.CONFIG_REMOTE_POLICY_URL_INVALID,
                SmartRedisLimiterStarterConstant.CONFIG_PATH_REMOTE_POLICY_SNAPSHOT_URL, snapshotUrl));
    }

    private void validatePositive(Number value, String configPath) {
        if (value == null) {
            throw configException(String.format(ErrorMessage.CONFIG_ITEM_REQUIRED, configPath));
        }
        if (value.longValue() <= 0) {
            throw configException(String.format(ErrorMessage.CONFIG_ITEM_MUST_BE_POSITIVE,
                    configPath, value.longValue()));
        }
    }

    private void validateMaximum(Number value, long maximum, String configPath) {
        if (value != null && value.longValue() > maximum) {
            throw configException(String.format(ErrorMessage.CONFIG_ITEM_MAX_EXCEEDED,
                    configPath, maximum, value.longValue()));
        }
    }

    private void requireConfig(Object value, String configPath) {
        if (value == null) {
            throw configException(String.format(ErrorMessage.CONFIG_ITEM_REQUIRED, configPath));
        }
    }

    private void requireText(String value, String configPath) {
        if (!hasText(value)) {
            throw configException(String.format(ErrorMessage.CONFIG_ITEM_REQUIRED, configPath));
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String indexedPath(String path, int index) {
        return String.format(SmartRedisLimiterStarterConstant.TEMPLATE_CONFIG_INDEXED_PATH, path, index);
    }

    private String childPath(String path, String child) {
        return String.format(SmartRedisLimiterStarterConstant.TEMPLATE_CONFIG_CHILD_PATH, path, child);
    }

    private void printConfigSummary() {
        log.info("配置摘要:");
        log.info("  - 服务标识: {}", me);
        log.info("  - 限流模式: {}", SmartRedisLimiterMode.fromCode(mode).getDesc());
        log.info("  - Redis 超时: {}ms", redis.getCommandTimeout());
        log.info("  - Redis Hash Tag: {}", redis.getUseHashTag());
        log.info("  - 超时保护线程池: threads={}, queueCapacity={}",
                redis.getTimeoutExecutorThreads(), redis.getTimeoutExecutorQueueCapacity());
        log.info("  - 降级策略: {}",
                SmartRedisLimiterFallbackStrategy.fromCode(fallback.getOnRedisError()).getDesc());
        log.info("  - 远程策略: {}", remotePolicy.getEnable());
        if (SmartRedisLimiterMode.fromCode(mode).isInterceptorEnabled()) {
            log.info("  - 拦截器规则数: {}", interceptor.getRules().size());
        }
    }

    /**
     * 注解模式配置
     */
    @Data
    public static class AnnotationConfig {
        /**
         * 默认 Key 策略
         */
        private String defaultKeyStrategy = SmartRedisLimiterKeyStrategy.METHOD.getCode();
        /**
         * 默认限流规则
         */
        private List<SmartLimitRule> defaultLimits = new ArrayList<>();
        /**
         * 默认降级策略，不设置时使用全局 fallback
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
         * 默认 Key 策略
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
        private List<String> excludePatterns = new ArrayList<>(SmartRedisLimiterConstant.DEFAULT_EXCLUDE_PATTERN_LIST);
        /**
         * 默认降级策略，不设置时使用全局 fallback
         */
        private String defaultFallback;
    }

    /**
     * 拦截器规则
     */
    @Data
    public static class SmartInterceptorRule {
        /**
         * 稳定资源编码，空字符串表示仅使用本地策略
         */
        private String resourceCode = SmartRedisLimiterStarterConstant.DEFAULT_RESOURCE_CODE;
        /**
         * 路径模式
         */
        private String pathPattern;
        /**
         * HTTP 方法
         */
        private String method;
        /**
         * Key 策略
         */
        private String keyStrategy;
        /**
         * 自定义 KeyProvider 的 Spring Bean 名称
         */
        private String keyProvider;
        /**
         * 限流算法
         */
        private String algorithm;
        /**
         * 限流规则列表
         */
        private List<SmartLimitRule> limits = new ArrayList<>();
        /**
         * 降级策略，不设置时使用默认降级策略
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
        private Long count;
        /**
         * 时间窗口
         */
        private Long window;
        /**
         * 时间单位
         */
        private SmartRedisLimiterTimeUnit unit = SmartRedisLimiterTimeUnit.SECONDS;

        /**
         * 获取时间窗口秒数
         *
         * @return 窗口秒数
         * @throws SmartRedisLimiterException 时间单位为空或换算溢出时抛出
         */
        public long getWindowSeconds() {
            if (unit == null) {
                throw new SmartRedisLimiterException(
                        io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode.POLICY_TIME_UNIT_INVALID,
                        io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage.POLICY_TIME_UNIT_INVALID);
            }
            return unit.toSeconds(window);
        }
    }

    /**
     * Redis 配置
     */
    @Data
    public static class RedisConfig {
        /**
         * 限流器专用命令超时时间（毫秒）
         */
        private Long commandTimeout = SmartRedisLimiterConstant.DEFAULT_COMMAND_TIMEOUT;
        /**
         * 是否使用 Redis Cluster Hash Tag
         */
        private Boolean useHashTag = SmartRedisLimiterConstant.DEFAULT_USE_HASH_TAG;
        /**
         * 超时保护线程池大小
         */
        private Integer timeoutExecutorThreads = SmartRedisLimiterConstant.DEFAULT_TIMEOUT_EXECUTOR_THREADS;
        /**
         * 超时保护线程池队列容量
         */
        private Integer timeoutExecutorQueueCapacity = SmartRedisLimiterConstant.DEFAULT_TIMEOUT_EXECUTOR_QUEUE_CAPACITY;
    }

    /**
     * 降级配置
     */
    @Data
    public static class FallbackConfig {
        /**
         * Redis 异常时的降级策略
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

    /**
     * 远程动态策略配置
     */
    @Data
    public static class RemotePolicyConfig {
        /**
         * 是否启用远程动态策略
         */
        private Boolean enable = SmartRedisLimiterStarterConstant.DEFAULT_REMOTE_POLICY_ENABLE;
        /**
         * 完整快照 API 地址
         */
        private String snapshotUrl = SmartRedisLimiterStarterConstant.DEFAULT_REMOTE_POLICY_SNAPSHOT_URL;
        /**
         * 对外策略 REST 固定 token
         */
        @ToString.Exclude
        private String policyToken;
        /**
         * 固定延迟刷新间隔（毫秒）
         */
        private Long refreshIntervalMillis =
                SmartRedisLimiterStarterConstant.DEFAULT_REMOTE_POLICY_REFRESH_INTERVAL_MILLIS;
        /**
         * 连接超时（毫秒）
         */
        private Long connectTimeoutMillis =
                SmartRedisLimiterStarterConstant.DEFAULT_REMOTE_POLICY_CONNECT_TIMEOUT_MILLIS;
        /**
         * 读取超时（毫秒）
         */
        private Long readTimeoutMillis =
                SmartRedisLimiterStarterConstant.DEFAULT_REMOTE_POLICY_READ_TIMEOUT_MILLIS;
        /**
         * 是否在应用就绪后立即执行首次刷新
         */
        private Boolean initialRefresh = SmartRedisLimiterStarterConstant.DEFAULT_REMOTE_POLICY_INITIAL_REFRESH;
        /**
         * 单服务快照最大策略数
         */
        private Integer maxPolicyCount = SmartRedisLimiterStarterConstant.DEFAULT_REMOTE_POLICY_MAX_POLICY_COUNT;
        /**
         * 单策略最大限额窗口数
         */
        private Integer maxLimitsPerPolicy =
                SmartRedisLimiterStarterConstant.DEFAULT_REMOTE_POLICY_MAX_LIMITS_PER_POLICY;
        /**
         * 最大响应字节数
         */
        private Long maxResponseBytes = SmartRedisLimiterStarterConstant.DEFAULT_REMOTE_POLICY_MAX_RESPONSE_BYTES;
    }
}
