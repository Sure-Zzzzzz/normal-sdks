package io.github.surezzzzzz.sdk.limiter.redis.smart.policy;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.model.SmartRedisLimiterAcceptedPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterPolicyDigestHelper;

import java.time.Instant;

/**
 * 默认远程策略快照校验器
 *
 * @author surezzzzzz
 */
public class DefaultSmartRedisLimiterPolicySnapshotValidator
        implements SmartRedisLimiterPolicySnapshotValidator {

    /**
     * 限流器配置
     */
    private final SmartRedisLimiterProperties properties;

    /**
     * 构造默认快照校验器
     *
     * @param properties 限流器配置
     */
    public DefaultSmartRedisLimiterPolicySnapshotValidator(SmartRedisLimiterProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验完整快照并构造可原子激活的不可变快照
     *
     * @param snapshot   待校验协议快照
     * @param etag       服务端原始 ETag
     * @param current    当前已接受快照
     * @param acceptedAt 本地接受时间
     * @return 已校验的不可变快照
     */
    @Override
    public SmartRedisLimiterAcceptedPolicySnapshot validate(
            SmartRedisLimiterPolicySnapshot snapshot,
            String etag,
            SmartRedisLimiterAcceptedPolicySnapshot current,
            Instant acceptedAt) {
        if (snapshot == null || acceptedAt == null) {
            throw invalidPolicy(ErrorMessage.CONFIG_ITEM_REQUIRED);
        }
        String normalizedEtag = validateEtag(etag);
        validateSnapshotContent(snapshot);
        String digest = SmartRedisLimiterPolicyDigestHelper.sha256(snapshot);
        validateVersion(current, snapshot, normalizedEtag, digest);
        return new SmartRedisLimiterAcceptedPolicySnapshot(
                snapshot, normalizedEtag, digest, acceptedAt);
    }

    private void validateSnapshotContent(SmartRedisLimiterPolicySnapshot snapshot) {
        String expectedServiceCode = properties.getMe();
        if (!expectedServiceCode.equals(snapshot.getServiceCode())) {
            throw invalidPolicy(String.format(
                    ErrorMessage.POLICY_SERVICE_CODE_MISMATCH,
                    expectedServiceCode,
                    snapshot.getServiceCode()));
        }
        int maxPolicyCount = properties.getRemotePolicy().getMaxPolicyCount();
        if (snapshot.getPolicies().size() > maxPolicyCount) {
            throw invalidPolicy(String.format(
                    ErrorMessage.POLICY_COUNT_EXCEEDED,
                    maxPolicyCount,
                    snapshot.getPolicies().size()));
        }
        int maxLimitsPerPolicy = properties.getRemotePolicy().getMaxLimitsPerPolicy();
        for (SmartRedisLimiterPolicy policy : snapshot.getPolicies()) {
            if (policy.getLimits().size() > maxLimitsPerPolicy) {
                throw invalidPolicy(String.format(
                        ErrorMessage.POLICY_LIMIT_COUNT_EXCEEDED,
                        maxLimitsPerPolicy,
                        policy.getLimits().size()));
            }
            for (SmartRedisLimiterLimit limit : policy.getLimits()) {
                validateLuaSafeInteger(limit.getCount(),
                        io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant
                                .POLICY_FIELD_COUNT);
                validateLuaSafeInteger(limit.getWindowSeconds(),
                        io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant
                                .POLICY_FIELD_WINDOW);
                validateWindowMicros(limit.getWindowSeconds());
            }
        }
    }

    private void validateWindowMicros(long windowSeconds) {
        if (windowSeconds > SmartRedisLimiterStarterConstant.LUA_MAX_SAFE_INTEGER
                / SmartRedisLimiterStarterConstant.MICROSECONDS_PER_SECOND) {
            throw invalidPolicy(String.format(
                    ErrorMessage.POLICY_LUA_SAFE_INTEGER_EXCEEDED,
                    io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant
                            .POLICY_FIELD_WINDOW,
                    SmartRedisLimiterStarterConstant.LUA_MAX_SAFE_INTEGER,
                    windowSeconds));
        }
        long currentEpochMicros = System.currentTimeMillis()
                * SmartRedisLimiterStarterConstant.MICROSECONDS_PER_MILLISECOND;
        long windowMicros = windowSeconds * SmartRedisLimiterStarterConstant.MICROSECONDS_PER_SECOND;
        if (currentEpochMicros > SmartRedisLimiterStarterConstant.LUA_MAX_SAFE_INTEGER - windowMicros) {
            throw invalidPolicy(String.format(
                    ErrorMessage.POLICY_LUA_SAFE_INTEGER_EXCEEDED,
                    io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant
                            .POLICY_FIELD_WINDOW,
                    SmartRedisLimiterStarterConstant.LUA_MAX_SAFE_INTEGER,
                    currentEpochMicros + windowMicros));
        }
    }

    private void validateLuaSafeInteger(long value, String field) {
        if (value > SmartRedisLimiterStarterConstant.LUA_MAX_SAFE_INTEGER) {
            throw invalidPolicy(String.format(
                    ErrorMessage.POLICY_LUA_SAFE_INTEGER_EXCEEDED,
                    field,
                    SmartRedisLimiterStarterConstant.LUA_MAX_SAFE_INTEGER,
                    value));
        }
    }

    private void validateVersion(SmartRedisLimiterAcceptedPolicySnapshot current,
                                 SmartRedisLimiterPolicySnapshot snapshot,
                                 String etag,
                                 String digest) {
        if (current == null) {
            return;
        }
        long currentRevision = current.getRevision();
        long receivedRevision = snapshot.getRevision();
        if (receivedRevision < currentRevision) {
            throw revisionConflict(String.format(
                    ErrorMessage.POLICY_REVISION_ROLLBACK,
                    currentRevision,
                    receivedRevision));
        }
        if (receivedRevision == currentRevision) {
            if (!current.getEtag().equals(etag)
                    || !current.getCanonicalDigest().equals(digest)) {
                throw revisionConflict(String.format(
                        ErrorMessage.POLICY_SAME_REVISION_DRIFT,
                        receivedRevision));
            }
            return;
        }
        if (current.getEtag().equals(etag)) {
            throw revisionConflict(String.format(
                    ErrorMessage.POLICY_ETAG_REUSED,
                    currentRevision,
                    receivedRevision));
        }
    }

    private String validateEtag(String etag) {
        if (etag == null) {
            throw invalidPolicy(String.format(ErrorMessage.POLICY_ETAG_INVALID, etag));
        }
        String normalized = etag.trim();
        String opaqueTag = normalized.startsWith(SmartRedisLimiterStarterConstant.ETAG_WEAK_PREFIX)
                ? normalized.substring(SmartRedisLimiterStarterConstant.ETAG_WEAK_PREFIX.length())
                : normalized;
        if (opaqueTag.length() < 2
                || !opaqueTag.startsWith(SmartRedisLimiterStarterConstant.ETAG_QUOTE)
                || !opaqueTag.endsWith(SmartRedisLimiterStarterConstant.ETAG_QUOTE)) {
            throw invalidPolicy(String.format(ErrorMessage.POLICY_ETAG_INVALID, etag));
        }
        for (int i = 1; i < opaqueTag.length() - 1; i++) {
            char value = opaqueTag.charAt(i);
            if (value == '"' || Character.isISOControl(value)) {
                throw invalidPolicy(String.format(ErrorMessage.POLICY_ETAG_INVALID, etag));
            }
        }
        return normalized;
    }

    private SmartRedisLimiterException invalidPolicy(String reason) {
        return new SmartRedisLimiterException(
                ErrorCode.POLICY_SNAPSHOT_INVALID,
                String.format(ErrorMessage.POLICY_SNAPSHOT_INVALID, reason));
    }

    private SmartRedisLimiterException revisionConflict(String message) {
        return new SmartRedisLimiterException(
                ErrorCode.POLICY_REVISION_CONFLICT,
                message);
    }
}
