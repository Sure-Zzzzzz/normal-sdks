package io.github.surezzzzzz.sdk.ops.middleware.support;

/**
 * 运维日志安全输出帮助器。
 *
 * @author surezzzzzz
 */
public final class MiddlewareOpsLogHelper {

    private static final int MAX_CAUSE_DEPTH = 16;
    private static final int MAX_IDENTIFIER_LENGTH = 128;

    private MiddlewareOpsLogHelper() {
        throw new UnsupportedOperationException("帮助类不能实例化");
    }

    public static Throwable sanitizedThrowable(Throwable throwable) {
        return sanitizedThrowable(throwable, 0);
    }

    public static String identifier(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder result = new StringBuilder(Math.min(value.length(), MAX_IDENTIFIER_LENGTH));
        for (int index = 0; index < value.length() && result.length() < MAX_IDENTIFIER_LENGTH; index++) {
            char current = value.charAt(index);
            if (current >= 'a' && current <= 'z' || current >= 'A' && current <= 'Z'
                    || current >= '0' && current <= '9' || current == '.' || current == '_' || current == '-') {
                result.append(current);
            } else {
                result.append('*');
            }
        }
        return result.toString();
    }

    private static Throwable sanitizedThrowable(Throwable throwable, int depth) {
        if (throwable == null) {
            return new RuntimeException("unknown");
        }
        RuntimeException sanitized = new RuntimeException(throwable.getClass().getName());
        sanitized.setStackTrace(throwable.getStackTrace());
        Throwable cause = throwable.getCause();
        if (cause != null && cause != throwable && depth < MAX_CAUSE_DEPTH) {
            sanitized.initCause(sanitizedThrowable(cause, depth + 1));
        }
        return sanitized;
    }
}
