package io.github.surezzzzzz.sdk.kms.server.model;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsAuthorizationException;
import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import io.github.surezzzzzz.sdk.kms.server.constant.SmartKmsServerConstant;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 已评估 DataPlan 在 KMS 密钥归属维度上的完整投影。
 *
 * <p>KMS 只定义 {@code ownerPrincipalId} 一个数据维度。受限授权项必须恰好由该维度的
 * {@code IN} 约束组成；出现未知维度或无法完整翻译的 DNF 子句时拒绝请求，不能为了查询
 * 便利而丢弃约束。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class KmsOwnerAccessScope {

    private final boolean all;
    private final Set<String> ownerPrincipalIds;

    private KmsOwnerAccessScope(boolean all, Set<String> ownerPrincipalIds) {
        this.all = all;
        this.ownerPrincipalIds = Collections.unmodifiableSet(new LinkedHashSet<String>(ownerPrincipalIds));
    }

    /**
     * 将当前请求的 KMS DataPlan 转为可安全执行的归属范围。
     *
     * @param plan MVC 层已验证并评估的访问计划
     * @return 可用于列表、详情和写入校验的归属范围
     */
    public static KmsOwnerAccessScope from(DataAccessPlan plan) {
        if (plan == null || plan.getOutcome() == DataAccessOutcome.DENY) {
            throw new KmsAuthorizationException();
        }
        if (plan.getOutcome() == DataAccessOutcome.ALLOW_ALL) {
            return new KmsOwnerAccessScope(true, Collections.<String>emptySet());
        }
        if (plan.getOutcome() != DataAccessOutcome.ALLOW_RESTRICTED) {
            throw new KmsAuthorizationException();
        }
        Set<String> owners = new LinkedHashSet<String>();
        for (DataGrant grant : plan.getGrants()) {
            if (grant == null || grant.isAll() || grant.getConstraints().size() != 1) {
                throw new KmsAuthorizationException();
            }
            DataConstraint constraint = grant.getConstraints().get(0);
            if (!SmartKmsServerConstant.DATA_DIMENSION_OWNER_PRINCIPAL_ID.equals(constraint.getDimension())
                    || constraint.getOperator() != DataConstraintOperator.IN) {
                throw new KmsAuthorizationException();
            }
            for (String ownerPrincipalId : constraint.getValues()) {
                owners.add(KmsValidationHelper.requireOwnerPrincipalId(ownerPrincipalId));
            }
        }
        if (owners.isEmpty()) {
            throw new KmsAuthorizationException();
        }
        return new KmsOwnerAccessScope(false, owners);
    }

    /**
     * 校验一个受控归属是否被当前计划完整允许。
     *
     * @param ownerPrincipalId 待访问或写入的归属
     */
    public void requireAllowed(String ownerPrincipalId) {
        String normalized = KmsValidationHelper.requireOwnerPrincipalId(ownerPrincipalId);
        if (!all && !ownerPrincipalIds.contains(normalized)) {
            throw new KmsAuthorizationException();
        }
    }
}
