package io.github.surezzzzzz.sdk.elasticsearch.route.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.function.Predicate;

/**
 * Elasticsearch 版本兼容性错误模式
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public enum VersionCompatibilityErrorPattern {

    /**
     * 未识别的参数错误
     */
    UNRECOGNIZED_PARAMETER("unrecognized parameter",
            msg -> msg.contains("unrecognized parameter")),

    /**
     * master_timeout 参数问题（ES 8.x 移除）
     */
    MASTER_TIMEOUT("master_timeout parameter",
            msg -> msg.contains("master_timeout")),

    /**
     * 参数不存在
     */
    NO_SUCH_PARAMETER("no such parameter",
            msg -> msg.contains("no such parameter")),

    /**
     * 未知设置
     */
    UNKNOWN_SETTING("unknown setting",
            msg -> msg.contains("unknown setting")),

    /**
     * 未知查询
     */
    UNKNOWN_QUERY("unknown query",
            msg -> msg.contains("unknown query")),

    /**
     * 未找到 URI 处理器
     */
    NO_HANDLER_FOR_URI("no handler found for uri",
            msg -> msg.contains("no handler found for uri")),

    /**
     * 非法参数异常（涉及参数或设置）
     */
    ILLEGAL_ARGUMENT_PARAM_OR_SETTING("illegal_argument_exception with parameter/setting",
            msg -> msg.contains("illegal_argument_exception") &&
                    (msg.contains("parameter") || msg.contains("setting"))),

    /**
     * 映射类型已废弃（ES 7.x+）
     */
    MAPPING_TYPE_DEPRECATED("mapping types are deprecated",
            msg -> msg.contains("types removal") || msg.contains("include_type_name")),

    /**
     * 不支持的操作（版本差异）
     */
    UNSUPPORTED_OPERATION("unsupported operation",
            msg -> msg.contains("unsupported_operation_exception")),
    ;

    /**
     * 错误描述
     */
    private final String description;

    /**
     * 检测策略
     */
    private final Predicate<String> detector;

    /**
     * 检测消息是否匹配此模式
     *
     * @param message 错误消息（小写）
     * @return 是否匹配
     */
    public boolean matches(String message) {
        if (message == null) {
            return false;
        }
        return detector.test(message);
    }

    /**
     * 检测消息是否匹配任一兼容性错误模式
     *
     * @param message 错误消息
     * @return 是否为兼容性问题
     */
    public static boolean isAnyMatch(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }

        String lowerMessage = message.toLowerCase();
        return Arrays.stream(values())
                .anyMatch(pattern -> pattern.matches(lowerMessage));
    }

    /**
     * 找到匹配的错误模式
     *
     * @param message 错误消息
     * @return 匹配的模式，可能为 null
     */
    public static VersionCompatibilityErrorPattern findMatch(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }

        String lowerMessage = message.toLowerCase();
        return Arrays.stream(values())
                .filter(pattern -> pattern.matches(lowerMessage))
                .findFirst()
                .orElse(null);
    }
}
