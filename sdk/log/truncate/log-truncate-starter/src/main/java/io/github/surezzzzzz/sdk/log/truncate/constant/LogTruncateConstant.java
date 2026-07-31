package io.github.surezzzzzz.sdk.log.truncate.constant;

/**
 * 日志截断常量
 *
 * @author surezzzzzz
 */
public final class LogTruncateConstant {

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.log.truncate";

    /**
     * 默认截断器 Bean 名称
     */
    public static final String LOG_TRUNCATOR_BEAN_NAME = "logTruncator";

    /**
     * 默认总字节上限
     */
    public static final int DEFAULT_MAX_TOTAL_BYTES = 8 * 1024;

    /**
     * 默认字段字符上限
     */
    public static final int DEFAULT_MAX_FIELD_CHARS = 1024;

    /**
     * 默认最大深度
     */
    public static final int DEFAULT_MAX_DEPTH = 8;

    /**
     * 默认省略号
     */
    public static final String DEFAULT_ELLIPSIS = "...";

    /**
     * 默认截断提示模板
     */
    public static final String DEFAULT_TRUNCATED_NOTE_TEMPLATE = " [truncated {dropped}]";

    /**
     * 默认深度超限占位符
     */
    public static final String DEFAULT_DEPTH_EXCEEDED_PLACEHOLDER = "__depth_exceeded__";

    /**
     * 截断数量占位符
     */
    public static final String DROPPED_PLACEHOLDER = "{dropped}";

    private LogTruncateConstant() {
        throw new UnsupportedOperationException("日志截断常量类不能实例化");
    }
}
