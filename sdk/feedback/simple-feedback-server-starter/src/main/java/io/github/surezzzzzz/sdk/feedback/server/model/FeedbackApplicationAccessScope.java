package io.github.surezzzzzz.sdk.feedback.server.model;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.feedback.server.constant.FeedbackPermissionConstant;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 反馈管理侧应用访问范围（DataAccessPlan→application 维度 IN 集，KmsOwnerAccessScope 同型定式）。
 *
 * <p>非直观约束：DENY 与 RESTRICTED 空集语义不同——DENY=无 DATA 授权（403），
 * RESTRICTED 空集=有授权但与启用注册项交集为空（空列表 200，audit 手册同款语义）。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class FeedbackApplicationAccessScope {

    private final boolean all;
    private final Set<String> applicationCodes;
    private final boolean denied;

    private FeedbackApplicationAccessScope(boolean all, boolean denied, Set<String> applicationCodes) {
        this.all = all;
        this.denied = denied;
        this.applicationCodes = Collections.unmodifiableSet(new LinkedHashSet<String>(applicationCodes));
    }

    /**
     * 从数据访问计划构造（application 维度）。
     *
     * @param plan 数据访问计划（@CurrentDataAccessPlan 注入）
     * @return 访问范围
     */
    public static FeedbackApplicationAccessScope from(DataAccessPlan plan) {
        if (plan == null || plan.getOutcome() == DataAccessOutcome.DENY) {
            return deny();
        }
        if (plan.getOutcome() == DataAccessOutcome.ALLOW_ALL) {
            return all();
        }
        Set<String> codes = new LinkedHashSet<String>();
        if (plan.getGrants() != null) {
            plan.getGrants().forEach(grant -> {
                if (grant.getConstraints() == null) {
                    return;
                }
                grant.getConstraints().forEach(constraint -> {
                    if (FeedbackPermissionConstant.DATA_DIMENSION_APPLICATION.equals(constraint.getDimension())
                            && constraint.getValues() != null) {
                        codes.addAll(constraint.getValues());
                    }
                });
            });
        }
        return restricted(codes);
    }

    /**
     * 全量范围（ALLOW_ALL）。
     *
     * @return 全量范围
     */
    public static FeedbackApplicationAccessScope all() {
        return new FeedbackApplicationAccessScope(true, false, Collections.<String>emptySet());
    }

    /**
     * 受限范围（application IN 集；空集=有授权但无可见应用，空列表语义非 403）。
     *
     * @param codes 应用编码集
     * @return 受限范围
     */
    public static FeedbackApplicationAccessScope restricted(Set<String> codes) {
        return new FeedbackApplicationAccessScope(false, false, codes);
    }

    /**
     * 无授权范围（DENY，403 语义）。
     *
     * @return 拒绝范围
     */
    public static FeedbackApplicationAccessScope deny() {
        return new FeedbackApplicationAccessScope(false, true, Collections.<String>emptySet());
    }

    /**
     * 无 DATA 授权（403 语义）。
     *
     * @return true 表示拒绝
     */
    public boolean isDenied() {
        return denied;
    }

    /**
     * 把 application 条件并入 SQL：全量=不加条件；受限=IN 集（由 repository 拼装）。
     *
     * @return 可空的应用编码 IN 集（null=全量）
     */
    public Set<String> restrictedCodesOrNull() {
        return all ? null : applicationCodes;
    }

    /**
     * 判断目标应用是否在范围内。
     *
     * @param applicationCode 应用编码
     * @return true 可见（全量恒 true）
     */
    public boolean contains(String applicationCode) {
        return all || applicationCodes.contains(applicationCode);
    }
}
