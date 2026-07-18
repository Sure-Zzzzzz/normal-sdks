package io.github.surezzzzzz.sdk.auth.data.permission.core.test.cases;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.exception.DataPermissionValidationException;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.*;
import io.github.surezzzzzz.sdk.auth.data.permission.core.support.DefaultDataPermissionEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class DataPermissionBehaviorContractTest {

    private final DefaultDataPermissionEvaluator evaluator = new DefaultDataPermissionEvaluator();

    @Test
    void shouldKeepNestedProtocolCollectionsImmutableAndValueBased() {
        DataConstraint firstConstraint = new DataConstraint("scope_a", DataConstraintOperator.IN,
                Arrays.asList("value-b", "value-a"));
        DataConstraint sameConstraint = new DataConstraint("scope_a", DataConstraintOperator.IN,
                Arrays.asList("value-a", "value-b"));
        DataGrant firstGrant = restrictedGrant("test_resource", "read", "scope_a", "value-a");
        DataGrant sameGrant = restrictedGrant("test_resource", "read", "scope_a", "value-a");
        DataGrantDocument firstDocument = document(firstGrant);
        DataGrantDocument sameDocument = document(sameGrant);
        DataAccessPlan firstPlan = evaluator.evaluate(firstDocument, new DataPermissionRequest("test_resource", "read"));
        DataAccessPlan samePlan = evaluator.evaluate(sameDocument, new DataPermissionRequest("test_resource", "read"));

        log.info("规范化模型：constraint={}，grant={}，document={}，plan={}", firstConstraint, firstGrant,
                firstDocument, firstPlan);
        assertEquals(firstConstraint, sameConstraint, "等价约束必须具有结构化值语义");
        assertEquals(firstGrant, sameGrant, "等价授权项必须具有结构化值语义");
        assertEquals(firstDocument, sameDocument, "等价授权文档必须具有结构化值语义");
        assertEquals(firstPlan, samePlan, "等价访问计划必须具有结构化值语义");
        assertEquals(firstDocument.hashCode(), sameDocument.hashCode(), "等价授权文档必须具有相同哈希值");
        assertEquals(firstPlan.hashCode(), samePlan.hashCode(), "等价访问计划必须具有相同哈希值");

        UnsupportedOperationException valuesException = assertThrows(UnsupportedOperationException.class,
                () -> firstConstraint.getValues().add("value-c"), "约束值集合必须不可修改");
        log.info("约束值集合异常：{}", valuesException.toString());
        UnsupportedOperationException actionsException = assertThrows(UnsupportedOperationException.class,
                () -> firstGrant.getActions().add("export"), "授权动作集合必须不可修改");
        log.info("授权动作集合异常：{}", actionsException.toString());
        UnsupportedOperationException constraintsException = assertThrows(UnsupportedOperationException.class,
                () -> firstGrant.getConstraints().clear(), "授权约束集合必须不可修改");
        log.info("授权约束集合异常：{}", constraintsException.toString());
        UnsupportedOperationException planException = assertThrows(UnsupportedOperationException.class,
                () -> firstPlan.getGrants().clear(), "访问计划授权项集合必须不可修改");
        log.info("访问计划集合异常：{}", planException.toString());
    }

    @Test
    void shouldCanonicalizeDocumentGrantOrderWithoutWideningClauses() {
        DataGrant resourceB = allGrant("test_resource_b", "read");
        DataGrant resourceAAll = allGrant("test_resource_a", "read");
        DataGrant resourceARestricted = restrictedGrant("test_resource_a", "read", "scope_b", "value-b");
        DataGrantDocument document = new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                SimpleDataPermissionConstant.VERSION, Arrays.asList(resourceB, resourceAAll, resourceARestricted));

        log.info("排序后的授权文档：{}", document);
        assertEquals("test_resource_a", document.getGrants().get(0).getResource(), "授权项必须先按资源排序");
        assertFalse(document.getGrants().get(0).isAll(), "相同资源动作时受限授权必须保持稳定排序");
        assertEquals("test_resource_a", document.getGrants().get(1).getResource(), "同资源授权必须相邻");
        assertTrue(document.getGrants().get(1).isAll(), "全量授权必须保留独立子句");
        assertEquals("test_resource_b", document.getGrants().get(2).getResource(), "后续资源不得改变排序");

        DataAccessPlan plan = evaluator.evaluate(document, new DataPermissionRequest("test_resource_a", "read"));
        log.info("包含全量授权的访问计划：{}", plan);
        assertEquals(DataAccessOutcome.ALLOW_ALL, plan.getOutcome(), "任一精确命中的全量授权必须覆盖为全量放行");
        assertTrue(plan.getGrants().isEmpty(), "全量放行不得泄露或拼接受限子句");
    }

    @Test
    void shouldRejectInvalidModelBoundariesWithSpecificErrorCodes() {
        assertInvalid(() -> new DataGrant("test_resource", Collections.singletonList("read"), false,
                Collections.<DataConstraint>emptyList()), ErrorCode.INVALID_GRANT, "受限授权缺少约束必须拒绝");
        DataConstraint scopeA = new DataConstraint("scope_a", DataConstraintOperator.IN,
                Collections.singletonList("value-a"));
        assertInvalid(() -> new DataGrant("test_resource", Collections.singletonList("read"), false,
                Arrays.asList(scopeA, scopeA)), ErrorCode.INVALID_GRANT, "同一授权项的重复维度必须拒绝");
        assertInvalid(() -> new DataPermissionRequest(" test_resource", "read"), ErrorCode.INVALID_DOCUMENT,
                "带首尾空白的标识符必须拒绝");
        assertInvalid(() -> new DataConstraint("scope_a", DataConstraintOperator.IN,
                Collections.singletonList("#{currentUser}")), ErrorCode.INVALID_CONSTRAINT, "SpEL表达式必须拒绝");
    }

    @Test
    void shouldRejectNullNestedCollectionsAndElements() {
        assertInvalid(() -> new DataConstraint("scope_a", DataConstraintOperator.IN, Arrays.asList("value-a", null)),
                ErrorCode.INVALID_CONSTRAINT, "约束值集合不能包含null元素");
        assertInvalid(() -> new DataGrant("test_resource", null, true, Collections.<DataConstraint>emptyList()),
                ErrorCode.INVALID_GRANT, "授权动作集合不能为null");
        assertInvalid(() -> new DataGrant("test_resource", Collections.<String>singletonList(null), true,
                Collections.<DataConstraint>emptyList()), ErrorCode.INVALID_GRANT, "授权动作集合不能包含null元素");
        assertInvalid(() -> new DataGrant("test_resource", Collections.singletonList("read"), false,
                Collections.<DataConstraint>singletonList(null)), ErrorCode.INVALID_GRANT, "授权约束集合不能包含null元素");
        assertInvalid(() -> new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                Collections.<DataGrant>singletonList(null)), ErrorCode.INVALID_DOCUMENT, "授权文档集合不能包含null元素");
        assertInvalid(() -> new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                Collections.<DataGrant>emptyList()), ErrorCode.INVALID_DOCUMENT, "授权文档授权项集合不能为空");
    }

    @Test
    void shouldRejectRemainingNullModelParameters() {
        assertInvalid(() -> new DataConstraint(null, DataConstraintOperator.IN, Collections.singletonList("value-a")),
                ErrorCode.INVALID_CONSTRAINT, "约束维度不能为null");
        assertInvalid(() -> new DataConstraint("scope_a", DataConstraintOperator.IN, null), ErrorCode.INVALID_CONSTRAINT,
                "约束值集合不能为null");
        assertInvalid(() -> new DataGrant("test_resource", Collections.singletonList("read"), true, null),
                ErrorCode.INVALID_GRANT, "授权约束集合不能为null");
        assertInvalid(() -> new DataPermissionRequest("test_resource", null), ErrorCode.INVALID_DOCUMENT,
                "授权请求动作不能为null");
        assertInvalid(() -> new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, null,
                Collections.singletonList(allGrant("test_resource", "read"))), ErrorCode.UNSUPPORTED_VERSION,
                "授权文档版本不能为null");
    }

    @Test
    void shouldKeepResourceAndActionMatchingCaseSensitiveAndExact() {
        DataGrantDocument document = document(allGrant("test_resource", "read"));
        DataAccessPlan matchingPlan = evaluator.evaluate(document, new DataPermissionRequest("test_resource", "read"));
        DataAccessPlan resourceCasePlan = evaluator.evaluate(document, new DataPermissionRequest("TEST_RESOURCE", "read"));
        DataAccessPlan actionCasePlan = evaluator.evaluate(document, new DataPermissionRequest("test_resource", "READ"));
        DataAccessPlan prefixPlan = evaluator.evaluate(document, new DataPermissionRequest("test_resource_child", "read"));

        log.info("精确匹配计划：{}，资源大小写计划：{}，动作大小写计划：{}，前缀计划：{}", matchingPlan, resourceCasePlan,
                actionCasePlan, prefixPlan);
        assertEquals(DataAccessOutcome.ALLOW_ALL, matchingPlan.getOutcome(), "完全相同的资源动作必须匹配");
        assertEquals(DataAccessOutcome.DENY, resourceCasePlan.getOutcome(), "资源匹配必须区分大小写");
        assertEquals(DataAccessOutcome.DENY, actionCasePlan.getOutcome(), "动作匹配必须区分大小写");
        assertEquals(DataAccessOutcome.DENY, prefixPlan.getOutcome(), "资源匹配不得退化为前缀匹配");
        assertNotEquals(matchingPlan, resourceCasePlan, "不同访问结果不得具有相同结构化值");
    }

    private DataGrantDocument document(DataGrant grant) {
        return new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                Collections.singletonList(grant));
    }

    private DataGrant allGrant(String resource, String action) {
        return new DataGrant(resource, Collections.singletonList(action), true, Collections.<DataConstraint>emptyList());
    }

    private DataGrant restrictedGrant(String resource, String action, String dimension, String value) {
        return new DataGrant(resource, Collections.singletonList(action), false,
                Collections.singletonList(new DataConstraint(dimension, DataConstraintOperator.IN,
                        Collections.singletonList(value))));
    }

    private void assertInvalid(org.junit.jupiter.api.function.Executable executable, String errorCode, String message) {
        DataPermissionValidationException exception = assertThrows(DataPermissionValidationException.class, executable,
                message);
        log.info("校验异常：{}", exception.getMessage());
        assertEquals(errorCode, exception.getErrorCode(), message);
    }
}
