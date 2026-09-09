package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.exception.DataPermissionAccessDeniedException;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;

/**
 * 开放 API 用户族 DataAccessPlan 到部门查询范围的转换器。
 *
 * <p>语义（与 demo 先例同构）：同一 grant 内约束按 AND（同维度多约束取交集），
 * 不同 grant 之间按 OR（取并集）；未知维度、非 IN 操作符或无可执行约束一律
 * 失败关闭（403），不以放宽解释掩盖无法完整执行的计划。失败关闭的异常经
 * IamExceptionHandler 统一 WARN 留痕；本类对放行形态（全量 / 受限范围）留 DEBUG。
 *
 * @author surezzzzzz
 */
@Slf4j
public final class UserDataAccessPlanConverter {

    private UserDataAccessPlanConverter() {
    }

    /**
     * 将计划转换为部门查询范围。
     *
     * @param plan 已评估的数据访问计划
     * @return null 表示全量（ALLOW_ALL）；空集表示无可见部门
     */
    public static Set<Long> toDepartmentScope(DataAccessPlan plan) {
        if (plan == null || plan.getOutcome() == DataAccessOutcome.DENY) {
            throw new DataPermissionAccessDeniedException("数据权限不足");
        }
        if (plan.getOutcome() == DataAccessOutcome.ALLOW_ALL) {
            log.debug("DATA 计划全量放行（ALLOW_ALL）");
            return null;
        }
        Set<Long> scope = new HashSet<Long>();
        for (DataGrant grant : plan.getGrants()) {
            scope.addAll(departmentIds(grant));
        }
        log.debug("DATA 计划部门范围：{}", scope);
        return scope;
    }

    private static Set<Long> departmentIds(DataGrant grant) {
        Set<Long> values = null;
        for (DataConstraint constraint : grant.getConstraints()) {
            if (constraint.getOperator() != DataConstraintOperator.IN
                    || !SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_DEPARTMENT_ID
                    .equals(constraint.getDimension())) {
                throw new DataPermissionAccessDeniedException("用户数据范围维度无法执行："
                        + constraint.getDimension());
            }
            Set<Long> parsed = parseIds(constraint.getValues());
            values = values == null ? parsed : intersect(values, parsed);
        }
        if (values == null) {
            throw new DataPermissionAccessDeniedException("用户数据授权项缺少可执行约束");
        }
        return values;
    }

    private static Set<Long> parseIds(java.util.List<String> values) {
        Set<Long> parsed = new HashSet<Long>();
        for (String value : values) {
            try {
                parsed.add(Long.valueOf(value));
            } catch (NumberFormatException exception) {
                throw new DataPermissionAccessDeniedException("部门维度值非法：" + value);
            }
        }
        return parsed;
    }

    private static Set<Long> intersect(Set<Long> left, Set<Long> right) {
        Set<Long> result = new HashSet<Long>(left);
        result.retainAll(right);
        return result;
    }
}
