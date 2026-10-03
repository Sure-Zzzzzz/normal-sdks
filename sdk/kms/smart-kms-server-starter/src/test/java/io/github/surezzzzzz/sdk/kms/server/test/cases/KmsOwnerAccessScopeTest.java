package io.github.surezzzzzz.sdk.kms.server.test.cases;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.*;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsAuthorizationException;
import io.github.surezzzzzz.sdk.kms.server.model.KmsOwnerAccessScope;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KMS DataPlan 归属投影测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class KmsOwnerAccessScopeTest {

    @Test
    void shouldAcceptAllAndRestrictedOwnerPlans() {
        KmsOwnerAccessScope all = KmsOwnerAccessScope.from(plan(new DataGrant("kms-key",
                Collections.singletonList("read"), true, Collections.<DataConstraint>emptyList())));
        assertTrue(all.isAll(), "全量 DataPlan 必须映射为全量归属范围");
        assertDoesNotThrow(() -> all.requireAllowed("iam:user-a"), "全量范围应允许任意合法归属");

        KmsOwnerAccessScope restricted = KmsOwnerAccessScope.from(plan(new DataGrant("kms-key",
                Collections.singletonList("read"), false, Collections.singletonList(new DataConstraint(
                "ownerPrincipalId", DataConstraintOperator.IN, Arrays.asList("iam:user-a", "iam:user-b"))))));
        assertTrue(!restricted.isAll(), "受限 DataPlan 不能误映射为全量范围");
        assertDoesNotThrow(() -> restricted.requireAllowed("iam:user-a"), "命中 IN 约束的归属必须允许");
        assertThrows(KmsAuthorizationException.class, () -> restricted.requireAllowed("iam:user-c"),
                "未命中 IN 约束的归属必须拒绝");
        log.info("KMS 受限 DataPlan 已限制归属数量: {}", restricted.getOwnerPrincipalIds().size());
    }

    @Test
    void shouldRejectUntranslatableRestrictedPlans() {
        assertThrows(KmsAuthorizationException.class, () -> KmsOwnerAccessScope.from(plan(new DataGrant("kms-key",
                        Collections.singletonList("read"), false, Collections.singletonList(new DataConstraint("region",
                        DataConstraintOperator.IN, Collections.singletonList("east")))))),
                "未知数据维度不得被忽略");
        assertThrows(KmsAuthorizationException.class, () -> KmsOwnerAccessScope.from(DataAccessPlan.deny()),
                "拒绝计划不得拥有可执行的归属范围");
    }

    private DataAccessPlan plan(DataGrant grant) {
        return DataAccessPlan.evaluate(new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                        SimpleDataPermissionConstant.VERSION, Collections.singletonList(grant)),
                new DataPermissionRequest("kms-key", "read"));
    }
}
