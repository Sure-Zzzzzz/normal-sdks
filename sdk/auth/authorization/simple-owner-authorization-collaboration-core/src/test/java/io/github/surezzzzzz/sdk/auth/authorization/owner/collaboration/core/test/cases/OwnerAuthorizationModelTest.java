package io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.exception.OwnerAuthorizationCollaborationException;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 所属人授权协作模型测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class OwnerAuthorizationModelTest {

    @Test
    void shouldKeepOwnerKeyValueEqualityAndRejectMissingIdentity() {
        OwnerAuthorizationKey first = new OwnerAuthorizationKey("source-a", "subject-a");
        OwnerAuthorizationKey second = new OwnerAuthorizationKey("source-a", "subject-a");

        log.info("所属人键相等性：{}", first.equals(second));
        assertEquals(first, second, "相同身份源和主体标识必须得到相等的稳定键");
        assertEquals(first.hashCode(), second.hashCode(), "相等稳定键必须具有相同哈希值");
        assertInvalidModel(() -> new OwnerAuthorizationKey(null, "subject-a"), "缺少身份源时必须拒绝创建稳定键");
        assertInvalidModel(() -> new OwnerAuthorizationKey("", "subject-a"), "空身份源不能作为稳定键");
    }

    @Test
    void shouldExposeImmutableChangePageAndCandidateValues() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("authorization", "snapshot");
        OwnerAuthorizationChange change = new OwnerAuthorizationChange(1L, "event-a", "UPSERT", payload);
        OwnerAuthorizationChangePage page = new OwnerAuthorizationChangePage(true, false,
                Collections.singletonList(change), 1L, 1L, Instant.ofEpochSecond(100L));
        OwnerAuthorizationCandidate candidate = new OwnerAuthorizationCandidate(7L, "应用 A", "application-a");

        log.info("变更页和候选应用已创建：candidateId={}, changeCount={}", candidate.getApplicationId(),
                page.getChanges().size());
        assertEquals("snapshot", page.getChanges().get(0).getPayload().get("authorization"),
                "变更载荷必须保留中性授权字段");
        assertThrows(UnsupportedOperationException.class, () -> page.getChanges().clear(),
                "变更列表必须不可修改");
        assertThrows(UnsupportedOperationException.class, () -> page.getChanges().get(0).getPayload().put("x", "y"),
                "变更载荷必须不可修改");
        assertFalse(OwnerAuthorizationChangePage.unavailable().isAvailable(),
                "不可用结果必须明确标记为不可用");
    }

    @Test
    void shouldPreserveResyncWatermarks() {
        OwnerAuthorizationChangePage page = OwnerAuthorizationChangePage.resyncRequired(10L, 20L,
                Instant.ofEpochSecond(200L));

        log.info("重同步水位：low={}, high={}", page.getLowWatermark(), page.getHighWatermark());
        assertTrue(page.isAvailable(), "重同步结果仍表示远端可用");
        assertTrue(page.isResyncRequired(), "水位缺口必须显式要求重同步");
        assertEquals(Arrays.asList(), page.getChanges(), "要求重同步时不得伪装成变更空成功页");
        assertEquals(Long.valueOf(10L), page.getLowWatermark(), "必须保留最低可用水位");
        assertEquals(Long.valueOf(20L), page.getHighWatermark(), "必须保留最高水位");
    }

    @Test
    void shouldFreezeNestedPayloadAndCanonicalizeInactiveReadResult() {
        log.info("验证嵌套载荷不可变与无效投影状态收敛");
        Map<String, Object> nested = new LinkedHashMap<String, Object>();
        nested.put("permission", "read");
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("nested", nested);
        OwnerAuthorizationChange change = new OwnerAuthorizationChange(1L, "event-a", "OWNER_STATE", payload);

        nested.put("permission", "write");
        Map<String, Object> frozenNested = (Map<String, Object>) change.getPayload().get("nested");
        assertEquals("read", frozenNested.get("permission"), "嵌套载荷必须与 provider 后续修改隔离");
        assertThrows(UnsupportedOperationException.class, () -> frozenNested.put("x", "y"),
                "嵌套载荷也必须不可修改");

        OwnerAuthorizationReadResult inactive = new OwnerAuthorizationReadResult(false, 1L, 2L,
                3L, 4L, 5L, "owner", authorizationClaim("subject-a"));
        assertNull(inactive.getOwnerSecurityEpoch(), "无效结果不得保留旧纪元");
        assertNull(inactive.getOwnerUsername(), "无效结果不得保留归属展示信息");
        assertNull(inactive.getAuthorization(), "无效结果不得保留旧授权快照");
        assertNull(inactive.toApplicationAuthorizationContext(), "无效结果不得产生授权上下文");
    }

    @Test
    void shouldValidateAndExposeCanonicalActiveAuthorization() {
        log.info("验证有效投影的规范化授权上下文与 wire 字段边界");
        Map<String, Object> claim = new LinkedHashMap<String, Object>(authorizationClaim("subject-a"));
        claim.put("roles", new ArrayList<String>(Collections.singletonList("role-a")));
        OwnerAuthorizationReadResult result = new OwnerAuthorizationReadResult(true, 1L, 2L,
                3L, 4L, 5L, "owner-a", claim);

        ((List<String>) claim.get("roles")).add("role-b");
        assertEquals(Collections.singletonList("role-a"), result.toApplicationAuthorizationContext().getRoles(),
                "有效投影必须保存可验证的独立快照");
        assertThrows(UnsupportedOperationException.class,
                () -> ((List<String>) result.getAuthorization().get("roles")).add("role-c"),
                "规范化授权快照必须递归不可修改");
        assertInvalidModel(() -> new OwnerAuthorizationReadResult(true, 1L, 2L,
                        3L, 4L, 5L, "", authorizationClaim("subject-a")),
                "有效投影缺少归属展示名必须拒绝");
        assertInvalidModel(() -> new OwnerAuthorizationChangePage(true, true,
                        Collections.singletonList(new OwnerAuthorizationChange(1L, "event-a", "OWNER_STATE",
                                Collections.<String, Object>emptyMap())), 1L, 1L, Instant.now()),
                "要求重同步的页面不能携带增量变更");
        assertFalse(hasAuthorizationContextBeanProperty(),
                "派生上下文不能作为 JavaBean 属性进入协作 wire contract");
    }

    @Test
    void shouldRejectNonJsonPayloadAndNormalizeClaimValidationFailure() {
        log.info("验证非 JSON 载荷和畸形授权 claim 均按模块异常失败关闭");
        Map<String, Object> mutablePayload = new LinkedHashMap<String, Object>();
        mutablePayload.put("mutable", new StringBuilder("value"));
        assertInvalidModel(() -> new OwnerAuthorizationChange(1L, "event-a", "OWNER_STATE", mutablePayload),
                "可变对象不能伪装成 JSON 载荷");

        Map<String, Object> nonFinitePayload = new LinkedHashMap<String, Object>();
        nonFinitePayload.put("number", Double.NaN);
        assertInvalidModel(() -> new OwnerAuthorizationChange(1L, "event-a", "OWNER_STATE", nonFinitePayload),
                "非有限数值不能作为 JSON 载荷");

        Map<String, Object> invalidClaim = new LinkedHashMap<String, Object>();
        invalidClaim.put("unexpected", "value");
        assertInvalidModel(() -> new OwnerAuthorizationReadResult(true, 1L, 2L,
                        3L, 4L, 5L, "owner-a", invalidClaim),
                "下游 claim 解析失败必须转换为本模块异常");
    }

    @Test
    void shouldRejectInvalidBoundaryAndCyclicPayload() {
        log.info("验证模型边界和循环载荷均拒绝构造");
        assertInvalidModel(() -> new OwnerAuthorizationCandidate(0L, "应用 A", "application-a"),
                "目标应用标识必须为正数");
        assertInvalidModel(() -> new OwnerAuthorizationChange(-1L, "event-a", "OWNER_STATE",
                        Collections.<String, Object>emptyMap()),
                "变更顺序位点不能为负数");
        assertInvalidModel(() -> new OwnerAuthorizationChange(1L, "event-a", "OWNER_STATE", null),
                "变更载荷不能为空");
        assertInvalidModel(() -> new OwnerAuthorizationChangePage(true, false, Collections.emptyList(),
                        2L, 1L, Instant.ofEpochSecond(100L)),
                "最低水位不能超过最高水位");
        assertInvalidModel(() -> new OwnerAuthorizationChangePage(true, false, Collections.emptyList(),
                        1L, 1L, null),
                "可用变更页必须提供服务端时间");
        assertInvalidModel(() -> new OwnerAuthorizationReadResult(true, 1L, 2L,
                        3L, 4L, null, "owner-a", authorizationClaim("subject-a")),
                "有效投影必须提供恢复位点");

        List<Object> cyclicList = new ArrayList<Object>();
        cyclicList.add(cyclicList);
        Map<String, Object> cyclicPayload = new LinkedHashMap<String, Object>();
        cyclicPayload.put("cycle", cyclicList);
        assertInvalidModel(() -> new OwnerAuthorizationChange(1L, "event-a", "OWNER_STATE", cyclicPayload),
                "循环容器不能作为协作载荷");
    }

    private Map<String, Object> authorizationClaim(String subjectId) {
        ApplicationAuthorizationContext context = new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL, SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.HUMAN, subjectId, "application-a", true,
                Collections.singletonList("role-a"), Collections.singletonList("page-a"),
                Collections.singletonList("api-a"), null, 1L, "manifest-a", "digest-a",
                Instant.ofEpochSecond(100L), Instant.ofEpochSecond(200L));
        return ApplicationAuthorizationContextClaimMapper.toClaim(context);
    }

    private boolean hasAuthorizationContextBeanProperty() {
        try {
            return Arrays.stream(Introspector.getBeanInfo(OwnerAuthorizationReadResult.class)
                            .getPropertyDescriptors())
                    .anyMatch(descriptor -> "authorizationContext".equals(descriptor.getName()));
        } catch (IntrospectionException exception) {
            return fail("无法读取协作模型 JavaBean 属性", exception);
        }
    }

    private void assertInvalidModel(org.junit.jupiter.api.function.Executable executable, String message) {
        OwnerAuthorizationCollaborationException exception = assertThrows(OwnerAuthorizationCollaborationException.class,
                executable, message);
        assertEquals(ErrorCode.INVALID_OWNER_AUTHORIZATION_MODEL, exception.getErrorCode(),
                "协作模型校验必须返回稳定错误码");
    }
}
