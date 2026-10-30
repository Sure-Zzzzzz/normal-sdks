package io.github.surezzzzzz.sdk.license.core.support;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 协议值校验与冻结：整数不截断，签名快照不共享调用方可变对象。
 *
 * @author surezzzzzz
 */
public final class ProtocolValueHelper {

    /** JSON 安全整数上限，避免不同语言客户端因浮点精度产生不同业务数量。 */
    public static final long SAFE_INTEGER_MAX = 9007199254740991L;

    private ProtocolValueHelper() {
    }

    /** 只接受 JSON 整数对应类型；小数、指数浮点及超过安全整数范围的数值均拒绝。 */
    public static long integer(Object value) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger)) {
            throw invalid();
        }
        BigInteger integer = value instanceof BigInteger ? (BigInteger) value
                : BigInteger.valueOf(((Number) value).longValue());
        if (integer.abs().compareTo(BigInteger.valueOf(SAFE_INTEGER_MAX)) > 0) {
            throw invalid();
        }
        return integer.longValue();
    }

    /** 深复制并冻结 JSON 对象；嵌套集合不得在通过校验后被调用方原地改写。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> immutableObject(Map<String, Object> value) {
        return value == null ? Collections.<String, Object>emptyMap()
                : (Map<String, Object>) immutable(value);
    }

    /** 深复制并冻结 JSON 数组。 */
    @SuppressWarnings("unchecked")
    public static <T> List<T> immutableList(List<T> value) {
        return value == null ? Collections.<T>emptyList() : (List<T>) immutable(value);
    }

    private static Object immutable(Object value) {
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw invalid();
                }
                result.put((String) entry.getKey(), immutable(entry.getValue()));
            }
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List) {
            List<Object> result = new ArrayList<Object>();
            for (Object item : (List<?>) value) {
                result.add(immutable(item));
            }
            return Collections.unmodifiableList(result);
        }
        if (value instanceof Number) {
            // 先校验安全整数边界，保留合法整数的原始类型，避免改变公开快照值的兼容语义。
            integer(value);
            return value;
        }
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        throw invalid();
    }

    private static LicenseCoreException invalid() {
        return new LicenseCoreException(ErrorCode.PROTOCOL_FIELD, ErrorMessage.PROTOCOL_FIELD_INVALID);
    }
}
