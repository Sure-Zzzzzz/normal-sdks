package io.github.surezzzzzz.sdk.license.server.test.cases;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataPermissionRequest;
import java.util.Arrays;
import java.util.Collections;

/**
 * 测试用 DataAccessPlan 构造（直调 Controller 时的 @CurrentDataAccessPlan 等价物）。
 *
 * @author surezzzzzz
 */
final class TestPlans {

    static final String RESOURCE = "license-grant";

    private TestPlans() {
    }

    /** 全量计划（license-grant 资源指定动作 all=true）。 */
    static DataAccessPlan all(String action) {
        return allFor(RESOURCE, action);
    }

    /** 全量计划（任意资源指定动作 all=true）。 */
    static DataAccessPlan allFor(String resource, String action) {
        return DataAccessPlan.evaluate(
                new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                        SimpleDataPermissionConstant.VERSION,
                        Collections.singletonList(new DataGrant(resource,
                                Collections.singletonList(action), true,
                                Collections.<DataConstraint>emptyList()))),
                new DataPermissionRequest(resource, action));
    }

    /** 拒绝计划（无 DATA 授权的失败关闭形态）。 */
    static DataAccessPlan deny() {
        return DataAccessPlan.deny();
    }

    /** 受限计划（license-grant 资源 grantId 维度 IN 集合）。 */
    static DataAccessPlan restricted(String action, String... grantIds) {
        return restrictedFor(RESOURCE, action, "grantId", grantIds);
    }

    /** 受限计划（任意资源/任意维度 IN 集合）。 */
    static DataAccessPlan restrictedFor(String resource, String action, String dimension,
                                        String... ids) {
        return DataAccessPlan.evaluate(
                new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                        SimpleDataPermissionConstant.VERSION,
                        Collections.singletonList(new DataGrant(resource,
                                Collections.singletonList(action), false,
                                Collections.singletonList(new DataConstraint(dimension,
                                        io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator.IN,
                                        Arrays.asList(ids)))))),
                new DataPermissionRequest(resource, action));
    }
}
