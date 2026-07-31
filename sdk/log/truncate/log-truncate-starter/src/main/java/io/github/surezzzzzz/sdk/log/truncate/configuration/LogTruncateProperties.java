package io.github.surezzzzzz.sdk.log.truncate.configuration;

import io.github.surezzzzzz.sdk.log.truncate.constant.LogTruncateConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志截断配置
 *
 * @author surezzzzzz
 */
@Data
@LogTruncateComponent
@ConfigurationProperties(prefix = LogTruncateConstant.CONFIG_PREFIX)
public class LogTruncateProperties {

    /**
     * 最终日志字符串最大字节数
     */
    private int maxTotalBytes = LogTruncateConstant.DEFAULT_MAX_TOTAL_BYTES;

    /**
     * JSON 文本字段最大 code point 数
     */
    private int maxFieldChars = LogTruncateConstant.DEFAULT_MAX_FIELD_CHARS;

    /**
     * 对象展开最大深度
     */
    private int maxDepth = LogTruncateConstant.DEFAULT_MAX_DEPTH;

    /**
     * 截断后缀
     */
    private String ellipsis = LogTruncateConstant.DEFAULT_ELLIPSIS;

    /**
     * 截断提示模板
     */
    private String truncatedNoteTemplate = LogTruncateConstant.DEFAULT_TRUNCATED_NOTE_TEMPLATE;

    /**
     * 深度超限占位符
     */
    private String depthExceededPlaceholder = LogTruncateConstant.DEFAULT_DEPTH_EXCEEDED_PLACEHOLDER;
}
