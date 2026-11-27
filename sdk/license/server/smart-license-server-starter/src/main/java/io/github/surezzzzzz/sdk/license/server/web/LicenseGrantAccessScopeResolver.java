package io.github.surezzzzzz.sdk.license.server.web;

import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * License Grant 的 DATA 计划投影器；customerId 在当前授权事实库中解析为 grantId。
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
@SimpleLicenseServerComponent
public class LicenseGrantAccessScopeResolver {

    private final GrantRepository grants;

    /**
     * 将 license-grant 访问计划投影为主键范围。
     *
     * @param plan 已评估访问计划
     * @return grantId 范围
     */
    public LicenseAccessScope from(DataAccessPlan plan) {
        return LicenseAccessScope.fromGrantDimension(plan, customerIds -> {
            if (customerIds == null || customerIds.isEmpty()) {
                return new LinkedHashSet<String>();
            }
            Set<String> grantIds = new LinkedHashSet<String>();
            grants.findByCustomerIdIn(customerIds).forEach(grant ->
                    grantIds.add(grant.getGrantId()));
            return grantIds;
        });
    }
}
