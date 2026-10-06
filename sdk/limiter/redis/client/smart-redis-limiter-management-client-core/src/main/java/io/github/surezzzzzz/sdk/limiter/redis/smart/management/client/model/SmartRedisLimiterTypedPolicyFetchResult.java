package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.model;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicySnapshot;
import lombok.Getter;

/**
 * v2 类型化策略拉取结果
 *
 * @author surezzzzzz
 */
@Getter
public final class SmartRedisLimiterTypedPolicyFetchResult {

    /**
     * 是否未修改
     */
    private final boolean notModified;

    /**
     * 服务端原始 ETag
     */
    private final String etag;

    /**
     * 200 响应中的完整类型化快照
     */
    private final SmartRedisLimiterTypedPolicySnapshot snapshot;

    private SmartRedisLimiterTypedPolicyFetchResult(boolean notModified,
                                                    String etag,
                                                    SmartRedisLimiterTypedPolicySnapshot snapshot) {
        this.notModified = notModified;
        this.etag = etag;
        this.snapshot = snapshot;
    }

    /**
     * 创建 304 未修改结果
     *
     * @return 未修改结果
     */
    public static SmartRedisLimiterTypedPolicyFetchResult notModified() {
        return new SmartRedisLimiterTypedPolicyFetchResult(true, null, null);
    }

    /**
     * 创建 200 完整快照结果
     *
     * @param etag     服务端原始 ETag
     * @param snapshot 完整类型化快照
     * @return 完整快照结果
     */
    public static SmartRedisLimiterTypedPolicyFetchResult fetched(
            String etag, SmartRedisLimiterTypedPolicySnapshot snapshot) {
        if (etag == null || etag.trim().isEmpty() || snapshot == null) {
            throw new SmartRedisLimiterException(
                    ErrorCode.POLICY_SNAPSHOT_INVALID,
                    ErrorMessage.REASON_SNAPSHOT_SCHEMA_UNSUPPORTED);
        }
        return new SmartRedisLimiterTypedPolicyFetchResult(false, etag, snapshot);
    }
}
