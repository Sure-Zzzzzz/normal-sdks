package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRuleSelector;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterTypedManagementEventPayload;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicyKey;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicySnapshot;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类型化策略模型契约测试
 *
 * @author surezzzzzz
 */
@Slf4j
class SmartRedisLimiterTypedPolicyModelTest {

    private static SmartRedisLimiterLimit limit(long count, long window, String unit) {
        return new SmartRedisLimiterLimit(count, window,
                io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit.valueOf(unit));
    }

    private static SmartRedisLimiterTypedPolicyKey key(SmartRedisLimiterDataDimension dimension,
                                                       SmartRedisLimiterRuleSelector selector,
                                                       String customType,
                                                       String objectId) {
        return new SmartRedisLimiterTypedPolicyKey(
                "mock-service", "mock-resource", dimension, selector, "mock-namespace", customType, objectId);
    }

    @Test
    @DisplayName("CUSTOM 维度必须携带 customType，其他维度必须为 null")
    void testCustomTypeCombination() {
        assertThrows(SmartRedisLimiterException.class,
                () -> key(SmartRedisLimiterDataDimension.CUSTOM, SmartRedisLimiterRuleSelector.DEFAULT, null, null));
        assertThrows(SmartRedisLimiterException.class,
                () -> key(SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.DEFAULT, "device", null));

        SmartRedisLimiterTypedPolicyKey customKey = key(
                SmartRedisLimiterDataDimension.CUSTOM, SmartRedisLimiterRuleSelector.EXACT, "device", "box-01");
        assertEquals("device", customKey.getCustomType());
        assertEquals("box-01", customKey.getObjectId());
    }

    @Test
    @DisplayName("RESOURCE 只允许 DEFAULT，DEFAULT 不允许 objectId，EXACT 必须提供 objectId")
    void testSelectorCombination() {
        assertThrows(SmartRedisLimiterException.class,
                () -> key(SmartRedisLimiterDataDimension.RESOURCE, SmartRedisLimiterRuleSelector.EXACT, null, "x"));
        assertThrows(SmartRedisLimiterException.class,
                () -> key(SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.DEFAULT, null, "user-1"));

        SmartRedisLimiterTypedPolicyKey defaultKey = key(
                SmartRedisLimiterDataDimension.RESOURCE, SmartRedisLimiterRuleSelector.DEFAULT, null, null);
        assertNull(defaultKey.getObjectId());
        assertNull(defaultKey.getCustomType());
    }

    @Test
    @DisplayName("EXACT 对象保留原值：不 trim、保留大小写与前后空白")
    void testObjectIdPreservedAsIs() {
        String raw = " User-01 ";
        SmartRedisLimiterTypedPolicyKey typedKey = key(
                SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.EXACT, null, raw);
        assertEquals(raw, typedKey.getObjectId());

        assertThrows(SmartRedisLimiterException.class,
                () -> key(SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.EXACT, null, "   "));
    }

    @Test
    @DisplayName("EXACT 对象按码点限制 256 上限，补充平面字符不误判")
    void testObjectIdCodePointLimit() {
        StringBuilder supplementary = new StringBuilder();
        int codePoints = 0;
        while (codePoints < 256) {
            supplementary.appendCodePoint(0x1F600);
            codePoints++;
        }
        String within = supplementary.toString();
        assertEquals(256, within.codePointCount(0, within.length()));

        SmartRedisLimiterTypedPolicyKey withinKey = key(
                SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.EXACT, null, within);
        assertEquals(within, withinKey.getObjectId());

        String exceeded = within + "x";
        assertThrows(SmartRedisLimiterException.class,
                () -> key(SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.EXACT, null, exceeded));
    }

    @Test
    @DisplayName("命名空间与自定义类型按稳定编码格式校验且最多 64 字符")
    void testNamespaceAndCustomTypeFormat() {
        assertThrows(SmartRedisLimiterException.class,
                () -> new SmartRedisLimiterTypedPolicyKey("mock-service", "mock-resource",
                        SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.DEFAULT,
                        "bad namespace", null, null));
        StringBuilder longNamespace = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            longNamespace.append('a');
        }
        assertThrows(SmartRedisLimiterException.class,
                () -> new SmartRedisLimiterTypedPolicyKey("mock-service", "mock-resource",
                        SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.DEFAULT,
                        longNamespace.toString(), null, null));
    }

    @Test
    @DisplayName("规则限额窗口必须 1 到 16 个且窗口单位不重复")
    void testLimitsConstraint() {
        SmartRedisLimiterTypedPolicyKey typedKey = key(
                SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.EXACT, null, "user-1");
        assertThrows(SmartRedisLimiterException.class,
                () -> new SmartRedisLimiterTypedPolicy(typedKey, true, Collections.<SmartRedisLimiterLimit>emptyList()));

        assertThrows(SmartRedisLimiterException.class,
                () -> new SmartRedisLimiterTypedPolicy(typedKey, true,
                        Arrays.asList(limit(1, 60, "SECONDS"), limit(100, 1, "MINUTES"))));

        List<SmartRedisLimiterLimit> tooMany = new ArrayList<>();
        for (int i = 0; i < SmartRedisLimiterConstant.MAX_LIMITS_PER_POLICY + 1; i++) {
            tooMany.add(limit(i + 1, i + 1, "SECONDS"));
        }
        assertThrows(SmartRedisLimiterException.class,
                () -> new SmartRedisLimiterTypedPolicy(typedKey, true, tooMany));

        SmartRedisLimiterTypedPolicy policy = new SmartRedisLimiterTypedPolicy(typedKey, true,
                Arrays.asList(limit(10, 1, "SECONDS"), limit(100, 1, "MINUTES")));
        assertEquals(2, policy.getLimits().size());
        assertThrows(UnsupportedOperationException.class,
                () -> policy.getLimits().add(limit(1, 1, "SECONDS")));
    }

    @Test
    @DisplayName("类型化快照：schema 固定为 2、代次至少为 1、规则键唯一且服务匹配")
    void testTypedSnapshotContract() {
        SmartRedisLimiterTypedPolicy rule = new SmartRedisLimiterTypedPolicy(
                key(SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.EXACT, null, "user-1"),
                true, Collections.singletonList(limit(10, 1, "SECONDS")));

        assertThrows(SmartRedisLimiterException.class, () -> new SmartRedisLimiterTypedPolicySnapshot(
                SmartRedisLimiterConstant.POLICY_SCHEMA_VERSION, "mock-service", 1L, 1L, Instant.now(),
                Collections.singletonList(rule)));
        assertThrows(SmartRedisLimiterException.class, () -> new SmartRedisLimiterTypedPolicySnapshot(
                SmartRedisLimiterConstant.TYPED_POLICY_SCHEMA_VERSION, "mock-service", 0L, 1L, Instant.now(),
                Collections.singletonList(rule)));
        assertThrows(SmartRedisLimiterException.class, () -> new SmartRedisLimiterTypedPolicySnapshot(
                SmartRedisLimiterConstant.TYPED_POLICY_SCHEMA_VERSION, "mock-service", 1L, 1L, Instant.now(),
                Arrays.asList(rule, rule)));

        SmartRedisLimiterTypedPolicySnapshot snapshot = new SmartRedisLimiterTypedPolicySnapshot(
                SmartRedisLimiterConstant.TYPED_POLICY_SCHEMA_VERSION, "mock-service", 1L, 5L, Instant.now(),
                Collections.singletonList(rule));
        assertEquals(1L, snapshot.getPolicyEpoch());
        assertEquals(5L, snapshot.getRevision());
        assertEquals(1, snapshot.getRules().size());

        SmartRedisLimiterTypedPolicy otherService = new SmartRedisLimiterTypedPolicy(
                new SmartRedisLimiterTypedPolicyKey("other-service", "mock-resource",
                        SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.EXACT,
                        "mock-namespace", null, "user-1"),
                true, Collections.singletonList(limit(10, 1, "SECONDS")));
        SmartRedisLimiterException mismatch = assertThrows(SmartRedisLimiterException.class,
                () -> new SmartRedisLimiterTypedPolicySnapshot(
                        SmartRedisLimiterConstant.TYPED_POLICY_SCHEMA_VERSION, "mock-service", 1L, 1L, Instant.now(),
                        Collections.singletonList(otherService)));
        assertEquals(ErrorCode.TYPED_POLICY_SNAPSHOT_SERVICE_MISMATCH, mismatch.getErrorCode());
    }

    @Test
    @DisplayName("类型化事件载荷：结果枚举受控、attributes 不可变快照")
    void testTypedEventPayloadContract() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(SmartRedisLimiterConstant.OPERATOR_IDENTITY_ATTRIBUTE_KEY,
                Collections.singletonMap("schema", SmartRedisLimiterConstant.OPERATOR_IDENTITY_SCHEMA));

        SmartRedisLimiterTypedManagementEventPayload payload = new SmartRedisLimiterTypedManagementEventPayload(
                io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterManagementOperation.CREATE,
                "mock-service", "mock-resource", SmartRedisLimiterDataDimension.CUSTOMER,
                SmartRedisLimiterRuleSelector.EXACT, "d1g35t", 1L, 6L,
                SmartRedisLimiterConstant.TYPED_EVENT_RESULT_SUCCESS, null, Instant.now(), attributes);
        assertEquals(SmartRedisLimiterConstant.TYPED_EVENT_RESULT_SUCCESS, payload.getResult());
        assertThrows(UnsupportedOperationException.class, () -> payload.getAttributes().clear());

        assertThrows(SmartRedisLimiterException.class,
                () -> new SmartRedisLimiterTypedManagementEventPayload(
                        io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterManagementOperation.CREATE,
                        "mock-service", "mock-resource", SmartRedisLimiterDataDimension.CUSTOMER,
                        SmartRedisLimiterRuleSelector.EXACT, "d1g35t", 1L, 6L,
                        "MAYBE", null, Instant.now(), attributes));

        SmartRedisLimiterException missing = assertThrows(SmartRedisLimiterException.class,
                () -> new io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterTypedManagementEvent(
                        this, null));
        assertEquals(ErrorCode.TYPED_EVENT_PAYLOAD_INVALID, missing.getErrorCode());
    }

    @Test
    @DisplayName("不同字段组合的键互不相等")
    void testKeyEquality() {
        SmartRedisLimiterTypedPolicyKey first = key(
                SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.EXACT, null, "user-1");
        SmartRedisLimiterTypedPolicyKey second = key(
                SmartRedisLimiterDataDimension.CREDENTIAL, SmartRedisLimiterRuleSelector.EXACT, null, "user-1");
        assertNotEquals(first, second);
        assertTrue(SmartRedisLimiterDataDimension.isValid("CUSTOMER"));
        assertTrue(SmartRedisLimiterRuleSelector.isValid("DEFAULT"));
        assertTrue(SmartRedisLimiterConstant.TYPED_POLICY_SCHEMA_VERSION.equals("2"));
    }

    @Test
    @DisplayName("类型化规则与快照 JSON round-trip 保持契约")
    void testTypedJsonRoundTrip() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        SmartRedisLimiterTypedPolicy rule = new SmartRedisLimiterTypedPolicy(
                key(SmartRedisLimiterDataDimension.CUSTOM, SmartRedisLimiterRuleSelector.EXACT, "device", "box-01"),
                true, Arrays.asList(limit(10, 1, "SECONDS"), limit(100, 1, "MINUTES")));
        SmartRedisLimiterTypedPolicy parsedRule =
                mapper.readValue(mapper.writeValueAsString(rule), SmartRedisLimiterTypedPolicy.class);
        assertEquals(rule, parsedRule);

        SmartRedisLimiterTypedPolicySnapshot snapshot = new SmartRedisLimiterTypedPolicySnapshot(
                SmartRedisLimiterConstant.TYPED_POLICY_SCHEMA_VERSION, "mock-service", 1L, 5L,
                Instant.parse("2026-10-06T10:00:00Z"), Collections.singletonList(rule));
        SmartRedisLimiterTypedPolicySnapshot parsedSnapshot =
                mapper.readValue(mapper.writeValueAsString(snapshot), SmartRedisLimiterTypedPolicySnapshot.class);
        assertEquals(snapshot, parsedSnapshot);
        assertEquals(SmartRedisLimiterConstant.TYPED_POLICY_SCHEMA_VERSION, parsedSnapshot.getSchemaVersion());
        assertEquals(1L, parsedSnapshot.getPolicyEpoch());
    }
}
