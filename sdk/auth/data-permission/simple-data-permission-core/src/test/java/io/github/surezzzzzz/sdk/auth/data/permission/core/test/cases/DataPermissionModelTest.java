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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class DataPermissionModelTest {

    private final DefaultDataPermissionEvaluator evaluator = new DefaultDataPermissionEvaluator();

    @Test
    void shouldNormalizeAndDefensivelyCopyProtocolModel() {
        List<String> values = new ArrayList<String>(Arrays.asList("value-b", "value-a", "value-a"));
        DataConstraint scopeB = new DataConstraint("scope_b", DataConstraintOperator.IN, values);
        DataConstraint scopeA = new DataConstraint("scope_a", DataConstraintOperator.IN,
                Collections.singletonList("value-a"));
        DataGrant grant = new DataGrant("test_resource", Arrays.asList("read", "export", "read"), false,
                Arrays.asList(scopeB, scopeA));
        DataGrantDocument document = document(grant);
        values.add("value-c");

        DataAccessPlan plan = evaluator.evaluate(document, new DataPermissionRequest("test_resource", "read"));
        log.info("规范化文档：{}，访问计划：{}", document, plan);
        assertEquals(Arrays.asList("export", "read"), grant.getActions(), "动作必须按规范顺序去重");
        assertEquals(Arrays.asList("scope_a", "scope_b"), Arrays.asList(grant.getConstraints().get(0).getDimension(),
                grant.getConstraints().get(1).getDimension()), "约束必须按维度排序");
        assertEquals(Arrays.asList("value-a", "value-b"), scopeB.getValues(), "约束值必须防御性拷贝并排序");
        assertEquals(DataAccessOutcome.ALLOW_RESTRICTED, plan.getOutcome(), "匹配受限授权必须生成受限计划");
        assertEquals(1, plan.getGrants().size(), "访问计划必须保留唯一命中子句");
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                () -> document.getGrants().add(grant), "授权文档集合必须不可修改");
        log.info("集合不可修改异常：{}", exception.toString());
    }

    @Test
    void shouldOnlyExposeEvidenceBasedAccessPlanApi() {
        for (Constructor<?> constructor : DataAccessPlan.class.getConstructors()) {
            assertFalse(Arrays.equals(new Class<?>[]{DataAccessOutcome.class, java.util.Collection.class},
                    constructor.getParameterTypes()), "不能公开接受结果和授权项的计划构造器");
        }
        for (Method method : DataAccessPlan.class.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                for (Class<?> type : method.getParameterTypes()) {
                    assertFalse(java.util.Collection.class.isAssignableFrom(type), "不能公开接受授权项集合的计划工厂");
                }
            }
        }
        DataGrant restrictedGrant = restrictedGrant("scope_a", "value-a");
        DataAccessPlan restrictedPlan = evaluator.evaluate(document(restrictedGrant),
                new DataPermissionRequest("test_resource", "read"));
        DataAccessPlan denyPlan = evaluator.evaluate(document(restrictedGrant),
                new DataPermissionRequest("other_resource", "read"));
        log.info("受限计划：{}，拒绝计划：{}", restrictedPlan, denyPlan);
        assertEquals(DataAccessOutcome.ALLOW_RESTRICTED, restrictedPlan.getOutcome(), "仅精确命中受限授权可产生受限计划");
        assertTrue(restrictedPlan.getGrants().stream().noneMatch(DataGrant::isAll), "受限计划不能包含全量授权");
        assertEquals(DataAccessOutcome.DENY, denyPlan.getOutcome(), "未命中授权必须拒绝");
        assertTrue(denyPlan.getGrants().isEmpty(), "拒绝计划不得携带授权项");
    }

    @Test
    void shouldRejectUnicodeWhitespaceSurrogateAndPatternInput() {
        assertInvalid(() -> new DataPermissionRequest(" test_resource", "read"), ErrorCode.INVALID_DOCUMENT,
                "NBSP首尾空白必须拒绝");
        assertInvalid(() -> new DataGrant("\uD800", Collections.singletonList("read"), true,
                Collections.<DataConstraint>emptyList()), ErrorCode.INVALID_GRANT, "孤立高代理项必须拒绝");
        assertInvalid(() -> new DataConstraint("scope_a", DataConstraintOperator.IN,
                Collections.singletonList("\uDC00")), ErrorCode.INVALID_CONSTRAINT, "孤立低代理项必须拒绝");
        assertInvalid(() -> new DataPermissionRequest("test_*", "read"), ErrorCode.INVALID_DOCUMENT,
                "模式字符必须拒绝");
        assertInvalid(() -> new DataConstraint("scope_a", DataConstraintOperator.IN,
                Collections.singletonList("#{currentUser}")), ErrorCode.INVALID_CONSTRAINT, "动态表达式必须拒绝");
        StringBuilder identifier = new StringBuilder();
        for (int index = 0; index < SimpleDataPermissionConstant.MAX_IDENTIFIER_CODE_POINT_COUNT; index++) {
            identifier.appendCodePoint(0x1F600);
        }
        log.info("验证合法代理对码点长度：{}", identifier.length());
        new DataGrant(identifier.toString(), Collections.singletonList("read"), true,
                Collections.<DataConstraint>emptyList());
    }

    @Test
    void shouldEnforceActualCollectionSnapshotBoundaries() {
        new DataGrant("test_resource", Collections.nCopies(SimpleDataPermissionConstant.MAX_ACTION_COUNT, "read"), true,
                Collections.<DataConstraint>emptyList());
        assertInvalid(() -> new DataGrant("test_resource", misleadingCollection("read",
                SimpleDataPermissionConstant.MAX_ACTION_COUNT + 1), true, Collections.<DataConstraint>emptyList()),
                ErrorCode.INVALID_GRANT, "动作实际数量超过上限必须拒绝");
        DataConstraint constraint = new DataConstraint("scope_a", DataConstraintOperator.IN,
                Collections.singletonList("value-a"));
        List<DataConstraint> maximumConstraints = new ArrayList<DataConstraint>();
        for (int index = 0; index < SimpleDataPermissionConstant.MAX_CONSTRAINT_COUNT; index++) {
            maximumConstraints.add(new DataConstraint("scope_" + index, DataConstraintOperator.IN,
                    Collections.singletonList("value-a")));
        }
        new DataGrant("test_resource", Collections.singletonList("read"), false, maximumConstraints);
        assertInvalid(() -> new DataGrant("test_resource", Collections.singletonList("read"), false,
                misleadingCollection(constraint, SimpleDataPermissionConstant.MAX_CONSTRAINT_COUNT + 1)),
                ErrorCode.INVALID_GRANT, "约束实际数量超过上限必须拒绝");
        new DataConstraint("scope_a", DataConstraintOperator.IN,
                Collections.nCopies(SimpleDataPermissionConstant.MAX_VALUE_COUNT, "value-a"));
        assertInvalid(() -> new DataConstraint("scope_a", DataConstraintOperator.IN,
                misleadingCollection("value-a", SimpleDataPermissionConstant.MAX_VALUE_COUNT + 1)),
                ErrorCode.INVALID_CONSTRAINT, "值实际数量超过上限必须拒绝");
        List<DataGrant> grants = new ArrayList<DataGrant>();
        for (int index = 0; index <= SimpleDataPermissionConstant.MAX_GRANT_COUNT; index++) {
            grants.add(new DataGrant("resource_" + index, Collections.singletonList("read"), true,
                    Collections.<DataConstraint>emptyList()));
        }
        new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                grants.subList(0, SimpleDataPermissionConstant.MAX_GRANT_COUNT));
        assertInvalid(() -> new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                SimpleDataPermissionConstant.VERSION, misleadingCollection(grants.get(0), grants)),
                ErrorCode.INVALID_DOCUMENT, "授权项实际数量超过上限必须拒绝");
    }

    @Test
    void shouldUseSnapshotForGrantConstraintSemantics() {
        DataConstraint constraint = new DataConstraint("scope_a", DataConstraintOperator.IN,
                Collections.singletonList("value-a"));
        assertInvalid(() -> new DataGrant("test_resource", Collections.singletonList("read"), true,
                collectionWithEmptySize(constraint)), ErrorCode.INVALID_GRANT,
                "全量授权必须以实际快照中的约束判断互斥");
        assertInvalid(() -> new DataGrant("test_resource", Collections.singletonList("read"), false,
                collectionWithEmptyIterator()), ErrorCode.INVALID_GRANT,
                "受限授权必须以实际快照非空约束判断");
    }

    private DataGrantDocument document(DataGrant grant) {
        return new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                Collections.singletonList(grant));
    }

    private DataGrant restrictedGrant(String dimension, String value) {
        return new DataGrant("test_resource", Collections.singletonList("read"), false,
                Collections.singletonList(new DataConstraint(dimension, DataConstraintOperator.IN,
                        Collections.singletonList(value))));
    }

    private <T> AbstractCollection<T> misleadingCollection(final T value, final int emittedCount) {
        return new AbstractCollection<T>() {
            @Override
            public Iterator<T> iterator() {
                return Collections.nCopies(emittedCount, value).iterator();
            }

            @Override
            public int size() {
                return 0;
            }
        };
    }

    private <T> AbstractCollection<T> collectionWithEmptySize(final T value) {
        return new AbstractCollection<T>() {
            @Override
            public Iterator<T> iterator() {
                return Collections.singletonList(value).iterator();
            }

            @Override
            public int size() {
                return 0;
            }
        };
    }

    private <T> AbstractCollection<T> collectionWithEmptyIterator() {
        return new AbstractCollection<T>() {
            @Override
            public Iterator<T> iterator() {
                return Collections.<T>emptyList().iterator();
            }

            @Override
            public int size() {
                return 1;
            }
        };
    }

    private <T> AbstractCollection<T> misleadingCollection(final T first, final List<T> values) {
        return new AbstractCollection<T>() {
            @Override
            public Iterator<T> iterator() {
                return values.iterator();
            }

            @Override
            public int size() {
                return 1;
            }
        };
    }

    private void assertInvalid(org.junit.jupiter.api.function.Executable executable, String errorCode, String message) {
        DataPermissionValidationException exception = assertThrows(DataPermissionValidationException.class, executable,
                message);
        log.info("校验异常：{}", exception.getMessage());
        assertEquals(errorCode, exception.getErrorCode(), message);
    }
}
