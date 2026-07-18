package io.github.surezzzzzz.sdk.limiter.redis.smart.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SmartRedisLimiter 扩展属性快照 Helper
 *
 * @author surezzzzzz
 */
public final class SmartRedisLimiterAttributeSnapshotHelper {

    private SmartRedisLimiterAttributeSnapshotHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 创建扩展属性的递归快照
     *
     * <p>支持 JSON 基础值、枚举、Instant、UUID、Map、List、Set 和数组。
     * Map 的键必须为 String；Set 保持集合语义，数组在快照中转换为不可变 List。
     *
     * @param attributes 扩展属性
     * @return 扩展属性快照
     * @throws SmartRedisLimiterException 属性类型不受支持或存在循环引用时抛出
     */
    public static Map<String, Object> snapshot(Map<String, Object> attributes) {
        return snapshot(attributes, true);
    }

    /**
     * 创建扩展属性的严格递归不可变快照
     *
     * @param attributes 扩展属性
     * @return 递归不可变快照
     * @throws SmartRedisLimiterException 属性类型不受支持或存在循环引用时抛出
     */
    public static Map<String, Object> snapshotStrict(Map<String, Object> attributes) {
        return snapshot(attributes, true);
    }

    private static Map<String, Object> snapshot(Map<String, Object> attributes, boolean strict) {
        if (attributes == null || attributes.isEmpty()) {
            return Collections.emptyMap();
        }
        return snapshotMap(attributes, new IdentityHashMap<Object, Boolean>(), strict);
    }

    private static Map<String, Object> snapshotMap(Map<?, ?> source,
                                                   IdentityHashMap<Object, Boolean> visiting,
                                                   boolean strict) {
        enter(source, visiting);
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>(source.size());
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw invalidAttribute();
                }
                snapshot.put((String) entry.getKey(), snapshotValue(entry.getValue(), visiting, strict));
            }
            return Collections.unmodifiableMap(snapshot);
        } finally {
            visiting.remove(source);
        }
    }

    private static Object snapshotValue(Object value,
                                        IdentityHashMap<Object, Boolean> visiting,
                                        boolean strict) {
        if (value == null || isImmutableValue(value)) {
            return value;
        }
        if (value instanceof Map) {
            return snapshotMap((Map<?, ?>) value, visiting, strict);
        }
        if (value instanceof Set) {
            return snapshotCollection((Collection<?>) value, visiting, true, strict);
        }
        if (value instanceof Collection) {
            return snapshotCollection((Collection<?>) value, visiting, false, strict);
        }
        if (value.getClass().isArray()) {
            return snapshotArray(value, visiting, strict);
        }
        throw invalidAttribute();
    }

    private static Collection<Object> snapshotCollection(Collection<?> source,
                                                         IdentityHashMap<Object, Boolean> visiting,
                                                         boolean set,
                                                         boolean strict) {
        enter(source, visiting);
        try {
            if (set) {
                Set<Object> snapshot = new LinkedHashSet<>(source.size());
                for (Object item : source) {
                    snapshot.add(snapshotValue(item, visiting, strict));
                }
                return Collections.unmodifiableSet(snapshot);
            }
            List<Object> snapshot = new ArrayList<>(source.size());
            for (Object item : source) {
                snapshot.add(snapshotValue(item, visiting, strict));
            }
            return Collections.unmodifiableList(snapshot);
        } finally {
            visiting.remove(source);
        }
    }

    private static List<Object> snapshotArray(Object source,
                                              IdentityHashMap<Object, Boolean> visiting,
                                              boolean strict) {
        enter(source, visiting);
        try {
            int length = Array.getLength(source);
            List<Object> snapshot = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                snapshot.add(snapshotValue(Array.get(source, i), visiting, strict));
            }
            return Collections.unmodifiableList(snapshot);
        } finally {
            visiting.remove(source);
        }
    }

    private static boolean isImmutableValue(Object value) {
        return value instanceof String
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof BigInteger
                || value instanceof BigDecimal
                || value instanceof Enum
                || value instanceof Instant
                || value instanceof UUID;
    }

    private static void enter(Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw invalidAttribute();
        }
    }

    private static SmartRedisLimiterException invalidAttribute() {
        return new SmartRedisLimiterException(
                ErrorCode.ATTRIBUTE_VALUE_INVALID,
                String.format(ErrorMessage.ATTRIBUTE_VALUE_INVALID,
                        ErrorMessage.REASON_ATTRIBUTE_TYPE_UNSUPPORTED));
    }
}
