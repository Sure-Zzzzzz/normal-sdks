package io.github.surezzzzzz.sdk.limiter.redis.smart.policy.client;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;
import lombok.Getter;

/**
 * 远程策略拉取结果
 *
 * @author surezzzzzz
 */
@Getter
public final class SmartRedisLimiterPolicyFetchResult {

    /**
     * 是否未修改
     */
    private final boolean notModified;

    /**
     * 服务端原始 ETag
     */
    private final String etag;

    /**
     * 200 响应中的完整快照
     */
    private final SmartRedisLimiterPolicySnapshot snapshot;

    private SmartRedisLimiterPolicyFetchResult(boolean notModified,
                                               String etag,
                                               SmartRedisLimiterPolicySnapshot snapshot) {
        this.notModified = notModified;
        this.etag = etag;
        this.snapshot = snapshot;
    }

    /**
     * 创建 304 未修改结果
     *
     * @return 未修改结果
     */
    public static SmartRedisLimiterPolicyFetchResult notModified() {
        return new SmartRedisLimiterPolicyFetchResult(true, null, null);
    }

    /**
     * 创建 200 完整快照结果
     *
     * @param etag     服务端原始 ETag
     * @param snapshot 完整快照
     * @return 完整快照结果
     */
    public static SmartRedisLimiterPolicyFetchResult fetched(
            String etag, SmartRedisLimiterPolicySnapshot snapshot) {
        if (etag == null || etag.trim().isEmpty() || snapshot == null) {
            throw new SmartRedisLimiterException(
                    ErrorCode.POLICY_RESPONSE_INVALID,
                    ErrorMessage.POLICY_ETAG_REQUIRED);
        }
        return new SmartRedisLimiterPolicyFetchResult(false, etag, snapshot);
    }
}
