package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicyKey;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.AtomicSmartRedisLimiterPolicySnapshotStore;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.DefaultSmartRedisLimiterPolicyResolver;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.DefaultSmartRedisLimiterPolicySnapshotValidator;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.SmartRedisLimiterPolicyResolution;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.model.SmartRedisLimiterAcceptedPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterKeyHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterPolicyDigestHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 动态策略核心契约测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterPolicyTest {

    @Test
    public void testResolverReplacesCompleteLimitList() {
        SmartRedisLimiterAcceptedPolicySnapshot accepted = acceptedSnapshot(snapshot(1L, 3L, 5L), "\"v1\"");
        SmartRedisLimiterProperties.SmartLimitRule local = localLimit(100L, 60L);

        SmartRedisLimiterPolicyResolution resolution = new DefaultSmartRedisLimiterPolicyResolver().resolve(
                accepted, "test-service", "query", "tenant-a", Collections.singletonList(local));

        log.info("远程策略解析结果: source={}, revision={}, limits={}",
                resolution.getPolicySource(), resolution.getPolicyRevision(), resolution.getLimits());
        assertEquals(SmartRedisLimiterConstant.POLICY_SOURCE_REMOTE, resolution.getPolicySource(),
                "精确命中应整体使用远程策略");
        assertEquals(1L, resolution.getPolicyRevision(), "应携带本次请求读取的快照版本");
        assertEquals(1, resolution.getLimits().size(), "远程 limits 应整体替换本地列表");
        assertEquals(3L, resolution.getLimits().get(0).getCount(), "最终阈值应来自远程策略");
    }

    @Test
    public void testResolverFallsBackToLocalWithoutMatch() {
        SmartRedisLimiterAcceptedPolicySnapshot accepted = acceptedSnapshot(snapshot(1L, 3L, 5L), "\"v1\"");
        SmartRedisLimiterProperties.SmartLimitRule local = localLimit(100L, 60L);

        SmartRedisLimiterPolicyResolution resolution = new DefaultSmartRedisLimiterPolicyResolver().resolve(
                accepted, "test-service", "query", "tenant-b", Collections.singletonList(local));

        assertEquals(SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL, resolution.getPolicySource(),
                "未命中应使用本地策略");
        assertEquals(100L, resolution.getLimits().get(0).getCount(), "本地 limits 不应被修改");
    }

    @Test
    public void testStoreReplacesWholeSnapshotAtomically() {
        AtomicSmartRedisLimiterPolicySnapshotStore store = new AtomicSmartRedisLimiterPolicySnapshotStore();
        SmartRedisLimiterAcceptedPolicySnapshot first = acceptedSnapshot(snapshot(1L, 3L, 5L), "\"v1\"");
        SmartRedisLimiterAcceptedPolicySnapshot second = acceptedSnapshot(snapshot(2L, 7L, 5L), "\"v2\"");

        store.replace(first);
        assertSame(first, store.getCurrent(), "第一次应保存完整快照引用");
        store.replace(second);
        assertSame(second, store.getCurrent(), "替换必须一次切换到完整新快照");
    }

    @Test
    public void testValidatorRejectsRollbackAndSameRevisionDrift() {
        SmartRedisLimiterProperties properties = remoteProperties();
        DefaultSmartRedisLimiterPolicySnapshotValidator validator =
                new DefaultSmartRedisLimiterPolicySnapshotValidator(properties);
        SmartRedisLimiterAcceptedPolicySnapshot current = validator.validate(
                snapshot(2L, 3L, 5L), "\"v2\"", null, Instant.now());

        assertThrows(RuntimeException.class,
                () -> validator.validate(snapshot(1L, 3L, 5L), "\"v1\"", current, Instant.now()),
                "快照版本回退必须拒绝");
        assertThrows(RuntimeException.class,
                () -> validator.validate(snapshot(2L, 4L, 5L), "\"v2b\"", current, Instant.now()),
                "同版本内容漂移必须拒绝");
    }

    @Test
    public void testValidatorRejectsServiceCodeAndEtagViolations() {
        DefaultSmartRedisLimiterPolicySnapshotValidator validator =
                new DefaultSmartRedisLimiterPolicySnapshotValidator(remoteProperties());

        SmartRedisLimiterException mismatch = assertThrows(SmartRedisLimiterException.class,
                () -> validator.validate(snapshotFor("other-service", 1L, 3L, 5L),
                        "\"v1\"", null, Instant.now()),
                "服务编码不一致必须拒绝");
        assertEquals(ErrorCode.POLICY_SNAPSHOT_INVALID, mismatch.getErrorCode(),
                "服务编码不一致必须使用快照校验错误码");

        SmartRedisLimiterException invalidEtag = assertThrows(SmartRedisLimiterException.class,
                () -> validator.validate(snapshot(1L, 3L, 5L), "invalid", null, Instant.now()),
                "非法 ETag 必须拒绝");
        assertEquals(ErrorCode.POLICY_SNAPSHOT_INVALID, invalidEtag.getErrorCode(),
                "非法 ETag 必须使用快照校验错误码");
    }

    @Test
    public void testValidatorRejectsConfiguredPolicyAndLimitCaps() {
        SmartRedisLimiterProperties properties = remoteProperties();
        properties.getRemotePolicy().setMaxPolicyCount(1);
        properties.getRemotePolicy().setMaxLimitsPerPolicy(1);
        DefaultSmartRedisLimiterPolicySnapshotValidator validator =
                new DefaultSmartRedisLimiterPolicySnapshotValidator(properties);

        SmartRedisLimiterPolicy first = policy("test-service", "query-a", "tenant-a",
                new SmartRedisLimiterLimit(1L, 1L, SmartRedisLimiterTimeUnit.SECONDS));
        SmartRedisLimiterPolicy second = policy("test-service", "query-b", "tenant-b",
                new SmartRedisLimiterLimit(1L, 1L, SmartRedisLimiterTimeUnit.SECONDS));
        SmartRedisLimiterException policyCount = assertThrows(SmartRedisLimiterException.class,
                () -> validator.validate(new SmartRedisLimiterPolicySnapshot(
                        SmartRedisLimiterConstant.POLICY_SCHEMA_VERSION,
                        "test-service", 1L, Instant.parse("2026-07-22T00:00:00Z"),
                        Arrays.asList(first, second)), "\"v1\"", null, Instant.now()),
                "策略数量超过配置上限必须拒绝");
        assertEquals(ErrorCode.POLICY_SNAPSHOT_INVALID, policyCount.getErrorCode(),
                "策略数量超限必须使用快照校验错误码");

        SmartRedisLimiterPolicy multipleLimits = policy("test-service", "query", "tenant-a",
                new SmartRedisLimiterLimit(1L, 1L, SmartRedisLimiterTimeUnit.SECONDS),
                new SmartRedisLimiterLimit(2L, 1L, SmartRedisLimiterTimeUnit.MINUTES));
        SmartRedisLimiterException limitCount = assertThrows(SmartRedisLimiterException.class,
                () -> validator.validate(new SmartRedisLimiterPolicySnapshot(
                        SmartRedisLimiterConstant.POLICY_SCHEMA_VERSION,
                        "test-service", 1L, Instant.parse("2026-07-22T00:00:00Z"),
                        Collections.singletonList(multipleLimits)), "\"v1\"", null, Instant.now()),
                "窗口数量超过配置上限必须拒绝");
        assertEquals(ErrorCode.POLICY_SNAPSHOT_INVALID, limitCount.getErrorCode(),
                "窗口数量超限必须使用快照校验错误码");
    }

    @Test
    public void testValidatorRejectsEtagReuseAndLuaUnsafeWindow() {
        DefaultSmartRedisLimiterPolicySnapshotValidator validator =
                new DefaultSmartRedisLimiterPolicySnapshotValidator(remoteProperties());
        SmartRedisLimiterAcceptedPolicySnapshot current = validator.validate(
                snapshot(1L, 3L, 5L), "\"v1\"", null, Instant.now());

        SmartRedisLimiterException etagReuse = assertThrows(SmartRedisLimiterException.class,
                () -> validator.validate(snapshot(2L, 3L, 5L), "\"v1\"", current, Instant.now()),
                "新 revision 复用 ETag 必须拒绝");
        assertEquals(ErrorCode.POLICY_REVISION_CONFLICT, etagReuse.getErrorCode(),
                "ETag 复用必须使用 revision 冲突错误码");

        long unsafeWindow = SmartRedisLimiterStarterConstant.LUA_MAX_SAFE_INTEGER
                / SmartRedisLimiterStarterConstant.MICROSECONDS_PER_SECOND + 1L;
        SmartRedisLimiterException unsafeWindowException = assertThrows(SmartRedisLimiterException.class,
                () -> validator.validate(snapshot(2L, 1L, unsafeWindow), "\"v2\"", null, Instant.now()),
                "Lua 微秒窗口溢出必须拒绝");
        assertEquals(ErrorCode.POLICY_SNAPSHOT_INVALID, unsafeWindowException.getErrorCode(),
                "Lua 安全整数超限必须使用快照校验错误码");
    }

    @Test
    public void testCanonicalDigestIgnoresEquivalentUnitRepresentation() {
        SmartRedisLimiterPolicySnapshot seconds = snapshotWithLimit(
                1L, new SmartRedisLimiterLimit(3L, 60L, SmartRedisLimiterTimeUnit.SECONDS));
        SmartRedisLimiterPolicySnapshot minutes = snapshotWithLimit(
                1L, new SmartRedisLimiterLimit(3L, 1L, SmartRedisLimiterTimeUnit.MINUTES));

        String secondsDigest = SmartRedisLimiterPolicyDigestHelper.sha256(seconds);
        String minutesDigest = SmartRedisLimiterPolicyDigestHelper.sha256(minutes);
        log.info("等价窗口摘要: seconds={}, minutes={}", secondsDigest, minutesDigest);
        assertEquals(secondsDigest, minutesDigest, "等价时间单位应产生相同 canonical 摘要");
    }

    @Test
    public void testPolicyKeyContainsOnlySubjectHash() {
        String subject = "tenant-sensitive-value";
        String key = SmartRedisLimiterKeyHelper.buildPolicyBaseKey("test-service", "query", subject);

        log.info("动态策略基础 Key: {}", key);
        assertFalse(key.contains(subject), "Redis Key 绝不能包含原始 subject");
        assertTrue(key.endsWith(SmartRedisLimiterKeyHelper.sha256(subject)), "Key 应以 subject SHA-256 结尾");
        assertNotEquals(subject, SmartRedisLimiterKeyHelper.sha256(subject), "摘要不得等于原文");
    }

    private SmartRedisLimiterProperties remoteProperties() {
        SmartRedisLimiterProperties properties = new SmartRedisLimiterProperties();
        properties.setMe("test-service");
        properties.getRemotePolicy().setEnable(true);
        properties.getRemotePolicy().setSnapshotUrl("http://management.internal/api/v1/policy/snapshot");
        return properties;
    }

    private SmartRedisLimiterAcceptedPolicySnapshot acceptedSnapshot(
            SmartRedisLimiterPolicySnapshot snapshot, String etag) {
        return new SmartRedisLimiterAcceptedPolicySnapshot(
                snapshot,
                etag,
                SmartRedisLimiterPolicyDigestHelper.sha256(snapshot),
                Instant.now());
    }

    private SmartRedisLimiterPolicySnapshot snapshot(long revision, long count, long windowSeconds) {
        return snapshotWithLimit(revision,
                new SmartRedisLimiterLimit(count, windowSeconds, SmartRedisLimiterTimeUnit.SECONDS));
    }

    private SmartRedisLimiterPolicySnapshot snapshotFor(
            String serviceCode, long revision, long count, long windowSeconds) {
        return new SmartRedisLimiterPolicySnapshot(
                SmartRedisLimiterConstant.POLICY_SCHEMA_VERSION,
                serviceCode,
                revision,
                Instant.parse("2026-07-18T00:00:00Z"),
                Collections.singletonList(policy(serviceCode, "query", "tenant-a",
                        new SmartRedisLimiterLimit(count, windowSeconds, SmartRedisLimiterTimeUnit.SECONDS))));
    }

    private SmartRedisLimiterPolicySnapshot snapshotWithLimit(long revision, SmartRedisLimiterLimit limit) {
        return new SmartRedisLimiterPolicySnapshot(
                SmartRedisLimiterConstant.POLICY_SCHEMA_VERSION,
                "test-service",
                revision,
                Instant.parse("2026-07-18T00:00:00Z"),
                Collections.singletonList(policy("test-service", "query", "tenant-a", limit)));
    }

    private SmartRedisLimiterPolicy policy(
            String serviceCode, String resourceCode, String subject, SmartRedisLimiterLimit... limits) {
        return new SmartRedisLimiterPolicy(
                new SmartRedisLimiterPolicyKey(serviceCode, resourceCode, subject),
                Arrays.asList(limits));
    }

    private SmartRedisLimiterProperties.SmartLimitRule localLimit(long count, long windowSeconds) {
        SmartRedisLimiterProperties.SmartLimitRule rule = new SmartRedisLimiterProperties.SmartLimitRule();
        rule.setCount(count);
        rule.setWindow(windowSeconds);
        rule.setUnit(SmartRedisLimiterTimeUnit.SECONDS);
        return rule;
    }
}
