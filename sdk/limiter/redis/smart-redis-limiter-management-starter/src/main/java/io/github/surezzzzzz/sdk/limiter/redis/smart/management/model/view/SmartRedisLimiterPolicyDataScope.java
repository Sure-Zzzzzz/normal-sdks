package io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementAccessDeniedException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterPolicyValidationHelper;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 完整 DATA 计划编译出的不可变服务范围；缺计划从不等于全量。
 */
@Getter
public final class SmartRedisLimiterPolicyDataScope {
    private final boolean all;
    private final Set<String> serviceCodes;

    private SmartRedisLimiterPolicyDataScope(boolean all, Set<String> serviceCodes) {
        this.all = all;
        this.serviceCodes = Collections.unmodifiableSet(new LinkedHashSet<>(serviceCodes));
    }

    /**
     * 创建 Console 显式全量范围。
     */
    public static SmartRedisLimiterPolicyDataScope all() {
        return new SmartRedisLimiterPolicyDataScope(true, Collections.emptySet());
    }

    /**
     * 编译完整计划，任一不能表达的约束使整个计划失败关闭。
     */
    public static SmartRedisLimiterPolicyDataScope from(DataAccessPlan plan) {
        Set<String> union = new LinkedHashSet<>();
        if (plan == null || plan.getOutcome() == DataAccessOutcome.DENY) {
            return new SmartRedisLimiterPolicyDataScope(false, union);
        }
        if (plan.getOutcome() == DataAccessOutcome.ALLOW_ALL) {
            return all();
        }
        for (DataGrant grant : plan.getGrants()) {
            Set<String> intersection = null;
            for (DataConstraint constraint : grant.getConstraints()) {
                if (!SmartRedisLimiterManagementConstant.DATA_SERVICE_DIMENSION.equals(constraint.getDimension())
                        || constraint.getOperator() != DataConstraintOperator.IN) {
                    return new SmartRedisLimiterPolicyDataScope(false, Collections.emptySet());
                }
                Set<String> values = new LinkedHashSet<>();
                for (String value : constraint.getValues()) {
                    try {
                        if (!value.equals(SmartRedisLimiterPolicyValidationHelper.normalizeServiceCode(value))) {
                            return new SmartRedisLimiterPolicyDataScope(false, Collections.emptySet());
                        }
                    } catch (SmartRedisLimiterException ex) {
                        return new SmartRedisLimiterPolicyDataScope(false, Collections.emptySet());
                    }
                    values.add(value);
                }
                if (intersection == null) {
                    intersection = values;
                } else {
                    intersection.retainAll(values);
                }
            }
            if (intersection == null) {
                return new SmartRedisLimiterPolicyDataScope(false, Collections.emptySet());
            }
            union.addAll(intersection);
        }
        return new SmartRedisLimiterPolicyDataScope(false, union);
    }

    /**
     * 判断是否存在至少一个合法可访问服务。
     */
    public boolean allowsAny() {
        return all || !serviceCodes.isEmpty();
    }

    /**
     * 判断目标服务是否命中完整范围。
     */
    public boolean allows(String serviceCode) {
        return all || serviceCodes.contains(serviceCode);
    }

    /**
     * 拒绝无法执行或空的计划。
     */
    public void requireUsable() {
        if (!allowsAny()) {
            throw new SmartRedisLimiterManagementAccessDeniedException();
        }
    }

    /**
     * 在访问服务 revision 或策略前校验目标服务。
     */
    public void requireService(String serviceCode) {
        requireUsable();
        if (!allows(serviceCode)) {
            throw new SmartRedisLimiterManagementAccessDeniedException();
        }
    }
}
