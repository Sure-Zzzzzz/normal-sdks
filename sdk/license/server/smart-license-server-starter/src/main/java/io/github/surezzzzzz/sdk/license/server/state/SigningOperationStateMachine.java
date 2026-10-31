package io.github.surezzzzzz.sdk.license.server.state;

import io.github.surezzzzzz.sdk.license.server.constant.OperationState;

/**
 * 签发任务受理：固定输入与状态迁移的条件更新。
 *
 * <p>迁移只经 {@link #transition}：状态机合法性 + rowVersion/executionGeneration 乐观锁，
 * 不靠进程内锁。受理事务先持久化，再事务外调 KMS。</p>
 *
 * @author surezzzzzz
 */
public final class SigningOperationStateMachine {

    private OperationState state;
    private long rowVersion;
    private long executionGeneration;

    public SigningOperationStateMachine(OperationState initialState, long rowVersion, long executionGeneration) {
        this.state = initialState;
        this.rowVersion = rowVersion;
        this.executionGeneration = executionGeneration;
    }

    /**
     * 条件迁移：期望当前 rowVersion 一致才更新（CAS）；成功后 rowVersion+1。
     *
     * @return 成功与否（版本不符或迁移非法即失败，调用方按冲突处理不盲重试）
     */
    public boolean transition(OperationState target, long expectedRowVersion) {
        if (!state.canTransitionTo(target) || this.rowVersion != expectedRowVersion || this.rowVersion == Long.MAX_VALUE) {
            return false;
        }
        this.state = target;
        this.rowVersion++;
        return true;
    }

    /** 受控恢复：递增执行代数并回 SIGNING；旧执行者即使返回也无法再提交。 */
    public boolean recover(long expectedRowVersion) {
        if (state != OperationState.UNCERTAIN || this.rowVersion != expectedRowVersion
                || this.executionGeneration == Long.MAX_VALUE || this.rowVersion == Long.MAX_VALUE) {
            return false;
        }
        this.executionGeneration++;
        this.state = OperationState.SIGNING;
        this.rowVersion++;
        return true;
    }

    /**
     * 已由业务政策确认失联后的人工接管；普通迁移不允许 SIGNING 自环。
     * 时间窗口与非空接管理由由服务核验，本方法仍严格匹配原行版本和原代数。
     */
    public boolean takeOverStaleSigning(long expectedRowVersion, long expectedExecutionGeneration) {
        if (state != OperationState.SIGNING || this.rowVersion != expectedRowVersion
                || this.executionGeneration != expectedExecutionGeneration
                || this.executionGeneration == Long.MAX_VALUE || this.rowVersion == Long.MAX_VALUE) {
            return false;
        }
        this.executionGeneration++;
        this.rowVersion++;
        return true;
    }

    public OperationState getState() {
        return state;
    }

    public long getRowVersion() {
        return rowVersion;
    }

    public long getExecutionGeneration() {
        return executionGeneration;
    }
}
