package io.github.surezzzzzz.sdk.crm.server.support;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.crm.server.constant.SimpleCrmServerConstant;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * DATA 读范围（owner 维度）：无授权上下文=本人等值；授权 all=全部；受限授权=本人并集 IN 值。
 *
 * <p>计划由公共资源链按 DATA 授权文档评估（{@code @DataPermissionOperation} +
 * {@code @CurrentDataAccessPlan}，家族形态）；计划缺席（散形态/测试）退化为本人等值，
 * 与无 DATA 时代行为一致。写路径不消费本类（设计：写=owner 等值永不变，治理动作由专属 API 码控制）。</p>
 *
 * @author surezzzzzz
 */
public final class SimpleCrmServerOwnerScope {

    private final boolean all;
    private final List<String> owners;

    private SimpleCrmServerOwnerScope(boolean all, List<String> owners) {
        this.all = all;
        this.owners = owners;
    }

    /**
     * 从主体与访问计划解析读范围。
     *
     * @param actor 归属主体
     * @param plan DATA 访问计划（可空）
     * @return 读范围
     */
    public static SimpleCrmServerOwnerScope of(CrmOwner actor, DataAccessPlan plan) {
        List<String> own = Collections.singletonList(actor.getOwner());
        if (plan == null || plan.getOutcome() == null
                || plan.getOutcome() != DataAccessOutcome.ALLOW_RESTRICTED
                && plan.getOutcome() != DataAccessOutcome.ALLOW_ALL) {
            return new SimpleCrmServerOwnerScope(false, own);
        }
        if (plan.getOutcome() == DataAccessOutcome.ALLOW_ALL) {
            return new SimpleCrmServerOwnerScope(true, null);
        }
        Set<String> merged = new LinkedHashSet<String>(own);
        for (DataGrant grant : plan.getGrants()) {
            for (DataConstraint constraint : grant.getConstraints()) {
                if (SimpleCrmServerConstant.DATA_DIMENSION_OWNER.equals(constraint.getDimension())) {
                    merged.addAll(constraint.getValues());
                }
            }
        }
        return new SimpleCrmServerOwnerScope(false, new ArrayList<String>(merged));
    }

    /**
     * 点查可见性判定。
     *
     * @param owner 目标事实归属
     * @return 是否可见
     */
    public boolean allows(String owner) {
        return all || owners.contains(owner);
    }

    /**
     * 列表 SQL 的 owner 集合；null 表示不加 owner 谓词（授权 all 的全量形态）。
     *
     * @return owner 集合或 null
     */
    public List<String> sqlOwners() {
        return all ? null : owners;
    }
}
