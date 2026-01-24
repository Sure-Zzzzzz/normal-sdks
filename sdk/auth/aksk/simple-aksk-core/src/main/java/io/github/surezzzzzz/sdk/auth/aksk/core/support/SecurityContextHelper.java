package io.github.surezzzzzz.sdk.auth.aksk.core.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.core.exception.AkskException;

/**
 * Security Context 处理工具类
 * <p>
 * 用于处理 security_context 的通用逻辑，支持多种格式（JSON、字符串、数字、布尔等）
 *
 * @author Sure
 * @since 1.0.0
 */
public final class SecurityContextHelper {

    private SecurityContextHelper() {
        throw new AkskException(ErrorMessage.UTILITY_CLASS_INSTANTIATION);
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 验证 security_context 大小
     *
     * @param context security_context（任意类型）
     * @param maxSize 最大字节数
     * @throws AkskException 如果超过大小限制
     */
    public static void validateSize(Object context, int maxSize) {
        if (context == null) {
            return;
        }

        String contextStr = convertToString(context);
        if (contextStr.length() > maxSize) {
            throw new AkskException(
                    ErrorCode.SECURITY_CONTEXT_SIZE_EXCEEDED,
                    String.format(ErrorMessage.SECURITY_CONTEXT_SIZE_EXCEEDED, contextStr.length(), maxSize));
        }
    }

    /**
     * 将 security_context 转换为字符串（用于大小验证和日志）
     *
     * @param context security_context（任意类型）
     * @return 字符串表示
     */
    public static String convertToString(Object context) {
        if (context == null) {
            return "";
        }
        if (context instanceof String) {
            return (String) context;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(context);
        } catch (JsonProcessingException e) {
            throw new AkskException(ErrorCode.VALIDATION_FAILED, "Failed to serialize security_context", e);
        }
    }
}
