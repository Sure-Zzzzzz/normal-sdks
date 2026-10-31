package io.github.surezzzzzz.sdk.license.server.constant;

/**
 * License 授权到 limiter Management 的策略投影状态。
 *
 * @author surezzzzzz
 */
public enum LimiterPolicyBindingState {
    PENDING_SYNC,
    SYNCED,
    DISABLE_PENDING,
    DISABLED,
    FAILED,
    UNCERTAIN,
    SUPERSEDED
}
