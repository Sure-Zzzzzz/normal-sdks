package io.github.surezzzzzz.sdk.auth.data.permission.core.test.cases;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.exception.DataPermissionAccessDeniedException;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataPermissionRequest;
import io.github.surezzzzzz.sdk.auth.data.permission.core.support.DataAccessPlanRestrictionVerifier;
import io.github.surezzzzzz.sdk.auth.data.permission.core.support.DefaultDataPermissionEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * core SPI 契约测试（1.2.0 新增件）：校验器判定语义与拒绝异常错误码。
 *
 * @author surezzzzzz
 */
class CoreSpiContractTest {

    @Test
    @DisplayName("校验器保持 grant 内 AND、grant 间 OR：命中完整授权项放行，跨项拼接/超范围/无计划拒绝")
    void verifierKeepsDnfSemantics() {
        DataGrant first = restrictedGrant("tenant-a", "department-a");
        DataGrant second = restrictedGrant("tenant-b", "department-b");
        DataAccessPlan plan = new DefaultDataPermissionEvaluator().evaluate(
                new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                        Arrays.asList(first, second)),
                new DataPermissionRequest("order", "read"));

        assertTrue(DataAccessPlanRestrictionVerifier.isTargetAllowed(plan, dimensions("tenant-a", "department-a")),
                "完整命中同一授权项必须允许");
        assertFalse(DataAccessPlanRestrictionVerifier.isTargetAllowed(plan, dimensions("tenant-a", "department-b")),
                "不得跨授权项拼接维度");
        assertFalse(DataAccessPlanRestrictionVerifier.isTargetAllowed(
                        DataAccessPlan.deny(), dimensions("tenant-a", "department-a")),
                "DENY 计划必须拒绝");
        assertFalse(DataAccessPlanRestrictionVerifier.isTargetAllowed(null, dimensions("x", "y")),
                "无计划=拒绝");
    }

    @Test
    @DisplayName("requireTargetAllowed 拒绝时抛 core 异常携带 BIZ_006；便捷构造默认同码")
    void requireThrowsCoreExceptionWithCode() {
        DataGrant grant = restrictedGrant("tenant-a", "department-a");
        DataAccessPlan plan = new DefaultDataPermissionEvaluator().evaluate(
                new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                        Collections.singletonList(grant)),
                new DataPermissionRequest("order", "read"));

        DataPermissionAccessDeniedException exception = assertThrows(
                DataPermissionAccessDeniedException.class,
                () -> DataAccessPlanRestrictionVerifier.requireTargetAllowed(plan, dimensions("tenant-c", "department-c")));
        assertEquals("数据范围不足", exception.getMessage());
        assertEquals(ErrorCode.DATA_ACCESS_DENIED, exception.getErrorCode());
        assertEquals(ErrorCode.DATA_ACCESS_DENIED,
                new DataPermissionAccessDeniedException("便捷构造同码").getErrorCode());
        assertEquals("CUSTOM_1", new DataPermissionAccessDeniedException("CUSTOM_1", "自定义码").getErrorCode());
        assertEquals(DataAccessOutcome.DENY, DataAccessPlan.deny().getOutcome(), "deny 工厂口径不变");
    }

    private DataGrant restrictedGrant(String tenantId, String departmentId) {
        return new DataGrant("order", Collections.singletonList("read"), false, Arrays.asList(
                new DataConstraint("tenantId", DataConstraintOperator.IN, Collections.singletonList(tenantId)),
                new DataConstraint("departmentId", DataConstraintOperator.IN, Collections.singletonList(departmentId))));
    }

    private Map<String, String> dimensions(String tenantId, String departmentId) {
        Map<String, String> dimensions = new HashMap<String, String>();
        dimensions.put("tenantId", tenantId);
        dimensions.put("departmentId", departmentId);
        return dimensions;
    }
}
