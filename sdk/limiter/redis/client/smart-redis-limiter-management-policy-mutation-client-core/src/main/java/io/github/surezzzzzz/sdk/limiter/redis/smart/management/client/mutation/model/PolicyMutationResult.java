package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model;

/**
 * 变更统一结果：ruleId、rowVersion、revision、enabled 四元组
 *
 * <p>{@link #NO_REVISION} 表示本次未产生新 revision（幂等命中内容一致、未写库）。</p>
 *
 * @author surezzzzzz
 */
public final class PolicyMutationResult {

    /**
     * 幂等命中（内容已一致）时无新 revision 的占位值
     */
    public static final long NO_REVISION = -1L;

    private final long ruleId;
    private final long rowVersion;
    private final long revision;
    private final boolean enabled;

    public PolicyMutationResult(long ruleId, long rowVersion, long revision, boolean enabled) {
        this.ruleId = ruleId;
        this.rowVersion = rowVersion;
        this.revision = revision;
        this.enabled = enabled;
    }

    public long getRuleId() {
        return ruleId;
    }

    public long getRowVersion() {
        return rowVersion;
    }

    public long getRevision() {
        return revision;
    }

    /**
     * 是否产生了新 revision（false = 幂等命中未写库）
     */
    public boolean hasNewRevision() {
        return revision != NO_REVISION;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
