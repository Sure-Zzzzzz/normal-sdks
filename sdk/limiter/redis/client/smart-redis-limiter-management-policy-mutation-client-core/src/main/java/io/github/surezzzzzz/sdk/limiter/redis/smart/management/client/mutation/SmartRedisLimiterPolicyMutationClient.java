package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyMutationResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyRuleView;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyUpsertCommand;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 管理面类型化策略变更客户端契约（License 履约联动等写场景）
 *
 * <p>与运行端快照客户端（{@code SmartRedisLimiterManagementClient}，fetch-only）方向相反：
 * 本客户端调用管理端 v2 规则接口执行变更与回读，授权面为
 * {@code smartLimiterPolicy:write} + {@code smartLimiterPolicy:read}（回读）+
 * DATA {@code limiter-policy} write/read（均按 serviceCode 限定范围）。</p>
 *
 * <p>javax/Jakarta 双传输 starter 提供基于 AKSK 底座 {@code akskClientRestTemplate} 的实现；
 * 调用方不感知传输。</p>
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterPolicyMutationClient {

    /**
     * 编排层冲突重试埋点（409 重读重试轨迹可关联排查）
     */
    org.slf4j.Logger RETRY_LOG = org.slf4j.LoggerFactory.getLogger(SmartRedisLimiterPolicyMutationClient.class);

    /**
     * 按完整策略键精确回读：返回唯一命中规则；无匹配返回 null；多命中视为数据异常拒绝
     *
     * @param key 完整策略键（七字段身份）
     * @return 唯一规则现值，无匹配为 null
     */
    PolicyRuleView findByKey(PolicyUpsertCommand key);

    /**
     * 创建规则（身份重复由管理端 409 表达，编排层负责转为重读）
     */
    PolicyMutationResult create(PolicyUpsertCommand command);

    /**
     * 整体替换窗口（CAS：expectedRowVersion 不匹配即 409 语义，编排层负责重读重试）
     */
    PolicyMutationResult updateLimits(long ruleId, long expectedRowVersion, List<PolicyLimit> limits);

    /**
     * 更新启停（CAS）
     */
    PolicyMutationResult updateEnabled(long ruleId, long expectedRowVersion, boolean enabled);

    /**
     * 删除规则（CAS）
     */
    PolicyMutationResult delete(long ruleId, long expectedRowVersion);

    /**
     * 幂等 upsert（默认重试 3 次）：键无规则则创建；唯一规则内容已一致则幂等返回
     * （{@link PolicyMutationResult#NO_REVISION}）；不一致则以现 rowVersion 整体替换；
     * 并发冲突重读重试。
     *
     * @param command 变更命令（完整策略键 + 目标 limits/enabled）
     * @return 变更统一结果
     */
    default PolicyMutationResult upsert(PolicyUpsertCommand command) {
        return upsert(command, 3);
    }

    /**
     * 幂等 upsert（可配重试次数）
     *
     * @param command     变更命令
     * @param maxAttempts 并发冲突重读重试上限（至少 1）
     * @return 变更统一结果
     */
    default PolicyMutationResult upsert(PolicyUpsertCommand command, int maxAttempts) {
        requireIdentity(command);
        int attempts = Math.max(1, maxAttempts);
        PolicyRuleView existing = findByKey(command);
        if (existing == null) {
            return createWithConflictRetry(command, attempts);
        }
        if (matches(existing, command)) {
            return new PolicyMutationResult(existing.getRuleId(), existing.getRowVersion(),
                    PolicyMutationResult.NO_REVISION, existing.isEnabled());
        }
        return updateWithConflictRetry(existing.getRuleId(), existing.getRowVersion(),
                command.getLimits(), command.isEnabled(), attempts);
    }

    /**
     * 创建并对 409（身份重复，并发方先建）执行重读转更新
     */
    default PolicyMutationResult createWithConflictRetry(PolicyUpsertCommand command, int attempts) {
        PolicyMutationConflictException conflict = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                return create(command);
            } catch (PolicyMutationConflictException caught) {
                conflict = caught;
                RETRY_LOG.warn("策略创建冲突（身份重复），重读后转更新：serviceCode={}, resourceCode={}, objectId={}",
                        command.getServiceCode(), command.getResourceCode(), command.getObjectId());
                PolicyRuleView existing = findByKey(command);
                if (existing == null) {
                    continue;
                }
                if (matches(existing, command)) {
                    return new PolicyMutationResult(existing.getRuleId(), existing.getRowVersion(),
                            PolicyMutationResult.NO_REVISION, existing.isEnabled());
                }
                return updateWithConflictRetry(existing.getRuleId(), existing.getRowVersion(),
                        command.getLimits(), command.isEnabled(), Math.max(1, attempts - attempt - 1));
            }
        }
        throw conflict;
    }

    /**
     * 更新并对 409（rowVersion 过期，并发方先写）执行重读重试；重读后内容已一致则幂等返回
     */
    default PolicyMutationResult updateWithConflictRetry(long ruleId, long expectedRowVersion,
                                                         List<PolicyLimit> limits, boolean enabled,
                                                         int attempts) {
        PolicyMutationConflictException conflict = null;
        long rowVersion = expectedRowVersion;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                PolicyMutationResult updated = updateLimits(ruleId, rowVersion, limits);
                return enabled == updated.isEnabled()
                        ? updated
                        : updateEnabled(ruleId, updated.getRowVersion(), enabled);
            } catch (PolicyMutationConflictException caught) {
                conflict = caught;
                RETRY_LOG.warn("策略更新冲突（行版本过期），重读后重试：ruleId={}, expectedRowVersion={}",
                        ruleId, rowVersion);
                PolicyRuleView current = findByIdentityRow(ruleId);
                if (current == null) {
                    throw conflict;
                }
                if (matches(current, limits, enabled)) {
                    return new PolicyMutationResult(current.getRuleId(), current.getRowVersion(),
                            PolicyMutationResult.NO_REVISION, current.isEnabled());
                }
                rowVersion = current.getRowVersion();
            }
        }
        throw conflict;
    }

    /**
     * 按主键回读现值（供冲突重试；传输实现经完整策略键查询后按 id 定位）
     */
    PolicyRuleView findByIdentityRow(long ruleId);

    /**
     * 现值与目标一致判定：limits 逐档相等（有序）且 enabled 相同
     */
    default boolean matches(PolicyRuleView view, PolicyUpsertCommand command) {
        return matches(view, command.getLimits(), command.isEnabled());
    }

    default boolean matches(PolicyRuleView view, List<PolicyLimit> limits, boolean enabled) {
        boolean limitsEqual = view.getLimits().size() == limits.size();
        if (limitsEqual) {
            for (int i = 0; i < limits.size(); i++) {
                PolicyLimit expected = limits.get(i);
                PolicyLimit current = view.getLimits().get(i);
                if (expected.getCount() != current.getCount()
                        || expected.getWindow() != current.getWindow()
                        || !Objects.equals(expected.getUnit(), current.getUnit())) {
                    limitsEqual = false;
                    break;
                }
            }
        }
        return limitsEqual && view.isEnabled() == enabled;
    }

    default void requireIdentity(PolicyUpsertCommand command) {
        List<String> required = Arrays.asList(command.getServiceCode(), command.getResourceCode(),
                command.getDimension(), command.getSelector(), command.getNamespace());
        for (String value : required) {
            if (value == null || value.trim().isEmpty()) {
                throw new SmartRedisLimiterException(ErrorCode.POLICY_INVALID,
                        "策略 upsert 身份字段缺失：serviceCode/resourceCode/dimension/selector/namespace 均必填");
            }
        }
        if (command.getLimits() == null || command.getLimits().isEmpty()
                || command.getLimits().size() > 16) {
            throw new SmartRedisLimiterException(ErrorCode.POLICY_INVALID,
                    "策略 upsert limits 必须为 1–16 档");
        }
        if ("CUSTOM".equals(command.getDimension())
                && (command.getCustomType() == null || command.getCustomType().trim().isEmpty())) {
            throw new SmartRedisLimiterException(ErrorCode.POLICY_INVALID,
                    "CUSTOM 维度必须携带 customType");
        }
        if ("EXACT".equals(command.getSelector())
                && (command.getObjectId() == null || command.getObjectId().trim().isEmpty())) {
            throw new SmartRedisLimiterException(ErrorCode.POLICY_INVALID,
                    "EXACT 选择器必须携带 objectId");
        }
    }

    /**
     * 变更冲突（HTTP 409 语义：身份重复或行版本过期）
     */
    class PolicyMutationConflictException extends SmartRedisLimiterException {

        public PolicyMutationConflictException(String message) {
            super(ErrorCode.POLICY_DUPLICATE_KEY, message);
        }
    }
}
