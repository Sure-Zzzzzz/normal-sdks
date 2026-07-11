package io.github.surezzzzzz.sdk.retry.redis.constant;

/**
 * Redis 重试常量
 *
 * @author surezzzzzz
 */
public final class RedisRetryConstant {

    private RedisRetryConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.retry.redis";
    public static final String PROPERTY_ENABLE = "enable";
    public static final String PROPERTY_TRUE = "true";

    public static final boolean DEFAULT_ENABLE = true;
    public static final String DEFAULT_KEY_PREFIX = "sure-redis-retry";
    public static final String DEFAULT_ME = "default";
    public static final int DEFAULT_MAX_RETRY_COUNT = 3;
    public static final int DEFAULT_RETRY_RECORD_TTL_SECONDS = 24 * 60 * 60;
    public static final int DEFAULT_BASE_DELAY_MS = 1000;
    public static final int DEFAULT_MAX_DELAY_MS = 30000;
    public static final int TTL_BUFFER_HOURS = 1;
    public static final int HASH_PREFIX_LENGTH = 8;

    public static final String BUSINESS_TYPE_RETRY = "retry";
    public static final String HASH_ALGORITHM_SHA1 = "SHA-1";
    public static final String TEMPLATE_STANDARD_KEY = "%s:retry:%s:%s::%s";
    public static final String TEMPLATE_STANDARD_HASH_TAG_KEY = "%s:retry:%s:%s::{%s}";
    public static final String TEMPLATE_LEGACY_KEY = "%s:retry:%s";
    public static final String TEMPLATE_LEGACY_HASH_TAG_KEY = "{%s}:retry:%s";
    public static final String TEMPLATE_STANDARD_KEYS_PATTERN = "%s:retry:%s:%s::*";
    public static final String TEMPLATE_LEGACY_KEYS_PATTERN = "%s:retry:*";
    public static final String TEMPLATE_LEGACY_HASH_TAG_KEYS_PATTERN = "{%s}:retry:*";
}
