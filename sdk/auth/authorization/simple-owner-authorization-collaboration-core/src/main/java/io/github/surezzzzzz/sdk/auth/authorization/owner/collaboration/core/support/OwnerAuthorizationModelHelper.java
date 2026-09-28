package io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.support;

import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.constant.SimpleOwnerAuthorizationCollaborationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.exception.OwnerAuthorizationCollaborationException;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationChange;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * 所属人授权协作模型校验与不可变复制帮助类。
 *
 * @author surezzzzzz
 */
public final class OwnerAuthorizationModelHelper {

    private OwnerAuthorizationModelHelper() {
        throw new UnsupportedOperationException(
                SimpleOwnerAuthorizationCollaborationConstant.MESSAGE_HELPER_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 校验非空文本，不对不透明标识做截断或规范化。
     *
     * @param value     字段值
     * @param fieldName 字段名
     * @return 原始文本
     */
    public static String requireText(String value, String fieldName) {
        if (value == null) {
            throw invalidModel(String.format(SimpleOwnerAuthorizationCollaborationConstant.DETAIL_FIELD_CANNOT_BE_NULL,
                    fieldName));
        }
        if (value.isEmpty()) {
            throw invalidModel(String.format(SimpleOwnerAuthorizationCollaborationConstant.DETAIL_FIELD_CANNOT_BE_EMPTY,
                    fieldName));
        }
        return value;
    }

    /**
     * 校验正整数。
     *
     * @param value     字段值
     * @param fieldName 字段名
     * @return 原始整数
     */
    public static Long requirePositive(Long value, String fieldName) {
        if (value == null) {
            throw invalidModel(String.format(SimpleOwnerAuthorizationCollaborationConstant.DETAIL_FIELD_CANNOT_BE_NULL,
                    fieldName));
        }
        if (value.longValue() <= SimpleOwnerAuthorizationCollaborationConstant.LONG_ZERO) {
            throw invalidModel(String.format(SimpleOwnerAuthorizationCollaborationConstant.DETAIL_FIELD_MUST_BE_POSITIVE,
                    fieldName));
        }
        return value;
    }

    /**
     * 校验非负整数。
     *
     * @param value     字段值
     * @param fieldName 字段名
     * @return 原始整数
     */
    public static Long requireNonNegative(Long value, String fieldName) {
        if (value == null) {
            throw invalidModel(String.format(SimpleOwnerAuthorizationCollaborationConstant.DETAIL_FIELD_CANNOT_BE_NULL,
                    fieldName));
        }
        if (value.longValue() < SimpleOwnerAuthorizationCollaborationConstant.LONG_ZERO) {
            throw invalidModel(String.format(SimpleOwnerAuthorizationCollaborationConstant.DETAIL_FIELD_CANNOT_BE_NEGATIVE,
                    fieldName));
        }
        return value;
    }

    /**
     * 递归复制 JSON 形态载荷，避免 provider 或调用方通过嵌套集合篡改已验证快照。
     *
     * @param payload 原始载荷
     * @return 不可变载荷
     */
    public static Map<String, Object> freezePayload(Map<String, Object> payload) {
        if (payload == null) {
            throw invalidModel(String.format(SimpleOwnerAuthorizationCollaborationConstant.DETAIL_FIELD_CANNOT_BE_NULL,
                    SimpleOwnerAuthorizationCollaborationConstant.FIELD_PAYLOAD));
        }
        return freezeMap(payload, new IdentityHashMap<Object, Boolean>());
    }

    /**
     * 复制变更列表，空列表统一为不可变空列表。
     *
     * @param changes 原始变更列表
     * @return 不可变变更列表
     */
    public static List<OwnerAuthorizationChange> freezeChanges(List<OwnerAuthorizationChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return Collections.emptyList();
        }
        if (changes.contains(null)) {
            throw invalidModel(SimpleOwnerAuthorizationCollaborationConstant.DETAIL_CHANGES_CANNOT_CONTAIN_NULL);
        }
        return Collections.unmodifiableList(new ArrayList<OwnerAuthorizationChange>(changes));
    }

    private static Map<String, Object> freezeMap(Map<?, ?> source, IdentityHashMap<Object, Boolean> visiting) {
        enter(source, visiting);
        try {
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw invalidModel(SimpleOwnerAuthorizationCollaborationConstant.DETAIL_PAYLOAD_KEY_MUST_BE_STRING);
                }
                copy.put((String) entry.getKey(), freezeValue(entry.getValue(), visiting));
            }
            return Collections.unmodifiableMap(copy);
        } finally {
            visiting.remove(source);
        }
    }

    private static List<Object> freezeList(List<?> source, IdentityHashMap<Object, Boolean> visiting) {
        enter(source, visiting);
        try {
            List<Object> copy = new ArrayList<Object>(source.size());
            for (Object value : source) {
                copy.add(freezeValue(value, visiting));
            }
            return Collections.unmodifiableList(copy);
        } finally {
            visiting.remove(source);
        }
    }

    private static Object freezeValue(Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (value instanceof Map) {
            return freezeMap((Map<?, ?>) value, visiting);
        }
        if (value instanceof List) {
            return freezeList((List<?>) value, visiting);
        }
        if (!isJsonScalar(value) || isNonFiniteNumber(value)) {
            throw invalidModel(SimpleOwnerAuthorizationCollaborationConstant.DETAIL_PAYLOAD_VALUE_MUST_BE_JSON);
        }
        return value;
    }

    private static boolean isJsonScalar(Object value) {
        return value == null || value instanceof String || value instanceof Boolean || value instanceof Byte
                || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double || value instanceof BigInteger
                || value instanceof BigDecimal;
    }

    private static boolean isNonFiniteNumber(Object value) {
        return value instanceof Double && (((Double) value).isInfinite() || ((Double) value).isNaN())
                || value instanceof Float && (((Float) value).isInfinite() || ((Float) value).isNaN());
    }

    private static void enter(Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw invalidModel(
                    SimpleOwnerAuthorizationCollaborationConstant.DETAIL_PAYLOAD_CANNOT_CONTAIN_CYCLIC_CONTAINERS);
        }
    }

    /**
     * 创建协作模型校验异常。
     *
     * @param detail 安全详情
     * @return 携带稳定错误码的异常
     */
    public static OwnerAuthorizationCollaborationException invalidModel(String detail) {
        return new OwnerAuthorizationCollaborationException(ErrorCode.INVALID_OWNER_AUTHORIZATION_MODEL,
                String.format(ErrorMessage.INVALID_OWNER_AUTHORIZATION_MODEL, detail));
    }

    /**
     * 创建携带根因的协作模型校验异常。
     *
     * @param detail 安全详情
     * @param cause  根因
     * @return 携带稳定错误码的异常
     */
    public static OwnerAuthorizationCollaborationException invalidModel(String detail, Throwable cause) {
        return new OwnerAuthorizationCollaborationException(ErrorCode.INVALID_OWNER_AUTHORIZATION_MODEL,
                String.format(ErrorMessage.INVALID_OWNER_AUTHORIZATION_MODEL, detail), cause);
    }
}
