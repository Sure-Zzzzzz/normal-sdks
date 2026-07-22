package io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant;

/**
 * SmartRedisLimiter Management 错误码
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String CONFIG_VALIDATION_FAILED = "CONFIG_001";
    public static final String CONFIG_REST_TOKEN_REQUIRED = "CONFIG_002";
    public static final String POLICY_VALIDATION_FAILED = "VALIDATION_001";
    public static final String POLICY_NOT_FOUND = "NOT_FOUND_001";
    public static final String POLICY_IDENTITY_CONFLICT = "CONFLICT_001";
    public static final String POLICY_VERSION_CONFLICT = "CONFLICT_002";
    public static final String REVISION_OVERFLOW = "CONFLICT_003";
    public static final String PERSISTENCE_FAILED = "PERSISTENCE_001";
    public static final String SNAPSHOT_FAILED = "SNAPSHOT_001";
    public static final String EVENT_REGISTRATION_FAILED = "EVENT_001";
    public static final String PAGE_QUERY_FAILED = "PAGE_001";
}
