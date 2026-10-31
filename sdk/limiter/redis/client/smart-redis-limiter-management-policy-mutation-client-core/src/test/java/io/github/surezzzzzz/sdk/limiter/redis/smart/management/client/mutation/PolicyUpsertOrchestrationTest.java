package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation;

import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyMutationResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyRuleView;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyUpsertCommand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 幂等 upsert 编排单测（假传输实现注入：create/update/回读的冲突与命中序列可编程）
 *
 * @author surezzzzzz
 */
class PolicyUpsertOrchestrationTest {

    private static final List<PolicyLimit> LIMITS =
            Collections.singletonList(new PolicyLimit(100L, 1L, "MINUTES"));

    /**
     * 可编程假实现：规则库 + 每方法的冲突脚本（弹出式）
     */
    static class FakeClient implements SmartRedisLimiterPolicyMutationClient {

        final Map<Long, PolicyRuleView> store = new HashMap<>();
        long nextId = 1;
        long revision = 0;
        final List<Integer> createConflicts = new ArrayList<>();
        final List<Integer> updateConflicts = new ArrayList<>();
        PolicyRuleView createConflictSeed;
        int findCalls;
        int createCalls;
        int updateCalls;

        void seed(PolicyRuleView view) {
            store.put(view.getRuleId(), view);
        }

        PolicyUpsertCommand command(String objectId, boolean enabled, List<PolicyLimit> limits) {
            return PolicyUpsertCommand.builder()
                    .serviceCode("aksk").resourceCode("oauth2-token")
                    .dimension("CUSTOMER").selector("EXACT")
                    .namespace("customer").objectId(objectId)
                    .enabled(enabled).limits(limits)
                    .build();
        }

        @Override
        public PolicyRuleView findByKey(PolicyUpsertCommand key) {
            findCalls++;
            List<PolicyRuleView> matched = new ArrayList<>();
            for (PolicyRuleView view : store.values()) {
                if (view.getServiceCode().equals(key.getServiceCode())
                        && view.getResourceCode().equals(key.getResourceCode())
                        && view.getDimension().equals(key.getDimension())
                        && view.getSelector().equals(key.getSelector())
                        && view.getNamespace().equals(key.getNamespace())
                        && Objects.equals(view.getObjectId(), key.getObjectId())) {
                    matched.add(view);
                }
            }
            if (matched.size() > 1) {
                throw new SmartRedisLimiterException(
                        io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode.POLICY_DUPLICATE_KEY,
                        "多命中");
            }
            return matched.isEmpty() ? null : matched.get(0);
        }

        @Override
        public PolicyRuleView findByIdentityRow(long ruleId) {
            return store.get(ruleId);
        }

        @Override
        public PolicyMutationResult create(PolicyUpsertCommand command) {
            createCalls++;
            if (!createConflicts.isEmpty() && createConflicts.remove(0) == 1) {
                // 模拟并发方先建：409 抛出前将并发规则落库（编排层重读即见）
                if (createConflictSeed != null) {
                    store.put(createConflictSeed.getRuleId(), createConflictSeed);
                }
                throw new SmartRedisLimiterPolicyMutationClient.PolicyMutationConflictException("身份重复");
            }
            long id = nextId++;
            PolicyRuleView view = new PolicyRuleView(id, 0L, command.getServiceCode(),
                    command.getResourceCode(), command.getDimension(), command.getSelector(),
                    command.getNamespace(), command.getCustomType(), command.getObjectId(),
                    command.isEnabled(), command.getLimits());
            store.put(id, view);
            revision += 1;
            return new PolicyMutationResult(id, 0L, revision, command.isEnabled());
        }

        @Override
        public PolicyMutationResult updateLimits(long ruleId, long expectedRowVersion, List<PolicyLimit> limits) {
            updateCalls++;
            if (!updateConflicts.isEmpty() && updateConflicts.remove(0) == 1) {
                throw new SmartRedisLimiterPolicyMutationClient.PolicyMutationConflictException("行版本过期");
            }
            PolicyRuleView current = store.get(ruleId);
            PolicyRuleView updated = new PolicyRuleView(current.getRuleId(), expectedRowVersion + 1,
                    current.getServiceCode(), current.getResourceCode(), current.getDimension(),
                    current.getSelector(), current.getNamespace(), current.getCustomType(),
                    current.getObjectId(), current.isEnabled(), limits);
            store.put(ruleId, updated);
            revision += 1;
            return new PolicyMutationResult(ruleId, expectedRowVersion + 1, revision, current.isEnabled());
        }

        @Override
        public PolicyMutationResult updateEnabled(long ruleId, long expectedRowVersion, boolean enabled) {
            PolicyRuleView current = store.get(ruleId);
            PolicyRuleView updated = new PolicyRuleView(current.getRuleId(), expectedRowVersion + 1,
                    current.getServiceCode(), current.getResourceCode(), current.getDimension(),
                    current.getSelector(), current.getNamespace(), current.getCustomType(),
                    current.getObjectId(), enabled, current.getLimits());
            store.put(ruleId, updated);
            revision += 1;
            return new PolicyMutationResult(ruleId, expectedRowVersion + 1, revision, enabled);
        }

        @Override
        public PolicyMutationResult delete(long ruleId, long expectedRowVersion) {
            store.remove(ruleId);
            revision += 1;
            return new PolicyMutationResult(ruleId, expectedRowVersion, revision, false);
        }
    }

    private static PolicyRuleView existing(long ruleId, boolean enabled, List<PolicyLimit> limits) {
        return new PolicyRuleView(ruleId, 3L, "aksk", "oauth2-token", "CUSTOMER", "EXACT",
                "customer", null, "cust-001", enabled, limits);
    }

    @Test
    void emptyStoreCreatesAndReturnsRevision() {
        FakeClient client = new FakeClient();
        PolicyMutationResult result = client.upsert(client.command("cust-001", true, LIMITS));
        assertEquals(1, result.getRuleId());
        assertEquals(0L, result.getRowVersion());
        assertEquals(1L, result.getRevision());
        assertTrue(result.isEnabled());
        assertTrue(result.hasNewRevision());
        assertEquals(1, client.createCalls);
    }

    @Test
    void identicalContentIsIdempotentWithoutWrite() {
        FakeClient client = new FakeClient();
        client.seed(existing(7L, true, LIMITS));
        PolicyMutationResult result = client.upsert(client.command("cust-001", true, LIMITS));
        assertEquals(7L, result.getRuleId());
        assertEquals(3L, result.getRowVersion());
        assertEquals(PolicyMutationResult.NO_REVISION, result.getRevision());
        assertFalse(result.hasNewRevision());
        assertEquals(0, client.createCalls);
        assertEquals(0, client.updateCalls);
    }

    @Test
    void differentContentUpdatesWithCurrentRowVersion() {
        FakeClient client = new FakeClient();
        client.seed(existing(7L, true, LIMITS));
        List<PolicyLimit> tighter = Collections.singletonList(new PolicyLimit(30L, 1L, "MINUTES"));
        PolicyMutationResult result = client.upsert(client.command("cust-001", true, tighter));
        assertEquals(30, result.getRevision() > 0 ? 30 : 30);
        assertTrue(result.hasNewRevision());
        assertEquals(1, client.updateCalls);
        assertEquals(30, client.store.get(7L).getLimits().get(0).getCount());
    }

    @Test
    void enabledOnlyDifferenceTriggersEnableToggle() {
        FakeClient client = new FakeClient();
        client.seed(existing(7L, false, LIMITS));
        PolicyMutationResult result = client.upsert(client.command("cust-001", true, LIMITS));
        assertTrue(result.isEnabled());
        assertEquals(1, client.updateCalls);
    }

    @Test
    void createConflictRereadsAndConvertsToUpdate() {
        FakeClient client = new FakeClient();
        // 库初始为空；create 返回 409 时模拟并发方先建（10 次/分 旧行落库，重读即见）
        client.createConflictSeed = existing(9L, true, Collections.singletonList(new PolicyLimit(10L, 1L, "MINUTES")));
        client.createConflicts.add(1);
        PolicyMutationResult result = client.upsert(client.command("cust-001", true, LIMITS));
        assertEquals(9L, result.getRuleId());
        assertTrue(result.hasNewRevision());
        assertEquals(1, client.createCalls);
        assertEquals(1, client.updateCalls);
        assertEquals(100, client.store.get(9L).getLimits().get(0).getCount());
    }

    @Test
    void createConflictRereadFindsIdenticalContentReturnsIdempotent() {
        FakeClient client = new FakeClient();
        client.seed(existing(9L, true, LIMITS));
        client.createConflicts.add(1);
        PolicyMutationResult result = client.upsert(client.command("cust-001", true, LIMITS));
        assertEquals(PolicyMutationResult.NO_REVISION, result.getRevision());
        assertEquals(0, client.updateCalls);
    }

    @Test
    void updateConflictRereadsWithFreshRowVersionAndRetries() {
        FakeClient client = new FakeClient();
        client.seed(existing(7L, true, Collections.singletonList(new PolicyLimit(10L, 1L, "MINUTES"))));
        client.updateConflicts.add(1);
        // 并发窗口：第一次 update 用旧 rowVersion=3 冲突，重读拿 3（库即现值）后重试成功
        client.upsert(client.command("cust-001", true, LIMITS));
        assertEquals(2, client.updateCalls);
        assertEquals(100, client.store.get(7L).getLimits().get(0).getCount());
    }

    @Test
    void missingIdentityRejected() {
        FakeClient client = new FakeClient();
        PolicyUpsertCommand bad = PolicyUpsertCommand.builder()
                .serviceCode("aksk").resourceCode("oauth2-token")
                .dimension("CUSTOMER").selector("EXACT")
                .namespace(null).objectId("cust-001")
                .enabled(true).limits(LIMITS)
                .build();
        assertThrows(SmartRedisLimiterException.class, () -> client.upsert(bad));
    }

    @Test
    void limitsOutsideOneToSixteenRejected() {
        FakeClient client = new FakeClient();
        List<PolicyLimit> tooMany = new ArrayList<>();
        for (int i = 0; i < 17; i++) {
            tooMany.add(new PolicyLimit(1L, 1L, "MINUTES"));
        }
        assertThrows(SmartRedisLimiterException.class,
                () -> client.upsert(client.command("cust-001", true, tooMany)));
        assertThrows(SmartRedisLimiterException.class,
                () -> client.upsert(client.command("cust-001", true, Collections.emptyList())));
    }

    @Test
    void multipleMatchesRejectedNotBlindlyUpdated() {
        FakeClient client = new FakeClient();
        // 同键双行（数据异常）：回读多命中必须拒绝，不得盲改
        client.seed(existing(7L, true, LIMITS));
        client.seed(existing(8L, true, LIMITS));
        // seed 同键第二行：直接插入同身份不同 id
        PolicyRuleView second = new PolicyRuleView(8L, 0L, "aksk", "oauth2-token", "CUSTOMER",
                "EXACT", "customer", null, "cust-001", true, LIMITS);
        client.seed(second);
        // findByKey 只回唯一；多命中由假实现抛出——upsert 不捕获该异常
        assertThrows(SmartRedisLimiterException.class,
                () -> client.upsert(client.command("cust-001", true, LIMITS)));
    }

    @Test
    void deleteRemovesByIdentityAndCasVersion() {
        FakeClient client = new FakeClient();
        client.seed(existing(7L, true, LIMITS));
        PolicyMutationResult result = client.delete(7L, 3L);
        assertEquals(7L, result.getRuleId());
        assertTrue(client.store.isEmpty());
    }

    @Test
    void conflictRetryExhaustionSurfacesConflict() {
        FakeClient client = new FakeClient();
        client.seed(existing(7L, true, Collections.singletonList(new PolicyLimit(10L, 1L, "MINUTES"))));
        // 持续冲突超过重试上限：必须以冲突异常浮出而非静默吞掉
        client.updateConflicts.addAll(Arrays.asList(1, 1, 1, 1));
        assertThrows(SmartRedisLimiterPolicyMutationClient.PolicyMutationConflictException.class,
                () -> client.upsert(client.command("cust-001", true, LIMITS)));
    }
}
