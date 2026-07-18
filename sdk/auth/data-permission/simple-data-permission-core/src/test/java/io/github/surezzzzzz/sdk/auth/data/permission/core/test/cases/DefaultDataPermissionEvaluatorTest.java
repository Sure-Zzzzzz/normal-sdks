package io.github.surezzzzzz.sdk.auth.data.permission.core.test.cases;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataAccessOutcome;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.exception.DataPermissionValidationException;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.*;
import io.github.surezzzzzz.sdk.auth.data.permission.core.spi.DataGrantDocumentSource;
import io.github.surezzzzzz.sdk.auth.data.permission.core.support.DefaultDataPermissionEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class DefaultDataPermissionEvaluatorTest {

    private final DefaultDataPermissionEvaluator evaluator = new DefaultDataPermissionEvaluator();

    @Test
    void shouldReturnAllowRestrictedWithoutFlatteningDnfClauses() {
        DataGrant firstGrant = restrictedGrant("scope_a", "value-a", "scope_b", "value-b");
        DataGrant secondGrant = restrictedGrant("scope_a", "value-c", "scope_b", "value-d");
        DataGrantDocument document = new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                SimpleDataPermissionConstant.VERSION, Arrays.asList(secondGrant, firstGrant));

        DataAccessPlan plan = evaluator.evaluate(document, new DataPermissionRequest("test_resource", "read"));

        log.info("受限访问计划：{}", plan);
        assertEquals(DataAccessOutcome.ALLOW_RESTRICTED, plan.getOutcome(), "匹配受限授权时必须受限放行");
        assertEquals(2, plan.getGrants().size(), "必须保留两个不可拆分的 DNF 子句");
        assertEquals("value-a", plan.getGrants().get(0).getConstraints().get(0).getValues().get(0),
                "首个子句必须保留自身的scope_a取值");
        assertEquals("value-b", plan.getGrants().get(0).getConstraints().get(1).getValues().get(0),
                "首个子句必须保留自身的scope_b取值");
        assertEquals("value-c", plan.getGrants().get(1).getConstraints().get(0).getValues().get(0),
                "第二个子句必须保留自身的scope_a取值");
        assertEquals("value-d", plan.getGrants().get(1).getConstraints().get(1).getValues().get(0),
                "第二个子句必须保留自身的scope_b取值");
    }

    @Test
    void shouldRejectNullEvaluationParameters() {
        DataPermissionValidationException documentException = assertThrows(DataPermissionValidationException.class,
                () -> evaluator.evaluate((DataGrantDocument) null, new DataPermissionRequest("test_resource", "read")),
                "授权文档不能为null");
        log.info("授权文档异常：{}", documentException.getMessage());
        assertEquals(ErrorCode.INVALID_DOCUMENT, documentException.getErrorCode(), "空授权文档必须使用文档错误码");

        DataGrantDocument document = document(allGrant());
        DataPermissionValidationException requestException = assertThrows(DataPermissionValidationException.class,
                () -> evaluator.evaluate(document, null), "授权请求不能为null");
        log.info("授权请求异常：{}", requestException.getMessage());
        assertEquals(ErrorCode.INVALID_DOCUMENT, requestException.getErrorCode(), "空授权请求必须使用文档错误码");
    }

    @Test
    void shouldFailClosedForMissingDocumentSource() {
        DataPermissionRequest request = new DataPermissionRequest("test_resource", "read");
        DataAccessPlan optionalPlan = evaluator.evaluate(Optional.<DataGrantDocument>empty(), request);
        DataAccessPlan sourcePlan = evaluator.evaluate(new DataGrantDocumentSource() {
            @Override
            public Optional<DataGrantDocument> currentDocument() {
                return Optional.empty();
            }
        }, request);
        log.info("可选文档计划：{}，来源文档计划：{}", optionalPlan, sourcePlan);
        assertEquals(DataAccessOutcome.DENY, optionalPlan.getOutcome(), "空Optional必须拒绝");
        assertTrue(optionalPlan.getGrants().isEmpty(), "空Optional拒绝计划不得携带授权项");
        assertEquals(optionalPlan, sourcePlan, "空来源与空Optional必须得到相同拒绝计划");
    }

    @Test
    void shouldPreserveSourceContractAndExceptionBoundaries() {
        DataPermissionRequest request = new DataPermissionRequest("test_resource", "read");
        DataGrantDocument document = document(allGrant());
        assertEquals(evaluator.evaluate(document, request), evaluator.evaluate(Optional.of(document), request),
                "存在文档的Optional必须复用直接评估语义");
        assertInvalid(() -> evaluator.evaluate((Optional<DataGrantDocument>) null, request), "null Optional必须拒绝");
        assertInvalid(() -> evaluator.evaluate((DataGrantDocumentSource) null, request), "null来源必须拒绝");
        assertInvalid(() -> evaluator.evaluate(new DataGrantDocumentSource() {
            @Override
            public Optional<DataGrantDocument> currentDocument() {
                return null;
            }
        }, request), "返回null Optional的来源必须拒绝");
        IllegalStateException sourceException = assertThrows(IllegalStateException.class,
                () -> evaluator.evaluate(new DataGrantDocumentSource() {
                    @Override
                    public Optional<DataGrantDocument> currentDocument() {
                        throw new IllegalStateException("来源不可用");
                    }
                }, request), "来源异常必须原样传播");
        log.info("来源异常：{}", sourceException.getMessage());

        AtomicBoolean invoked = new AtomicBoolean(false);
        assertInvalid(() -> evaluator.evaluate(new DataGrantDocumentSource() {
            @Override
            public Optional<DataGrantDocument> currentDocument() {
                invoked.set(true);
                return Optional.empty();
            }
        }, null), "空请求必须在读取来源前拒绝");
        assertFalse(invoked.get(), "空请求不得读取可能有副作用的来源");
    }

    @Test
    void shouldReturnAllowAllAndDenyWithEmptyMatchedGrants() {
        DataGrantDocument document = document(allGrant());
        DataAccessPlan allPlan = evaluator.evaluate(document, new DataPermissionRequest("test_resource", "read"));
        DataAccessPlan denyPlan = evaluator.evaluate(document, new DataPermissionRequest("test_resource", "READ"));

        log.info("全量访问计划：{}，拒绝访问计划：{}", allPlan, denyPlan);
        assertEquals(DataAccessOutcome.ALLOW_ALL, allPlan.getOutcome(), "匹配全量授权时必须全量放行");
        assertTrue(allPlan.getGrants().isEmpty(), "全量放行不应携带命中授权项");
        assertEquals(DataAccessOutcome.DENY, denyPlan.getOutcome(), "动作匹配必须区分大小写");
        assertTrue(denyPlan.getGrants().isEmpty(), "拒绝计划不应携带命中授权项");
    }

    private DataGrantDocument document(DataGrant grant) {
        return new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                Collections.singletonList(grant));
    }

    private DataGrant allGrant() {
        return new DataGrant("test_resource", Collections.singletonList("read"), true,
                Collections.<DataConstraint>emptyList());
    }

    private DataGrant restrictedGrant(String firstDimension, String firstValue, String secondDimension, String secondValue) {
        return new DataGrant("test_resource", Collections.singletonList("read"), false,
                Arrays.asList(new DataConstraint(firstDimension, DataConstraintOperator.IN,
                                Collections.singletonList(firstValue)),
                        new DataConstraint(secondDimension, DataConstraintOperator.IN,
                                Collections.singletonList(secondValue))));
    }

    private void assertInvalid(org.junit.jupiter.api.function.Executable executable, String message) {
        DataPermissionValidationException exception = assertThrows(DataPermissionValidationException.class, executable,
                message);
        log.info("校验异常：{}", exception.getMessage());
        assertEquals(ErrorCode.INVALID_DOCUMENT, exception.getErrorCode(), message);
    }
}
