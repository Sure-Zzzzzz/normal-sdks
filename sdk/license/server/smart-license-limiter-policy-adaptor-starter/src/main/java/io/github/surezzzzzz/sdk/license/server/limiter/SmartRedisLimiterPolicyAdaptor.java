package io.github.surezzzzzz.sdk.license.server.limiter;

import io.github.surezzzzzz.sdk.license.core.spi.LimiterPolicyPort;
import io.github.surezzzzzz.sdk.license.server.limiter.annotation.SimpleLicenseLimiterPolicyAdaptorComponent;
import io.github.surezzzzzz.sdk.license.server.limiter.configuration.SimpleLicenseLimiterPolicyAdaptorProperties;
import io.github.surezzzzzz.sdk.license.server.limiter.constant.SimpleLicenseLimiterPolicyAdaptorConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.SmartRedisLimiterPolicyMutationClient;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyMutationResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyRuleView;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyUpsertCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 将 License 限频履约端口映射为 limiter Management 类型化策略变更客户端。
 *
 * <p>本类只做领域字段转换和故障分类，不直接拼 HTTP、不持有 AKSK 凭据。</p>
 *
 * @author surezzzzzz
 */
@SimpleLicenseLimiterPolicyAdaptorComponent
public class SmartRedisLimiterPolicyAdaptor implements LimiterPolicyPort {

    private final SmartRedisLimiterPolicyMutationClient client;
    private final SimpleLicenseLimiterPolicyAdaptorProperties properties;

    public SmartRedisLimiterPolicyAdaptor(SmartRedisLimiterPolicyMutationClient client,
                                          SimpleLicenseLimiterPolicyAdaptorProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public PolicyResult upsert(PolicyRequest request) {
        requireRequest(request);
        PolicyUpsertCommand command = command(request.getKey(), request.getLimits(), request.isEnabled());
        PolicyMutationResult result = client.upsert(command);
        return confirmedResult(commandKey(command), command.getLimits(), command.isEnabled(), null, result);
    }

    @Override
    public PolicyResult setEnabled(PolicyKey key, String ruleId, long expectedRowVersion,
                                  boolean enabled, String operationId) {
        String resolvedRuleId = requireRuleId(ruleId);
        if (expectedRowVersion < 0L) {
            throw failure(FailureKind.REJECTED, new IllegalArgumentException(
                    SimpleLicenseLimiterPolicyAdaptorConstant.ROW_VERSION_REQUIRED));
        }
        PolicyUpsertCommand command = command(key, Collections.<LimiterPolicyPort.Limit>emptyList(), enabled);
        long numericRuleId = parseRuleId(resolvedRuleId);
        // CAS 只校验行版本；先核对规则身份，避免陈旧 ruleId 命中其他客户的规则。
        PolicyRuleView before = client.findByIdentityRow(numericRuleId);
        if (before == null || before.getRuleId() != numericRuleId
                || before.getRowVersion() != expectedRowVersion) {
            throw failure(FailureKind.CONFLICT, new IllegalStateException(
                    SimpleLicenseLimiterPolicyAdaptorConstant.STALE_RULE));
        }
        requireSameIdentity(commandKey(command), before);
        if (before.getLimits() == null) {
            throw failure(FailureKind.INVALID_RESPONSE, new IllegalStateException(
                    SimpleLicenseLimiterPolicyAdaptorConstant.LIMITS_REQUIRED));
        }
        List<PolicyLimit> limits = new ArrayList<PolicyLimit>(before.getLimits());
        PolicyMutationResult result = client.updateEnabled(numericRuleId, expectedRowVersion, enabled);
        return confirmedResult(commandKey(command), limits, enabled, numericRuleId, result);
    }

    @Override
    public PolicyResult find(PolicyKey key) {
        PolicyUpsertCommand command = command(key, Collections.<LimiterPolicyPort.Limit>emptyList(), true);
        PolicyRuleView view = client.findByKey(command);
        if (view == null) {
            return null;
        }
        requireSameIdentity(commandKey(command), view);
        return toResult(view);
    }

    private PolicyUpsertCommand command(PolicyKey key, List<LimiterPolicyPort.Limit> limits,
                                        boolean enabled) {
        if (key == null) {
            throw failure(FailureKind.REJECTED, new IllegalArgumentException(
                    SimpleLicenseLimiterPolicyAdaptorConstant.KEY_REQUIRED));
        }
        String namespace = resolveNamespace(key.getNamespace());
        List<PolicyLimit> mapped = new ArrayList<PolicyLimit>();
        for (LimiterPolicyPort.Limit limit : limits) {
            mapped.add(new PolicyLimit(limit.getCount(), limit.getWindow(), limit.getUnit()));
        }
        return PolicyUpsertCommand.builder()
                .serviceCode(key.getServiceCode())
                .resourceCode(key.getResourceCode())
                .dimension(SimpleLicenseLimiterPolicyAdaptorConstant.CUSTOMER_DIMENSION)
                .selector(SimpleLicenseLimiterPolicyAdaptorConstant.EXACT_SELECTOR)
                .namespace(namespace)
                .objectId(key.getCustomerId())
                .enabled(enabled)
                .limits(mapped)
                .build();
    }

    private PolicyKey commandKey(PolicyUpsertCommand command) {
        return new PolicyKey(command.getServiceCode(), command.getResourceCode(), command.getNamespace(),
                command.getObjectId());
    }

    /** 写入已返回后，任何回读或校验失败都不能被台账记成确定失败。 */
    private PolicyResult confirmedResult(PolicyKey key, List<PolicyLimit> limits, boolean enabled,
                                         Long expectedRuleId, PolicyMutationResult result) {
        if (result == null || result.getRuleId() < 0L || result.getRowVersion() < 0L
                || result.getRevision() < PolicyResult.NO_REVISION
                || result.isEnabled() != enabled
                || expectedRuleId != null && result.getRuleId() != expectedRuleId) {
            throw failure(FailureKind.UNCERTAIN, new IllegalStateException(
                    SimpleLicenseLimiterPolicyAdaptorConstant.MUTATION_STATE_INVALID));
        }
        PolicyRuleView view = client.findByIdentityRow(result.getRuleId());
        if (view == null || view.getRuleId() != result.getRuleId()) {
            throw failure(FailureKind.UNCERTAIN, new IllegalStateException(
                    SimpleLicenseLimiterPolicyAdaptorConstant.MUTATION_RULE_MISSING));
        }
        if (!sameIdentity(key, view) || view.getRowVersion() != result.getRowVersion()
                || view.isEnabled() != enabled || !sameLimits(limits, view.getLimits())) {
            throw failure(FailureKind.UNCERTAIN, new IllegalStateException(
                    SimpleLicenseLimiterPolicyAdaptorConstant.MUTATION_READBACK_MISMATCH));
        }
        return new PolicyResult(key, String.valueOf(result.getRuleId()), result.getRowVersion(),
                result.getRevision(), result.isEnabled());
    }

    private boolean sameLimits(List<PolicyLimit> expected, List<PolicyLimit> actual) {
        if (expected == null || actual == null || expected.size() != actual.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            PolicyLimit left = expected.get(index);
            PolicyLimit right = actual.get(index);
            if (left == null || right == null || left.getCount() != right.getCount()
                    || left.getWindow() != right.getWindow()
                    || !Objects.equals(left.getUnit(), right.getUnit())) {
                return false;
            }
        }
        return true;
    }

    private void requireSameIdentity(PolicyKey key, PolicyRuleView view) {
        if (!sameIdentity(key, view)) {
            throw failure(FailureKind.INVALID_RESPONSE, new IllegalStateException(
                    SimpleLicenseLimiterPolicyAdaptorConstant.RULE_IDENTITY_MISMATCH));
        }
    }

    private boolean sameIdentity(PolicyKey key, PolicyRuleView view) {
        return key.getServiceCode().equals(view.getServiceCode())
                && key.getResourceCode().equals(view.getResourceCode())
                && key.getCustomerId().equals(view.getObjectId())
                && key.getNamespace().equals(view.getNamespace())
                && SimpleLicenseLimiterPolicyAdaptorConstant.CUSTOMER_DIMENSION.equals(view.getDimension())
                && SimpleLicenseLimiterPolicyAdaptorConstant.EXACT_SELECTOR.equals(view.getSelector())
                && (view.getCustomType() == null || view.getCustomType().isEmpty());
    }

    private PolicyResult toResult(PolicyRuleView view) {
        if (view.getNamespace() == null || view.getNamespace().trim().isEmpty()
                || view.getServiceCode() == null || view.getResourceCode() == null
                || view.getObjectId() == null || view.getObjectId().trim().isEmpty()
                || view.getRuleId() < 0L || view.getRowVersion() < 0L) {
            throw failure(FailureKind.INVALID_RESPONSE, new IllegalStateException(
                    SimpleLicenseLimiterPolicyAdaptorConstant.READBACK_KEY_INCOMPLETE));
        }
        return new PolicyResult(new PolicyKey(view.getServiceCode(), view.getResourceCode(),
                view.getNamespace(), view.getObjectId()), String.valueOf(view.getRuleId()),
                view.getRowVersion(), PolicyResult.NO_REVISION, view.isEnabled());
    }

    private LimiterPolicyPort.Failure failure(FailureKind kind, Throwable cause) {
        return new LimiterPolicyPort.Failure(kind, cause);
    }

    private void requireRequest(PolicyRequest request) {
        if (request == null || request.getKey() == null || request.getLimits() == null
                || request.getLimits().isEmpty()) {
            throw failure(FailureKind.REJECTED, new IllegalArgumentException(
                    SimpleLicenseLimiterPolicyAdaptorConstant.REQUEST_INCOMPLETE));
        }
    }

    private String resolveNamespace(String namespace) {
        if (namespace != null && !namespace.trim().isEmpty()) {
            return namespace;
        }
        if (properties == null || properties.getCustomerNamespace() == null
                || properties.getCustomerNamespace().trim().isEmpty()) {
            throw failure(FailureKind.REJECTED, new IllegalArgumentException(
                    SimpleLicenseLimiterPolicyAdaptorConstant.CUSTOMER_NAMESPACE_REQUIRED));
        }
        return properties.getCustomerNamespace();
    }

    private String requireRuleId(String ruleId) {
        if (ruleId == null || ruleId.trim().isEmpty()) {
            throw failure(FailureKind.REJECTED, new IllegalArgumentException(
                    SimpleLicenseLimiterPolicyAdaptorConstant.RULE_ID_REQUIRED));
        }
        return ruleId;
    }

    private long parseRuleId(String ruleId) {
        try {
            return Long.parseLong(ruleId);
        } catch (NumberFormatException failure) {
            throw failure(FailureKind.INVALID_RESPONSE, failure);
        }
    }

}
