package io.github.surezzzzzz.sdk.license.server.web;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 已评估 DataPlan 在 License 资源主键维度上的范围投影（KmsOwnerAccessScope 多资源对位）。
 *
 * <p>License 各 DATA 资源以自身主键为唯一维度（license-grant→grantId 等）。受限授权项必须
 * 恰好由该维度的 IN 约束组成；出现未知维度或无法完整翻译的子句时 403 拒绝——不为查询便利
 * 丢弃约束，也不静默放行全量数据（失败关闭）。列表、详情、写入共用同一范围。</p>
 *
 * @author surezzzzzz
 */
public final class LicenseAccessScope {

    private final boolean all;
    private final Set<String> ids;

    private LicenseAccessScope(boolean all, Set<String> ids) {
        this.all = all;
        this.ids = Collections.unmodifiableSet(new LinkedHashSet<String>(ids));
    }

    /**
     * 将当前请求的资源动作访问计划投影为主键范围。
     *
     * @param plan      MVC 层已评估的访问计划（@CurrentDataAccessPlan 注入）
     * @param dimension 该资源的主键维度名（如 grantId）
     * @return 可用于列表过滤与单行校验的范围
     */
    public static LicenseAccessScope from(DataAccessPlan plan, String dimension) {
        if (plan == null || plan.getOutcome() == DataAccessOutcome.DENY) {
            throw LicenseHttpException.forbidden();
        }
        if (plan.getOutcome() == DataAccessOutcome.ALLOW_ALL) {
            return new LicenseAccessScope(true, Collections.<String>emptySet());
        }
        if (plan.getOutcome() != DataAccessOutcome.ALLOW_RESTRICTED) {
            throw LicenseHttpException.forbidden();
        }
        Set<String> allowed = new LinkedHashSet<String>();
        for (DataGrant grant : plan.getGrants()) {
            if (grant == null || grant.isAll() || grant.getConstraints().size() != 1) {
                throw LicenseHttpException.forbidden();
            }
            DataConstraint constraint = grant.getConstraints().get(0);
            if (!dimension.equals(constraint.getDimension())
                    || constraint.getOperator() != DataConstraintOperator.IN) {
                throw LicenseHttpException.forbidden();
            }
            for (Object value : constraint.getValues()) {
                allowed.add(String.valueOf(value));
            }
        }
        return new LicenseAccessScope(false, allowed);
    }

    /** 单行治理动作校验：拒绝不匹配的主键。 */
    public void requireAllowed(String id) {
        if (!contains(id)) {
            throw LicenseHttpException.forbidden();
        }
    }

    /** 子资源详情与范围外资源统一按不存在处理，避免暴露其所属父资源。 */
    public void requireVisible(String id) {
        if (!contains(id)) {
            throw LicenseHttpException.notFound();
        }
    }

    /**
     * 查询路由（策略）：全量范围走全量查询，受限范围以 IN 集合走受限查询——
     * 调用方声明两种查询形态，本方法承担唯一的范围分派，控制器零分支。
     *
     * @param allQuery       全量查询（无范围约束形态）
     * @param restrictedQuery 受限查询（接受 IN 集合）
     * @return 查询结果
     */
    public <T> T resolve(java.util.function.Supplier<T> allQuery,
                         java.util.function.Function<java.util.Set<String>, T> restrictedQuery) {
        return restrictedIdsOrNull() != null ? restrictedQuery.apply(restrictedIdsOrNull())
                : allQuery.get();
    }

    public boolean contains(String id) {
        return all || ids.contains(id);
    }

    public boolean isAll() {
        return all;
    }

    /** 受限 IN 集合；全量范围返回 null（调用方不过滤）。 */
    public Set<String> restrictedIdsOrNull() {
        return all ? null : ids;
    }
}
