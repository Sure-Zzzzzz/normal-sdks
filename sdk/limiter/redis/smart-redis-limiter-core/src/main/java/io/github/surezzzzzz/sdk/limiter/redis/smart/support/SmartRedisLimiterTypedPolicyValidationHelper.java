package io.github.surezzzzzz.sdk.limiter.redis.smart.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.*;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;

import java.util.*;
import java.util.regex.Pattern;

/**
 * SmartRedisLimiter 类型化策略字段校验 Helper
 * <p>类型化对象的取值原样保留：不 trim、不转大小写、不做 Unicode 归一化，
 * 仅校验空值、空白、码点上限与控制字符；命名空间与自定义类型按稳定编码格式校验。</p>
 *
 * @author surezzzzzz
 */
public final class SmartRedisLimiterTypedPolicyValidationHelper {

    private static final Pattern STABLE_CODE_PATTERN =
            Pattern.compile(SmartRedisLimiterConstant.STABLE_CODE_PATTERN);

    private SmartRedisLimiterTypedPolicyValidationHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 校验计数维度
     *
     * @param dimension 计数维度
     * @throws SmartRedisLimiterException 维度缺失时抛出
     */
    public static void validateDimension(SmartRedisLimiterDataDimension dimension) {
        if (dimension == null) {
            throw typedKeyInvalid(SmartRedisLimiterConstant.POLICY_FIELD_DIMENSION,
                    ErrorMessage.REASON_TYPED_DIMENSION_INVALID);
        }
    }

    /**
     * 校验选择器与维度的组合约束
     * <p>RESOURCE 只有资源共享语义，固定 DEFAULT，不提供按调用者的 EXACT 变体。</p>
     *
     * @param dimension 计数维度
     * @param selector  规则选择器
     * @throws SmartRedisLimiterException 选择器缺失或组合非法时抛出
     */
    public static void validateSelector(SmartRedisLimiterDataDimension dimension,
                                        SmartRedisLimiterRuleSelector selector) {
        if (selector == null) {
            throw typedKeyInvalid(SmartRedisLimiterConstant.POLICY_FIELD_SELECTOR,
                    ErrorMessage.REASON_TYPED_SELECTOR_INVALID);
        }
        if (dimension == SmartRedisLimiterDataDimension.RESOURCE
                && selector != SmartRedisLimiterRuleSelector.DEFAULT) {
            throw typedKeyInvalid(SmartRedisLimiterConstant.POLICY_FIELD_SELECTOR,
                    ErrorMessage.REASON_TYPED_RESOURCE_DEFAULT_ONLY);
        }
    }

    /**
     * 规范化命名空间
     *
     * @param namespace 命名空间
     * @return 规范化后的命名空间
     * @throws SmartRedisLimiterException 命名空间非法时抛出
     */
    public static String normalizeNamespace(String namespace) {
        return normalizeStableCode(namespace, SmartRedisLimiterConstant.POLICY_FIELD_NAMESPACE,
                SmartRedisLimiterConstant.MAX_NAMESPACE_LENGTH);
    }

    /**
     * 校验自定义类型与维度的组合约束并返回规范化值
     *
     * @param dimension  计数维度
     * @param customType 自定义类型
     * @return CUSTOM 维度返回规范化后的自定义类型；其他维度必须为 null 并返回 null
     * @throws SmartRedisLimiterException 组合非法或格式非法时抛出
     */
    public static String normalizeCustomType(SmartRedisLimiterDataDimension dimension, String customType) {
        if (dimension == SmartRedisLimiterDataDimension.CUSTOM) {
            if (customType == null) {
                throw typedKeyInvalid(SmartRedisLimiterConstant.POLICY_FIELD_CUSTOM_TYPE,
                        ErrorMessage.REASON_TYPED_CUSTOM_TYPE_REQUIRED);
            }
            return normalizeStableCode(customType, SmartRedisLimiterConstant.POLICY_FIELD_CUSTOM_TYPE,
                    SmartRedisLimiterConstant.MAX_CUSTOM_TYPE_LENGTH);
        }
        if (customType != null) {
            throw typedKeyInvalid(SmartRedisLimiterConstant.POLICY_FIELD_CUSTOM_TYPE,
                    ErrorMessage.REASON_TYPED_CUSTOM_TYPE_FORBIDDEN);
        }
        return null;
    }

    /**
     * 校验计数对象与选择器的组合约束
     * <p>EXACT 对象为非空、非全空白的合法 Unicode 文本，最多 256 码点；
     * 保留大小写、前后空白与 Unicode 原形，仅数字 IP 由调用方按数字地址规范化。</p>
     *
     * @param objectId 计数对象
     * @param selector 规则选择器
     * @return EXACT 返回原样保留的对象；DEFAULT 必须为 null 并返回 null
     * @throws SmartRedisLimiterException 组合非法或取值非法时抛出
     */
    public static String validateObjectId(String objectId, SmartRedisLimiterRuleSelector selector) {
        if (selector == SmartRedisLimiterRuleSelector.DEFAULT) {
            if (objectId != null) {
                throw typedKeyInvalid(SmartRedisLimiterConstant.POLICY_FIELD_OBJECT_ID,
                        ErrorMessage.REASON_TYPED_OBJECT_ID_FORBIDDEN);
            }
            return null;
        }
        if (objectId == null) {
            throw typedKeyInvalid(SmartRedisLimiterConstant.POLICY_FIELD_OBJECT_ID,
                    ErrorMessage.REASON_TYPED_OBJECT_ID_REQUIRED);
        }
        if (objectId.trim().isEmpty()) {
            throw typedKeyInvalid(SmartRedisLimiterConstant.POLICY_FIELD_OBJECT_ID,
                    ErrorMessage.REASON_TYPED_OBJECT_ID_BLANK);
        }
        if (objectId.codePointCount(0, objectId.length())
                > SmartRedisLimiterConstant.MAX_OBJECT_ID_CODE_POINT_LENGTH) {
            throw typedKeyInvalid(SmartRedisLimiterConstant.POLICY_FIELD_OBJECT_ID,
                    ErrorMessage.REASON_TYPED_OBJECT_ID_CODE_POINT_EXCEEDED);
        }
        for (int i = 0; i < objectId.length(); i++) {
            if (Character.isISOControl(objectId.charAt(i))) {
                throw typedKeyInvalid(SmartRedisLimiterConstant.POLICY_FIELD_OBJECT_ID,
                        ErrorMessage.REASON_TYPED_OBJECT_ID_CONTROL_CHARACTER);
            }
        }
        return objectId;
    }

    /**
     * 校验规则完整限额窗口并返回不可变副本
     *
     * @param limits 限额窗口列表
     * @return 不可变窗口副本
     * @throws SmartRedisLimiterException 窗口数量为空、超限、含空项或窗口重复时抛出
     */
    public static List<SmartRedisLimiterLimit> validateLimits(List<SmartRedisLimiterLimit> limits) {
        if (limits == null || limits.isEmpty()) {
            throw typedPolicyInvalid(ErrorMessage.REASON_TYPED_LIMITS_EMPTY);
        }
        if (limits.size() > SmartRedisLimiterConstant.MAX_LIMITS_PER_POLICY) {
            throw typedPolicyInvalid(ErrorMessage.REASON_TYPED_LIMITS_TOO_MANY);
        }
        List<SmartRedisLimiterLimit> copied = new ArrayList<>(limits.size());
        Set<Long> windowSeconds = new HashSet<>();
        for (SmartRedisLimiterLimit limit : limits) {
            if (limit == null) {
                throw typedPolicyInvalid(ErrorMessage.REASON_TYPED_LIMITS_EMPTY);
            }
            if (!windowSeconds.add(limit.getWindowSeconds())) {
                throw typedPolicyInvalid(ErrorMessage.REASON_TYPED_WINDOW_DUPLICATED);
            }
            copied.add(limit);
        }
        return Collections.unmodifiableList(copied);
    }

    private static String normalizeStableCode(String value, String field, int maxLength) {
        if (value == null) {
            throw typedKeyInvalid(field, ErrorMessage.REASON_FIELD_REQUIRED);
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw typedKeyInvalid(field, ErrorMessage.REASON_FIELD_REQUIRED);
        }
        if (normalized.length() > maxLength) {
            throw typedKeyInvalid(field,
                    String.format(ErrorMessage.REASON_FIELD_MAX_LENGTH_EXCEEDED, maxLength));
        }
        if (!STABLE_CODE_PATTERN.matcher(normalized).matches()) {
            throw typedKeyInvalid(field, ErrorMessage.REASON_FIELD_STABLE_CODE_INVALID);
        }
        return normalized;
    }

    private static SmartRedisLimiterException typedKeyInvalid(String field, String reason) {
        return new SmartRedisLimiterException(
                ErrorCode.TYPED_POLICY_KEY_INVALID,
                String.format(ErrorMessage.TYPED_POLICY_KEY_INVALID, field, reason));
    }

    private static SmartRedisLimiterException typedPolicyInvalid(String reason) {
        return new SmartRedisLimiterException(
                ErrorCode.TYPED_POLICY_INVALID,
                String.format(ErrorMessage.TYPED_POLICY_INVALID, reason));
    }
}
