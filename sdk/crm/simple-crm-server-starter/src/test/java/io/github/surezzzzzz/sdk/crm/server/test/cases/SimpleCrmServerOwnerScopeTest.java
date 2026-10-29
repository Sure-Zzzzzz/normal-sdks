package io.github.surezzzzzz.sdk.crm.server.test.cases;

import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerOwnerScope;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DATA 读范围解析测试：计划缺席/拒绝=本人等值；all=全量；受限=本人∪owner 维度 IN 值。
 *
 * @author surezzzzzz
 */
class SimpleCrmServerOwnerScopeTest {

    private static final CrmOwner OWNER = CrmOwner.human("scope-owner");

    private DataAccessPlan planOf(DataGrant... grants) {
        DataGrantDocument document = new DataGrantDocument("simple-data-permission", "1.0",
                Arrays.asList(grants));
        return DataAccessPlan.evaluate(document,
                new io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataPermissionRequest(
                        "crm-customer", "read"));
    }

    @Test
    void nullPlanFallsBackToOwnOwner() {
        SimpleCrmServerOwnerScope scope = SimpleCrmServerOwnerScope.of(OWNER, null);
        assertTrue(scope.allows(OWNER.getOwner()), "本人恒可见");
        assertFalse(scope.allows("iam:other"), "无授权上下文不得越权");
        assertEquals(Collections.singletonList(OWNER.getOwner()), scope.sqlOwners());
    }

    @Test
    void allowAllExpandsToFullVisibility() {
        DataAccessPlan plan = planOf(new DataGrant("crm-customer",
                Collections.singletonList("read"), true, Collections.emptyList()));
        SimpleCrmServerOwnerScope scope = SimpleCrmServerOwnerScope.of(OWNER, plan);
        assertTrue(scope.allows("iam:anyone"), "all 授权=全量可见");
        assertNull(scope.sqlOwners(), "全量形态不加 owner 谓词");
    }

    @Test
    void restrictedGrantUnionsOwnWithOwnerDimensionValues() {
        DataAccessPlan plan = planOf(new DataGrant("crm-customer",
                Collections.singletonList("read"), false,
                Collections.singletonList(new io.github.surezzzzzz.sdk.auth.data.permission.core
                        .model.DataConstraint("owner",
                        io.github.surezzzzzz.sdk.auth.data.permission.core.constant
                                .DataConstraintOperator.IN,
                        Arrays.asList("iam:teammate-a", "iam:teammate-b")))));
        SimpleCrmServerOwnerScope scope = SimpleCrmServerOwnerScope.of(OWNER, plan);
        assertTrue(scope.allows(OWNER.getOwner()), "本人保留");
        assertTrue(scope.allows("iam:teammate-a"), "授权团队成员可见");
        assertTrue(scope.allows("iam:teammate-b"), "授权团队成员可见");
        assertFalse(scope.allows("iam:outsider"), "未授权主体不可见");
    }

    @Test
    void nonMatchingResourceGrantActsAsOwn() {
        DataAccessPlan plan = planOf(new DataGrant("other-resource",
                Collections.singletonList("read"), true, Collections.emptyList()));
        // 资源不匹配 → evaluate 产出 DENY → 范围退化为本人等值
        SimpleCrmServerOwnerScope scope = SimpleCrmServerOwnerScope.of(OWNER, plan);
        assertFalse(scope.allows("iam:other"), "无本资源授权不得越权");
        assertEquals(Collections.singletonList(OWNER.getOwner()), scope.sqlOwners());
    }
}
