package io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.exception.DataPermissionAccessDeniedException;

import java.util.Map;

/**
 * 校验单个业务目标是否满足完整数据授权项。
 *
 * @author surezzzzzz
 */
public final class DataAccessPlanRestrictionVerifier {

    private DataAccessPlanRestrictionVerifier() {
        throw new UnsupportedOperationException("数据访问计划限制校验器不能实例化");
    }

    /**
     * 要求业务目标属于当前数据访问计划。
     *
     * @param plan       数据访问计划
     * @param dimensions 业务目标的受控维度
     */
    public static void requireTargetAllowed(DataAccessPlan plan, Map<String, String> dimensions) {
        if (!isTargetAllowed(plan, dimensions)) {
            throw new DataPermissionAccessDeniedException("数据范围不足");
        }
    }

    /**
     * 判断业务目标是否满足至少一个完整授权项。
     *
     * @param plan       数据访问计划
     * @param dimensions 业务目标的受控维度
     * @return 是否允许访问目标
     */
    public static boolean isTargetAllowed(DataAccessPlan plan, Map<String, String> dimensions) {
        if (plan == null || dimensions == null || plan.getOutcome() == DataAccessOutcome.DENY) {
            return false;
        }
        if (plan.getOutcome() == DataAccessOutcome.ALLOW_ALL) {
            return true;
        }
        for (DataGrant grant : plan.getGrants()) {
            if (matchesGrant(grant, dimensions)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesGrant(DataGrant grant, Map<String, String> dimensions) {
        for (DataConstraint constraint : grant.getConstraints()) {
            if (constraint.getOperator() != DataConstraintOperator.IN) {
                return false;
            }
            String value = dimensions.get(constraint.getDimension());
            if (value == null || !constraint.getValues().contains(value)) {
                return false;
            }
        }
        return true;
    }
}
