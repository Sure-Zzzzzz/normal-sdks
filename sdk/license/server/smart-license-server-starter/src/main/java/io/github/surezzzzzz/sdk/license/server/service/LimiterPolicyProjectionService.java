package io.github.surezzzzzz.sdk.license.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.spi.LimiterPolicyPort;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.constant.LimiterPolicyBindingState;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.LimiterPolicyBindingEntity;
import io.github.surezzzzzz.sdk.license.server.repository.LimiterPolicyBindingRepository;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.support.ProjectionRequestHelper;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 将 License 授权中的客户限频快照投影到 limiter Management。
 *
 * <p>该服务只依赖 License 自有 SPI；远端调用不写入 License 事务，结果未知时保留
 * UNCERTAIN 台账，下一次通过完整策略键回读，不把超时当成失败后重复创建。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleLicenseServerComponent
public class LimiterPolicyProjectionService {

    private static final int MAX_POLICIES = 64;
    private static final int MAX_LIMITS = 8;
    private final LimiterPolicyBindingRepository bindings;
    private final PlatformTransactionManager transactionManager;

    /** limiter 传输件由宿主提供；未配置时，声明了限频授权的 Grant 失败关闭。 */
    @Autowired(required = false)
    private LimiterPolicyPort port;

    @Autowired(required = false)
    private GrantRepository grants;

    /**
     * 生产装配使用独立事务管理器；单元测试保留一参构造，直接使用 mock 仓储。
     * JPA 宿主必然提供事务管理器，缺失时由启动装配明确暴露问题。
     */
    @Autowired
    public LimiterPolicyProjectionService(LimiterPolicyBindingRepository bindings,
                                           PlatformTransactionManager transactionManager) {
        this.bindings = bindings;
        this.transactionManager = transactionManager;
    }

    public LimiterPolicyProjectionService(LimiterPolicyBindingRepository bindings) {
        this(bindings, null);
    }

    /** 启用授权前写入全部客户策略；任一策略未确认则授权保持原状态。 */
    public void enable(GrantRevisionEntity grant) {
        List<PolicyInput> policies = parse(grant.getLimiterPoliciesJson());
        if (policies.isEmpty()) {
            return;
        }
        requirePort();
        // 状态入口在事务内调用；锁住同客户全部授权，避免两个授权并发抢占同一 limiter 目标。
        if (grants != null) {
            grants.findAllWithLockByCustomerId(grant.getCustomerId());
        }
        for (PolicyInput policy : policies) {
            LimiterPolicyPort.PolicyRequest request = policy.request(grant, true);
            log.debug("开始投影客户限频：grantId={}，operationId={}，serviceCode={}，resourceCode={}",
                    grant.getGrantId(), request.getOperationId(), policy.serviceCode, policy.resourceCode);
            List<LimiterPolicyBindingEntity> targetBindings = ledger(
                    () -> bindings.findByCustomerIdAndServiceCodeAndResourceCode(grant.getCustomerId(),
                            policy.serviceCode, policy.resourceCode));
            if (targetBindings == null) {
                targetBindings = Collections.emptyList();
            }
            List<LimiterPolicyBindingEntity> conflicts = new ArrayList<LimiterPolicyBindingEntity>();
            for (LimiterPolicyBindingEntity candidate : targetBindings) {
                if (!grant.getGrantId().equals(candidate.getGrantId())
                        && candidate.getState() != LimiterPolicyBindingState.DISABLED
                        && candidate.getState() != LimiterPolicyBindingState.SUPERSEDED) {
                    conflicts.add(candidate);
                }
            }
            if (!conflicts.isEmpty()) {
                throw LicenseHttpException.conflict("同一客户的限频资源已由其他授权占用");
            }
            LimiterPolicyBindingEntity binding = prepare(grant, policy, request, true);
            try {
                LimiterPolicyPort.PolicyResult result = requireResult(invokeRemote(
                        () -> port.upsert(request)));
                validateResult(request.getKey(), result, true);
                persistSynced(binding, result, true);
                log.debug("客户限频投影已确认：grantId={}，operationId={}，ruleId={}，rowVersion={}",
                        grant.getGrantId(), request.getOperationId(), result.getRuleId(), result.getRowVersion());
                supersedeDisabled(grant, policy);
            } catch (LimiterPolicyPort.Failure failure) {
                log.warn("客户限频投影未确认：grantId={}，operationId={}，故障类型={}",
                        grant.getGrantId(), request.getOperationId(), failure.getKind());
                persistFailure(binding, failure.getKind());
                throw LicenseHttpException.dependencyUnavailable("客户限频策略履约未确认");
            }
        }
    }

    /** 暂停授权前禁用已确认策略；规则不存在视为已禁用并留下台账。 */
    public void disable(GrantRevisionEntity grant) {
        // 吊销、暂停和到期收敛都必须与同客户的启用动作串行，避免远端 CAS
        // 成功后另一条授权状态变更把同一客户的策略重新打开。
        if (grants != null) {
            grants.findAllWithLockByCustomerId(grant.getCustomerId());
        }
        List<LimiterPolicyBindingEntity> existing = ledger(
                () -> bindings.findByGrantId(grant.getGrantId()));
        if (existing == null) {
            existing = Collections.emptyList();
        }
        if (existing.isEmpty()) {
            return;
        }
        List<LimiterPolicyBindingEntity> actionable = new ArrayList<LimiterPolicyBindingEntity>();
        for (LimiterPolicyBindingEntity binding : existing) {
            if (binding.getState() != LimiterPolicyBindingState.DISABLED
                    && binding.getState() != LimiterPolicyBindingState.SUPERSEDED) {
                actionable.add(binding);
            }
        }
        if (actionable.isEmpty()) {
            return;
        }
        requirePort();
        for (LimiterPolicyBindingEntity binding : actionable) {
            LimiterPolicyPort.PolicyKey key = new LimiterPolicyPort.PolicyKey(binding.getServiceCode(),
                    binding.getResourceCode(), binding.getNamespace(), binding.getCustomerId());
            try {
                log.debug("开始停用客户限频：grantId={}，operationId={}，ruleId={}，rowVersion={}",
                        grant.getGrantId(), binding.getOperationId(), binding.getRuleId(), binding.getRuleRowVersion());
                LimiterPolicyPort.PolicyResult result;
                boolean needsFreshRead = binding.getRuleId() == null || binding.getRuleRowVersion() == null
                        || binding.getState() != LimiterPolicyBindingState.SYNCED;
                if (needsFreshRead) {
                    LimiterPolicyPort.PolicyResult found = invokeRemote(() -> port.find(key));
                    if (found == null) {
                        result = null;
                    } else {
                        validateResult(key, found, null);
                        if (!found.isEnabled()) {
                            result = found;
                        } else {
                            result = invokeRemote(() -> port.setEnabled(found.getKey(), found.getRuleId(),
                                    found.getRowVersion(), false, binding.getOperationId() + ":disable"));
                            validateResult(key, result, false, found.getRuleId(), found.getRowVersion());
                        }
                    }
                } else {
                    result = invokeRemote(() -> port.setEnabled(key, binding.getRuleId(),
                            binding.getRuleRowVersion(), false, binding.getOperationId() + ":disable"));
                    validateResult(key, result, false, binding.getRuleId(), binding.getRuleRowVersion());
                }
                if (result == null) {
                    persistDisabled(binding);
                } else {
                    persistSynced(binding, result, false);
                }
                log.debug("客户限频停用已确认：grantId={}，operationId={}，state={}",
                        grant.getGrantId(), binding.getOperationId(), result == null
                                ? LimiterPolicyBindingState.DISABLED : LimiterPolicyBindingState.DISABLED);
            } catch (LimiterPolicyPort.Failure failure) {
                log.warn("客户限频停用未确认：grantId={}，operationId={}，故障类型={}",
                        grant.getGrantId(), binding.getOperationId(), failure.getKind());
                persistFailure(binding, failure.getKind());
                throw LicenseHttpException.dependencyUnavailable("客户限频策略停用未确认");
            }
        }
    }

    private LimiterPolicyBindingEntity prepare(GrantRevisionEntity grant, PolicyInput policy,
                                               LimiterPolicyPort.PolicyRequest request, boolean enabled) {
        return ledger(() -> {
            Optional<LimiterPolicyBindingEntity> existing = bindings
                    .findByGrantIdAndServiceCodeAndResourceCodeAndCustomerId(grant.getGrantId(),
                            policy.serviceCode, policy.resourceCode, grant.getCustomerId());
            if (existing.isPresent()) {
                LimiterPolicyBindingEntity value = existing.get();
                value.pending(policy.source.toString(), grant.getGrantRevision(), request.getOperationId(), enabled);
                return bindings.save(value);
            }
            String bindingId = "lpb-" + ProjectionRequestHelper.inputDigest(policy.source).substring(0, 32)
                    + "-" + grant.getGrantId().replaceAll("[^A-Za-z0-9]", "");
            return bindings.save(new LimiterPolicyBindingEntity(bindingId, grant.getGrantId(),
                    grant.getGrantRevision(), request, policy.source.toString()));
        });
    }

    /**
     * 远端调用前后的本地台账必须独立提交，不能被宿主授权事务的 503 回滚。
     * 单元测试没有事务管理器时直接执行仓储操作，生产 JPA 装配则使用 REQUIRES_NEW。
     */
    private <T> T ledger(Supplier<T> action) {
        if (transactionManager == null) {
            return action.get();
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> action.get());
    }

    /** 按主键重新装载，避免把挂起事务中的旧实体直接合并到独立事务。 */
    private void persistSynced(LimiterPolicyBindingEntity detached,
                               LimiterPolicyPort.PolicyResult result, boolean enabled) {
        ledger(() -> {
            LimiterPolicyBindingEntity current = bindings.findById(detached.getBindingId()).orElse(detached);
            current.synced(result, enabled);
            bindings.save(current);
            return null;
        });
    }

    private void persistDisabled(LimiterPolicyBindingEntity detached) {
        ledger(() -> {
            LimiterPolicyBindingEntity current = bindings.findById(detached.getBindingId()).orElse(detached);
            current.failed(LimiterPolicyBindingState.DISABLED, null);
            bindings.save(current);
            return null;
        });
    }

    private void persistFailure(LimiterPolicyBindingEntity detached, LimiterPolicyPort.FailureKind kind) {
        ledger(() -> {
            LimiterPolicyBindingEntity current = bindings.findById(detached.getBindingId()).orElse(detached);
            current.failed(kind == LimiterPolicyPort.FailureKind.UNCERTAIN
                    ? LimiterPolicyBindingState.UNCERTAIN : LimiterPolicyBindingState.FAILED,
                    kind.name());
            bindings.save(current);
            return null;
        });
    }

    /** 成功接管旧的 DISABLED 目标后，在同一独立事务内保留旧绑定历史。 */
    private void supersedeDisabled(GrantRevisionEntity grant, PolicyInput policy) {
        ledger(() -> {
            List<LimiterPolicyBindingEntity> candidates = bindings
                    .findByCustomerIdAndServiceCodeAndResourceCode(grant.getCustomerId(),
                            policy.serviceCode, policy.resourceCode);
            if (candidates != null) {
                for (LimiterPolicyBindingEntity candidate : candidates) {
                    if (!grant.getGrantId().equals(candidate.getGrantId())
                            && candidate.getState() == LimiterPolicyBindingState.DISABLED) {
                        candidate.superseded();
                        bindings.save(candidate);
                    }
                }
            }
            return null;
        });
    }

    private void requirePort() {
        if (port == null) {
            throw LicenseHttpException.dependencyUnavailable("客户限频履约端口未装配");
        }
    }

    /**
     * 适配器必须按 SPI 抛出 Failure；若旧适配器直接抛出普通运行时异常，结果同样未知，
     * 不能让本地台账停在 PENDING_SYNC 并被后续流程误判为仍可执行。
     */
    private static <T> T invokeRemote(Supplier<T> action) {
        try {
            return action.get();
        } catch (LimiterPolicyPort.Failure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new LimiterPolicyPort.Failure(LimiterPolicyPort.FailureKind.UNCERTAIN, failure);
        }
    }

    /** 适配器必须返回完整、可用于下一次 CAS 的管理端结果。 */
    private static LimiterPolicyPort.PolicyResult requireResult(LimiterPolicyPort.PolicyResult result) {
        if (result == null) {
            throw new LimiterPolicyPort.Failure(LimiterPolicyPort.FailureKind.INVALID_RESPONSE, null);
        }
        return result;
    }

    /** 校验远端没有把一次写入错误地映射到其他客户或资源。namespace 由远端补全。 */
    private static void validateResult(LimiterPolicyPort.PolicyKey expected,
                                       LimiterPolicyPort.PolicyResult actual, Boolean expectedEnabled) {
        validateResult(expected, actual, expectedEnabled, null, null);
    }

    /**
     * 停用是 CAS 写入，除了业务键和启用状态，还必须确认没有切换到另一条远端规则，
     * 且返回版本没有倒退；否则不能把错误对象写回 License 台账。
     */
    private static void validateResult(LimiterPolicyPort.PolicyKey expected,
                                       LimiterPolicyPort.PolicyResult actual, Boolean expectedEnabled,
                                       String expectedRuleId, Long expectedRowVersion) {
        if (actual == null) {
            throw new LimiterPolicyPort.Failure(LimiterPolicyPort.FailureKind.INVALID_RESPONSE, null);
        }
        LimiterPolicyPort.PolicyKey received = actual.getKey();
        if (received == null || received.getNamespace() == null || received.getNamespace().trim().isEmpty()
                || !expected.getServiceCode().equals(received.getServiceCode())
                || !expected.getResourceCode().equals(received.getResourceCode())
                || !expected.getCustomerId().equals(received.getCustomerId())
                || expected.getNamespace() != null && !expected.getNamespace().equals(received.getNamespace())
                || expectedEnabled != null && expectedEnabled.booleanValue() != actual.isEnabled()
                || expectedRuleId != null && !expectedRuleId.equals(actual.getRuleId())
                || expectedRowVersion != null && actual.getRowVersion() < expectedRowVersion) {
            throw new LimiterPolicyPort.Failure(LimiterPolicyPort.FailureKind.INVALID_RESPONSE, null);
        }
    }

    private List<PolicyInput> parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        JsonNode node;
        try {
            node = StrictJson.mapper().readTree(json);
        } catch (Exception failure) {
            throw LicenseHttpException.dependencyUnavailable("授权限频快照不可解析");
        }
        if (node == null || !node.isArray() || node.size() > MAX_POLICIES) {
            throw LicenseHttpException.dependencyUnavailable("授权限频快照无效");
        }
        List<PolicyInput> result = new ArrayList<PolicyInput>();
        Set<String> targets = new HashSet<String>();
        for (JsonNode item : node) {
            PolicyInput policy = PolicyInput.parse(item);
            if (!targets.add(policy.serviceCode + "\u0000" + policy.resourceCode)) {
                throw LicenseHttpException.unprocessable("限频授权不得重复声明同一资源");
            }
            result.add(policy);
        }
        return result;
    }

    private static final class PolicyInput {
        private final String serviceCode;
        private final String resourceCode;
        private final List<LimiterPolicyPort.Limit> limits;
        private final ObjectNode source;

        private PolicyInput(String serviceCode, String resourceCode, List<LimiterPolicyPort.Limit> limits,
                            ObjectNode source) {
            this.serviceCode = serviceCode;
            this.resourceCode = resourceCode;
            this.limits = limits;
            this.source = source;
        }

        private static PolicyInput parse(JsonNode node) {
            if (!node.isObject()) {
                throw LicenseHttpException.unprocessable("限频授权项必须为对象");
            }
            ObjectNode source = (ObjectNode) node.deepCopy();
            requireKeys(source, "serviceCode", "resourceCode", "limits");
            String service = text(source, "serviceCode", 128);
            String resource = text(source, "resourceCode", 128);
            JsonNode limitsNode = source.get("limits");
            if (!limitsNode.isArray() || limitsNode.size() == 0 || limitsNode.size() > MAX_LIMITS) {
                throw LicenseHttpException.unprocessable("限频窗口数量无效");
            }
            List<LimiterPolicyPort.Limit> limits = new ArrayList<LimiterPolicyPort.Limit>();
            for (JsonNode limit : limitsNode) {
                if (!limit.isObject()) {
                    throw LicenseHttpException.unprocessable("限频窗口必须为对象");
                }
                ObjectNode item = (ObjectNode) limit;
                requireKeys(item, "count", "window", "unit");
                if (!item.get("count").isIntegralNumber() || !item.get("window").isIntegralNumber()) {
                    throw LicenseHttpException.unprocessable("限频次数和窗口必须为整数");
                }
                limits.add(new LimiterPolicyPort.Limit(item.get("count").longValue(),
                        item.get("window").longValue(), text(item, "unit", 16)));
            }
            return new PolicyInput(service, resource, limits, source);
        }

        private LimiterPolicyPort.PolicyRequest request(GrantRevisionEntity grant, boolean enabled) {
            LimiterPolicyPort.PolicyKey key = new LimiterPolicyPort.PolicyKey(serviceCode, resourceCode,
                    null, grant.getCustomerId());
            String operationId = "lpo-" + grant.getGrantId().replaceAll("[^A-Za-z0-9]", "")
                    + "-" + ProjectionRequestHelper.inputDigest(source).substring(0, 24)
                    + "-" + grant.getGrantRevision();
            return new LimiterPolicyPort.PolicyRequest(key, limits, enabled, operationId);
        }

        private static String text(ObjectNode object, String name, int maxLength) {
            JsonNode value = object.get(name);
            if (value == null || !value.isTextual() || value.textValue().trim().isEmpty()
                    || value.textValue().length() > maxLength) {
                throw LicenseHttpException.unprocessable("限频授权字段无效：" + name);
            }
            return value.textValue();
        }

        private static void requireKeys(ObjectNode object, String... required) {
            java.util.Set<String> allowed = new java.util.HashSet<String>();
            Collections.addAll(allowed, required);
            Iterator<String> names = object.fieldNames();
            while (names.hasNext()) {
                if (!allowed.contains(names.next())) {
                    throw LicenseHttpException.unprocessable("限频授权含未知字段");
                }
            }
            for (String name : required) {
                if (!object.has(name)) {
                    throw LicenseHttpException.unprocessable("限频授权缺少字段：" + name);
                }
            }
        }
    }
}
