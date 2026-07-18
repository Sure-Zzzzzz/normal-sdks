package io.github.surezzzzzz.sdk.limiter.redis.smart.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;

import java.util.regex.Pattern;

/**
 * SmartRedisLimiter 动态策略字段校验 Helper
 *
 * @author surezzzzzz
 */
public final class SmartRedisLimiterPolicyValidationHelper {

    private static final Pattern STABLE_CODE_PATTERN =
            Pattern.compile(SmartRedisLimiterConstant.STABLE_CODE_PATTERN);

    private SmartRedisLimiterPolicyValidationHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 规范化服务编码
     *
     * @param serviceCode 服务编码
     * @return 规范化后的服务编码
     */
    public static String normalizeServiceCode(String serviceCode) {
        return normalizeStableCode(serviceCode, SmartRedisLimiterConstant.POLICY_FIELD_SERVICE_CODE,
                SmartRedisLimiterConstant.MAX_SERVICE_CODE_LENGTH);
    }

    /**
     * 规范化资源编码
     *
     * @param resourceCode 资源编码
     * @return 规范化后的资源编码
     */
    public static String normalizeResourceCode(String resourceCode) {
        return normalizeStableCode(resourceCode, SmartRedisLimiterConstant.POLICY_FIELD_RESOURCE_CODE,
                SmartRedisLimiterConstant.MAX_RESOURCE_CODE_LENGTH);
    }

    /**
     * 规范化限流对象标识
     *
     * @param subject 限流对象标识
     * @return 规范化后的限流对象标识
     */
    public static String normalizeSubject(String subject) {
        return normalizeText(subject, SmartRedisLimiterConstant.POLICY_FIELD_SUBJECT,
                SmartRedisLimiterConstant.MAX_SUBJECT_LENGTH);
    }

    /**
     * 规范化操作人标识
     *
     * @param operator 操作人标识
     * @return 规范化后的操作人标识
     */
    public static String normalizeOperator(String operator) {
        try {
            return normalizeText(operator, SmartRedisLimiterConstant.POLICY_FIELD_OPERATOR,
                    SmartRedisLimiterConstant.MAX_OPERATOR_LENGTH);
        } catch (SmartRedisLimiterException ex) {
            throw new SmartRedisLimiterException(
                    ErrorCode.MANAGEMENT_PAYLOAD_INVALID,
                    String.format(ErrorMessage.MANAGEMENT_PAYLOAD_INVALID, ErrorMessage.REASON_OPERATOR_INVALID),
                    ex);
        }
    }

    /**
     * 校验执行策略上下文
     *
     * @param policySource  策略来源
     * @param resourceCode  资源编码
     * @param policyRevision 远程策略快照版本
     * @throws SmartRedisLimiterException 策略上下文非法时抛出
     */
    public static void validatePolicyContext(String policySource,
                                             String resourceCode,
                                             Long policyRevision) {
        if (SmartRedisLimiterConstant.POLICY_SOURCE_LOCAL.equals(policySource)) {
            if (policyRevision != null) {
                throw invalidPolicyContext(ErrorMessage.REASON_LOCAL_POLICY_REVISION_FORBIDDEN);
            }
            return;
        }
        if (SmartRedisLimiterConstant.POLICY_SOURCE_REMOTE.equals(policySource)) {
            if (resourceCode == null) {
                throw invalidPolicyContext(ErrorMessage.REASON_REMOTE_RESOURCE_CODE_REQUIRED);
            }
            if (policyRevision == null || policyRevision < 0) {
                throw invalidPolicyContext(ErrorMessage.REASON_REMOTE_POLICY_REVISION_INVALID);
            }
            return;
        }
        throw invalidPolicyContext(ErrorMessage.REASON_POLICY_SOURCE_UNSUPPORTED);
    }

    private static String normalizeStableCode(String value, String field, int maxLength) {
        String normalized = normalizeRequired(value, field, maxLength);
        if (!STABLE_CODE_PATTERN.matcher(normalized).matches()) {
            throw invalidField(field, ErrorMessage.REASON_FIELD_STABLE_CODE_INVALID);
        }
        return normalized;
    }

    private static String normalizeText(String value, String field, int maxLength) {
        String normalized = normalizeRequired(value, field, maxLength);
        for (int i = 0; i < normalized.length(); i++) {
            if (Character.isISOControl(normalized.charAt(i))) {
                throw invalidField(field, ErrorMessage.REASON_FIELD_CONTROL_CHARACTER);
            }
        }
        return normalized;
    }

    private static String normalizeRequired(String value, String field, int maxLength) {
        if (value == null) {
            throw invalidField(field, ErrorMessage.REASON_FIELD_REQUIRED);
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw invalidField(field, ErrorMessage.REASON_FIELD_REQUIRED);
        }
        if (normalized.length() > maxLength) {
            throw invalidField(field, String.format(ErrorMessage.REASON_FIELD_MAX_LENGTH_EXCEEDED, maxLength));
        }
        return normalized;
    }

    private static SmartRedisLimiterException invalidField(String field, String reason) {
        return new SmartRedisLimiterException(
                ErrorCode.POLICY_KEY_INVALID,
                String.format(ErrorMessage.POLICY_KEY_INVALID, field, reason));
    }

    private static SmartRedisLimiterException invalidPolicyContext(String reason) {
        return new SmartRedisLimiterException(
                ErrorCode.EXECUTION_POLICY_CONTEXT_INVALID,
                String.format(ErrorMessage.EXECUTION_POLICY_CONTEXT_INVALID, reason));
    }
}
