package io.github.surezzzzzz.sdk.log.truncate.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@LogTruncateComponent
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.log.truncate")
public class LogTruncateProperties {
    /**
     * 最大总字节数(UTF-8)，整体日志字符串限制
     */
    private int maxTotalBytes = 8 * 1024;

    /**
     * 对 JSON 文本值逐字段做字符截断（按 code point 计）
     */
    private int maxFieldChars = 1024;

    /**
     * 对象展开最大深度（超过深度以占位符输出）
     */
    private int maxDepth = 8;

    /**
     * 截断后缀
     */
    private String ellipsis = "...";

    /**
     * 超过限制的提示模板，{dropped} 会替换成被截断的字节/字符数量
     */
    private String truncatedNoteTemplate = " [truncated {dropped}]";

    /**
     * 超过最大深度时的占位符
     */
    private String depthExceededPlaceholder = "__depth_exceeded__";
}
