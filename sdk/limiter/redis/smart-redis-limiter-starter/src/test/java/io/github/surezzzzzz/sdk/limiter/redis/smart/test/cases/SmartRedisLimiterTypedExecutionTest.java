package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterAlgorithm;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterAlgorithmFactory;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterDataDimension;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterRuleSelector;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.execution.SmartRedisLimiterExecutionPlan;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicyKey;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedDimensionFact;
import io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedExecutionEngine;
import io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedExecutionOutcome;
import io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedExecutionPlan;
import io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedExecutionStatus;
import io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedFactProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedFactStatus;
import io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedGateDeclaration;
import io.github.surezzzzzz.sdk.limiter.redis.smart.typed.SmartRedisLimiterTypedPlanBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 类型化多维门禁计划构建与执行测试
 *
 * @author surezzzzzz
 */
@Slf4j
class SmartRedisLimiterTypedExecutionTest {

    private static SmartRedisLimiterProperties.SmartLimitRule rule(long count, long window) {
        SmartRedisLimiterProperties.SmartLimitRule limit = new SmartRedisLimiterProperties.SmartLimitRule();
        limit.setCount(count);
        limit.setWindow(window);
        return limit;
    }

    private static SmartRedisLimiterTypedGateDeclaration declaration(SmartRedisLimiterDataDimension dimension,
                                                                     String namespace) {
        return new SmartRedisLimiterTypedGateDeclaration(dimension, namespace, null,
                new ArrayList<>(Collections.singletonList(rule(10, 60))));
    }

    private static Map<SmartRedisLimiterDataDimension, SmartRedisLimiterTypedFactProvider> providers(
            SmartRedisLimiterTypedFactProvider... providerList) {
        Map<SmartRedisLimiterDataDimension, SmartRedisLimiterTypedFactProvider> map =
                new EnumMap<>(SmartRedisLimiterDataDimension.class);
        for (SmartRedisLimiterTypedFactProvider provider : providerList) {
            map.put(provider.dimension(), provider);
        }
        return map;
    }

    private static SmartRedisLimiterTypedFactProvider fixedProvider(
            final SmartRedisLimiterDataDimension dimension, final SmartRedisLimiterTypedDimensionFact fact) {
        return new SmartRedisLimiterTypedFactProvider() {
            @Override
            public SmartRedisLimiterDataDimension dimension() {
                return dimension;
            }

            @Override
            public SmartRedisLimiterTypedDimensionFact provide(SmartRedisLimiterContext context) {
                return fact;
            }
        };
    }

    @Test
    @DisplayName("固定顺序执行：计划门禁按 IP、RESOURCE、CUSTOMER、USER 排序")
    void testPlanFixedOrder() {
        List<SmartRedisLimiterTypedGateDeclaration> declarations = Arrays.asList(
                declaration(SmartRedisLimiterDataDimension.USER, "iam"),
                declaration(SmartRedisLimiterDataDimension.RESOURCE, "host"),
                declaration(SmartRedisLimiterDataDimension.CUSTOMER, "customer-root"));
        SmartRedisLimiterTypedExecutionPlan plan = SmartRedisLimiterTypedPlanBuilder.build(
                "mock-service", "mock-resource", declarations,
                providers(
                        fixedProvider(SmartRedisLimiterDataDimension.USER,
                                SmartRedisLimiterTypedDimensionFact.present("user-1")),
                        fixedProvider(SmartRedisLimiterDataDimension.CUSTOMER,
                                SmartRedisLimiterTypedDimensionFact.present("cust-1"))),
                null, 1L, null, true);
        assertTrue(plan.isExecutable());
        assertEquals(3, plan.getGates().size());
        assertEquals(SmartRedisLimiterDataDimension.RESOURCE, plan.getGates().get(0).getDimension());
        assertEquals(SmartRedisLimiterDataDimension.CUSTOMER, plan.getGates().get(1).getDimension());
        assertEquals(SmartRedisLimiterDataDimension.USER, plan.getGates().get(2).getDimension());
    }

    @Test
    @DisplayName("事实四态：DENIED 拒绝计划、UNAVAILABLE 拒绝计划、NOT_APPLICABLE 跳过门禁")
    void testFactStatusSemantics() {
        List<SmartRedisLimiterTypedGateDeclaration> declarations = Collections.singletonList(
                declaration(SmartRedisLimiterDataDimension.USER, "iam"));
        SmartRedisLimiterTypedExecutionPlan denied = SmartRedisLimiterTypedPlanBuilder.build(
                "mock-service", "mock-resource", declarations,
                providers(fixedProvider(SmartRedisLimiterDataDimension.USER,
                        SmartRedisLimiterTypedDimensionFact.of(SmartRedisLimiterTypedFactStatus.DENIED))),
                null, 1L, null, true);
        assertFalse(denied.isExecutable());
        assertEquals(SmartRedisLimiterTypedFactStatus.DENIED, denied.getRejection().getStatus());

        SmartRedisLimiterTypedExecutionPlan unavailable = SmartRedisLimiterTypedPlanBuilder.build(
                "mock-service", "mock-resource", declarations,
                providers(fixedProvider(SmartRedisLimiterDataDimension.USER,
                        SmartRedisLimiterTypedDimensionFact.of(SmartRedisLimiterTypedFactStatus.UNAVAILABLE))),
                null, 1L, null, true);
        assertFalse(unavailable.isExecutable());

        SmartRedisLimiterTypedExecutionPlan notApplicable = SmartRedisLimiterTypedPlanBuilder.build(
                "mock-service", "mock-resource", declarations,
                providers(fixedProvider(SmartRedisLimiterDataDimension.USER,
                        SmartRedisLimiterTypedDimensionFact.of(SmartRedisLimiterTypedFactStatus.NOT_APPLICABLE))),
                null, 1L, null, true);
        assertTrue(notApplicable.isExecutable());
        assertTrue(notApplicable.getGates().isEmpty());
    }

    @Test
    @DisplayName("规则选择：EXACT 精确覆盖优先，其次 DEFAULT，最后本地声明")
    void testLimitResolutionOrder() {
        SmartRedisLimiterTypedPolicyKey exactKey = new SmartRedisLimiterTypedPolicyKey(
                "mock-service", "mock-resource", SmartRedisLimiterDataDimension.USER,
                SmartRedisLimiterRuleSelector.EXACT, "iam", null, "user-1");
        SmartRedisLimiterTypedPolicyKey defaultKey = new SmartRedisLimiterTypedPolicyKey(
                "mock-service", "mock-resource", SmartRedisLimiterDataDimension.USER,
                SmartRedisLimiterRuleSelector.DEFAULT, "iam", null, null);
        SmartRedisLimiterTypedPolicySnapshot snapshot = new SmartRedisLimiterTypedPolicySnapshot(
                SmartRedisLimiterConstant.TYPED_POLICY_SCHEMA_VERSION, "mock-service", 1L, 9L, Instant.now(),
                Arrays.asList(
                        new SmartRedisLimiterTypedPolicy(exactKey, true,
                                Collections.singletonList(new SmartRedisLimiterLimit(99L, 1L,
                                        io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit.MINUTES))),
                        new SmartRedisLimiterTypedPolicy(defaultKey, true,
                                Collections.singletonList(new SmartRedisLimiterLimit(50L, 1L,
                                        io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit.MINUTES)))));
        SmartRedisLimiterTypedGateDeclaration local = new SmartRedisLimiterTypedGateDeclaration(
                SmartRedisLimiterDataDimension.USER, "iam", null,
                new ArrayList<>(Collections.singletonList(rule(10, 60))));
        List<SmartRedisLimiterTypedGateDeclaration> declarations = Collections.singletonList(local);
        Map<SmartRedisLimiterDataDimension, SmartRedisLimiterTypedFactProvider> userProvider = providers(
                fixedProvider(SmartRedisLimiterDataDimension.USER,
                        SmartRedisLimiterTypedDimensionFact.present("user-1")));

        SmartRedisLimiterTypedExecutionPlan exactPlan = SmartRedisLimiterTypedPlanBuilder.build(
                "mock-service", "mock-resource", declarations, userProvider, snapshot, 1L, null, true);
        assertEquals(SmartRedisLimiterStarterConstant.TYPED_SOURCE_EXACT, exactPlan.getGates().get(0).getSource());
        assertEquals(99L, exactPlan.getGates().get(0).getLimits().get(0).getCount());

        SmartRedisLimiterTypedPolicySnapshot noExact = new SmartRedisLimiterTypedPolicySnapshot(
                SmartRedisLimiterConstant.TYPED_POLICY_SCHEMA_VERSION, "mock-service", 1L, 9L, Instant.now(),
                Collections.singletonList(new SmartRedisLimiterTypedPolicy(defaultKey, true,
                        Collections.singletonList(new SmartRedisLimiterLimit(50L, 1L,
                                io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit.MINUTES)))));
        SmartRedisLimiterTypedExecutionPlan defaultPlan = SmartRedisLimiterTypedPlanBuilder.build(
                "mock-service", "mock-resource", declarations, userProvider, noExact, 1L, null, true);
        assertEquals(SmartRedisLimiterStarterConstant.TYPED_SOURCE_DEFAULT, defaultPlan.getGates().get(0).getSource());

        SmartRedisLimiterTypedExecutionPlan epochMismatch = SmartRedisLimiterTypedPlanBuilder.build(
                "mock-service", "mock-resource", declarations, userProvider, snapshot, 2L, null, true);
        assertEquals(SmartRedisLimiterStarterConstant.TYPED_SOURCE_LOCAL, epochMismatch.getGates().get(0).getSource());
    }

    @Test
    @DisplayName("USER 与 SERVICE 同时适用时按冲突拒绝，单一适用正常执行")
    void testUserServiceExclusive() {
        List<SmartRedisLimiterTypedGateDeclaration> declarations = Arrays.asList(
                declaration(SmartRedisLimiterDataDimension.USER, "iam"),
                declaration(SmartRedisLimiterDataDimension.SERVICE, "iam"));
        Map<SmartRedisLimiterDataDimension, SmartRedisLimiterTypedFactProvider> both = providers(
                fixedProvider(SmartRedisLimiterDataDimension.USER,
                        SmartRedisLimiterTypedDimensionFact.present("user-1")),
                fixedProvider(SmartRedisLimiterDataDimension.SERVICE,
                        SmartRedisLimiterTypedDimensionFact.present("svc-1")));
        SmartRedisLimiterTypedExecutionPlan conflict = SmartRedisLimiterTypedPlanBuilder.build(
                "mock-service", "mock-resource", declarations, both, null, 1L, null, true);
        assertFalse(conflict.isExecutable());
        assertEquals("user-service-conflict", conflict.getRejection().getReason());

        Map<SmartRedisLimiterDataDimension, SmartRedisLimiterTypedFactProvider> serviceOnly = providers(
                fixedProvider(SmartRedisLimiterDataDimension.USER,
                        SmartRedisLimiterTypedDimensionFact.of(SmartRedisLimiterTypedFactStatus.NOT_APPLICABLE)),
                fixedProvider(SmartRedisLimiterDataDimension.SERVICE,
                        SmartRedisLimiterTypedDimensionFact.present("svc-1")));
        SmartRedisLimiterTypedExecutionPlan single = SmartRedisLimiterTypedPlanBuilder.build(
                "mock-service", "mock-resource", declarations, serviceOnly, null, 1L, null, true);
        assertTrue(single.isExecutable());
        assertEquals(1, single.getGates().size());
        assertEquals(SmartRedisLimiterDataDimension.SERVICE, single.getGates().get(0).getDimension());
    }

    @Test
    @DisplayName("执行语义：任一门禁拒绝即停，先得额度不退还；降级拒绝返回 503 语义")
    void testEngineSemantics() {
        SmartRedisLimiterProperties properties = new SmartRedisLimiterProperties();
        properties.setMe("mock-service");
        properties.getTyped().setAlgorithm(SmartRedisLimiterConstant.ALGORITHM_FIXED);

        ScriptedAlgorithm algorithm = new ScriptedAlgorithm();
        SmartRedisLimiterTypedExecutionEngine engine = new SmartRedisLimiterTypedExecutionEngine(
                properties, new ScriptedFactory(algorithm));

        List<SmartRedisLimiterTypedGateDeclaration> declarations = Arrays.asList(
                declaration(SmartRedisLimiterDataDimension.RESOURCE, "host"),
                declaration(SmartRedisLimiterDataDimension.USER, "iam"));
        Map<SmartRedisLimiterDataDimension, SmartRedisLimiterTypedFactProvider> provider = providers(
                fixedProvider(SmartRedisLimiterDataDimension.USER,
                        SmartRedisLimiterTypedDimensionFact.present("user-1")));

        algorithm.results.add(passed());
        algorithm.results.add(exceeded());
        SmartRedisLimiterTypedExecutionPlan plan = SmartRedisLimiterTypedPlanBuilder.build(
                "mock-service", "mock-resource", declarations, provider, null, 1L, null, true);
        SmartRedisLimiterTypedExecutionOutcome outcome = engine.execute(plan, null, "mock-resource", "IP");
        assertEquals(SmartRedisLimiterTypedExecutionStatus.LIMIT_EXCEEDED, outcome.getStatus());
        assertEquals(SmartRedisLimiterDataDimension.USER, outcome.getRejectedDimension());
        assertEquals(1, outcome.getAcquiredCount());

        algorithm.results.clear();
        algorithm.results.add(degraded(false));
        SmartRedisLimiterTypedExecutionOutcome degradedOutcome = engine.execute(plan, null, "mock-resource", "IP");
        assertEquals(SmartRedisLimiterTypedExecutionStatus.DEGRADE_DENIED, degradedOutcome.getStatus());
        assertEquals(0, degradedOutcome.getAcquiredCount());

        algorithm.results.clear();
        algorithm.results.add(passed());
        algorithm.results.add(passed());
        SmartRedisLimiterTypedExecutionOutcome passedOutcome = engine.execute(plan, null, "mock-resource", "IP");
        assertTrue(passedOutcome.isPassed());
        assertEquals(2, passedOutcome.getAcquiredCount());
    }

    private static SmartRedisLimiterResult passed() {
        return SmartRedisLimiterResult.builder().passed(true).fallback(false).build();
    }

    private static SmartRedisLimiterResult exceeded() {
        return SmartRedisLimiterResult.builder().passed(false).fallback(false)
                .limit(10).remaining(0).resetAt(System.currentTimeMillis() / 1000L + 60L).build();
    }

    private static SmartRedisLimiterResult degraded(boolean passed) {
        return SmartRedisLimiterResult.builder().passed(passed).fallback(true)
                .fallbackReason(SmartRedisLimiterConstant.FALLBACK_REASON_REDIS_ERROR).build();
    }

    private static final class ScriptedFactory implements SmartRedisLimiterAlgorithmFactory {
        private final SmartRedisLimiterAlgorithm algorithm;

        private ScriptedFactory(SmartRedisLimiterAlgorithm algorithm) {
            this.algorithm = algorithm;
        }

        @Override
        public SmartRedisLimiterAlgorithm getAlgorithm(String algorithmCode) {
            return algorithm;
        }
    }

    private static final class ScriptedAlgorithm implements SmartRedisLimiterAlgorithm {
        private final List<SmartRedisLimiterResult> results = new ArrayList<>();
        private SmartRedisLimiterProperties properties;

        @Override
        public String getAlgorithm() {
            return SmartRedisLimiterConstant.ALGORITHM_FIXED;
        }

        @Override
        public boolean tryAcquire(SmartRedisLimiterContext context,
                                  List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                                  String keyStrategy) {
            return true;
        }

        @Override
        public boolean tryAcquire(SmartRedisLimiterContext context,
                                  List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                                  String keyStrategy,
                                  String fallbackStrategy) {
            return true;
        }

        @Override
        public SmartRedisLimiterResult tryAcquireWithResult(SmartRedisLimiterContext context,
                                                            List<SmartRedisLimiterProperties.SmartLimitRule> limitRules,
                                                            String keyStrategy,
                                                            String fallbackStrategy) {
            return passed();
        }

        @Override
        public org.springframework.data.redis.core.script.DefaultRedisScript<List> getScript() {
            return null;
        }

        @Override
        public SmartRedisLimiterProperties getProperties() {
            return properties;
        }

        @Override
        public org.springframework.context.ApplicationContext getApplicationContext() {
            return null;
        }

        @Override
        public SmartRedisLimiterResult tryAcquireWithResult(SmartRedisLimiterContext context,
                                                            SmartRedisLimiterExecutionPlan plan,
                                                            String keyStrategy) {
            return results.isEmpty() ? passed() : results.remove(0);
        }
    }
}
