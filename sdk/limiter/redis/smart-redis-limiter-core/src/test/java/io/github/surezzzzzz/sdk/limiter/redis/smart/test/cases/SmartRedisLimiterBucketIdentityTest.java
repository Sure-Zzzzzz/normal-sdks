package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRuleSelector;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicyKey;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterBucketIdentityHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类型化计数桶身份与操作人摘要 Helper 测试
 *
 * @author surezzzzzz
 */
@Slf4j
class SmartRedisLimiterBucketIdentityTest {

    private static SmartRedisLimiterTypedPolicyKey key(SmartRedisLimiterDataDimension dimension,
                                                       SmartRedisLimiterRuleSelector selector,
                                                       String customType,
                                                       String objectId) {
        return new SmartRedisLimiterTypedPolicyKey(
                "mock-service", "mock-resource", dimension, selector, "mock-namespace", customType, objectId);
    }

    @Test
    @DisplayName("相同输入得到相同摘要，跨调用稳定")
    void testDigestDeterministic() {
        String first = SmartRedisLimiterBucketIdentityHelper.digest(
                key(SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.EXACT, null, "user-1"));
        String second = SmartRedisLimiterBucketIdentityHelper.digest(
                key(SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.EXACT, null, "user-1"));
        assertEquals(first, second);
        assertEquals(64, first.length());
        assertTrue(first.equals(first.toLowerCase()));
    }

    @Test
    @DisplayName("维度、对象、命名空间任一不同则摘要不同；长度前缀避免拼接歧义")
    void testDigestDistinguishesFields() {
        String user = SmartRedisLimiterBucketIdentityHelper.digest(
                key(SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.EXACT, null, "user-1"));
        String credential = SmartRedisLimiterBucketIdentityHelper.digest(
                key(SmartRedisLimiterDataDimension.CREDENTIAL, SmartRedisLimiterRuleSelector.EXACT, null, "user-1"));
        String otherObject = SmartRedisLimiterBucketIdentityHelper.digest(
                key(SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.EXACT, null, "user-2"));
        assertNotEquals(user, credential);
        assertNotEquals(user, otherObject);

        // 长度前缀编码保证 (ab,c) 与 (a,bc) 不合并为同一摘要
        SmartRedisLimiterTypedPolicyKey firstPair = new SmartRedisLimiterTypedPolicyKey(
                "mock-service", "ab", SmartRedisLimiterDataDimension.USER,
                SmartRedisLimiterRuleSelector.EXACT, "n", null, "c");
        SmartRedisLimiterTypedPolicyKey secondPair = new SmartRedisLimiterTypedPolicyKey(
                "mock-service", "a", SmartRedisLimiterDataDimension.USER,
                SmartRedisLimiterRuleSelector.EXACT, "n", null, "bc");
        assertNotEquals(SmartRedisLimiterBucketIdentityHelper.digest(firstPair),
                SmartRedisLimiterBucketIdentityHelper.digest(secondPair));
    }

    @Test
    @DisplayName("RESOURCE 维度使用固定共享对象：同资源所有 DEFAULT 规则同桶")
    void testResourceSharedBucket() {
        String resource = SmartRedisLimiterBucketIdentityHelper.digest(
                key(SmartRedisLimiterDataDimension.RESOURCE, SmartRedisLimiterRuleSelector.DEFAULT, null, null));
        assertNotNull(resource);
        String sameResource = SmartRedisLimiterBucketIdentityHelper.digest(
                new SmartRedisLimiterTypedPolicyKey("mock-service", "mock-resource",
                        SmartRedisLimiterDataDimension.RESOURCE, SmartRedisLimiterRuleSelector.DEFAULT,
                        "mock-namespace", null, null));
        assertEquals(resource, sameResource);
    }

    @Test
    @DisplayName("未配对代理字符按原始字符单元编码，不同代理不合并")
    void testUnpairedSurrogatePreserved() {
        String withHighSurrogate = "user-\uD83D";
        String withLowSurrogate = "user-\uDE00";
        String first = SmartRedisLimiterBucketIdentityHelper.digest(
                key(SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.EXACT, null,
                        withHighSurrogate));
        String second = SmartRedisLimiterBucketIdentityHelper.digest(
                key(SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.EXACT, null,
                        withLowSurrogate));
        assertNotEquals(first, second);

        String repeat = SmartRedisLimiterBucketIdentityHelper.digest(
                key(SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.EXACT, null,
                        withHighSurrogate));
        assertEquals(first, repeat);
    }

    @Test
    @DisplayName("前后空白保留：不同空白不合并为同一桶")
    void testWhitespacePreserved() {
        String plain = SmartRedisLimiterBucketIdentityHelper.digest(
                key(SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.EXACT, null, "user-1"));
        String padded = SmartRedisLimiterBucketIdentityHelper.digest(
                key(SmartRedisLimiterDataDimension.USER, SmartRedisLimiterRuleSelector.EXACT, null, " user-1 "));
        assertNotEquals(plain, padded);
    }

    @Test
    @DisplayName("操作人摘要固定 83 字符并带 resource:v1:sha256: 前缀，三元组任一不同则不同")
    void testOperatorDigest() {
        String digest = SmartRedisLimiterBucketIdentityHelper.operatorDigest("iam", "HUMAN", "user-1");
        assertTrue(digest.startsWith(SmartRedisLimiterConstant.OPERATOR_DIGEST_PREFIX));
        assertEquals(83, digest.length());

        assertEquals(digest, SmartRedisLimiterBucketIdentityHelper.operatorDigest("iam", "HUMAN", "user-1"));
        assertNotEquals(digest, SmartRedisLimiterBucketIdentityHelper.operatorDigest("aksk", "HUMAN", "user-1"));
        assertNotEquals(digest, SmartRedisLimiterBucketIdentityHelper.operatorDigest("iam", "SERVICE", "user-1"));
        assertNotEquals(digest, SmartRedisLimiterBucketIdentityHelper.operatorDigest("iam", "HUMAN", "user-2"));

        assertThrows(Exception.class,
                () -> SmartRedisLimiterBucketIdentityHelper.operatorDigest(null, "HUMAN", "user-1"));
        assertThrows(Exception.class,
                () -> SmartRedisLimiterBucketIdentityHelper.operatorDigest("iam", " ", "user-1"));
    }
}
