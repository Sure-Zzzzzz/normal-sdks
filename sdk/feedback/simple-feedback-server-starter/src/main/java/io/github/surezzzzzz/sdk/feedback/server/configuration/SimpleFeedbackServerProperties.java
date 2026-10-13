package io.github.surezzzzzz.sdk.feedback.server.configuration;

import io.github.surezzzzzz.sdk.feedback.server.constant.SimpleFeedbackServerConstant;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple Feedback Server Properties（单一配置类，键面见 DESIGN §4.2；
 * 默认值全部引用常量；敏感值一律部署环境注入不进示例实值）
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleFeedbackServerConstant.CONFIG_PREFIX)
@ConditionalOnProperty(prefix = SimpleFeedbackServerConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
public class SimpleFeedbackServerProperties {

    /**
     * 是否启用（默认 false）
     */
    private boolean enable = false;

    /**
     * REST 根路径
     */
    private String apiBasePath = SimpleFeedbackServerConstant.DEFAULT_API_BASE_PATH;

    /**
     * 频控配置（双档计数窗，同 owner 维度）
     */
    private RateLimitConfig rateLimit = new RateLimitConfig();

    /**
     * 附件配置（散形态下附件能力缺席，见 attachmentEnabled 语义）
     */
    private AttachmentConfig attachment = new AttachmentConfig();

    /**
     * 分页配置
     */
    private PageConfig page = new PageConfig();

    /**
     * 频控双档配置
     */
    @Data
    public static class RateLimitConfig {

        /**
         * 短窗时长（秒）
         */
        private int shortWindowSeconds = SimpleFeedbackServerConstant.RATE_LIMIT_SHORT_WINDOW_SECONDS;

        /**
         * 短窗条数上限
         */
        private int shortWindowMax = SimpleFeedbackServerConstant.RATE_LIMIT_SHORT_WINDOW_MAX;

        /**
         * 长窗时长（秒）
         */
        private int longWindowSeconds = SimpleFeedbackServerConstant.RATE_LIMIT_LONG_WINDOW_SECONDS;

        /**
         * 长窗条数上限
         */
        private int longWindowMax = SimpleFeedbackServerConstant.RATE_LIMIT_LONG_WINDOW_MAX;
    }

    /**
     * 附件配置
     */
    @Data
    public static class AttachmentConfig {

        /**
         * 附件能力开关：S3 件缺席（散形态）时无论此值如何，附件能力恒降级（依赖表达聚散，不做配置式假聚）
         */
        private boolean enabled = true;

        /**
         * 附件桶名（建议半年过期生命周期桶，兜底孤儿对象）
         */
        private String bucketName = "feedback-attachment";

        /**
         * 附件数量上限
         */
        private int maxCount = SimpleFeedbackServerConstant.ATTACHMENT_MAX_COUNT;

        /**
         * 附件单文件大小上限（字节）
         */
        private long maxSizeBytes = SimpleFeedbackServerConstant.ATTACHMENT_MAX_SIZE_BYTES;

        /**
         * 附件总大小上限（字节）
         */
        private long maxTotalSizeBytes = SimpleFeedbackServerConstant.ATTACHMENT_MAX_TOTAL_SIZE_BYTES;

        /**
         * 附件媒体类型白名单
         */
        private String[] allowedContentTypes = SimpleFeedbackServerConstant.ATTACHMENT_ALLOWED_CONTENT_TYPES;
    }

    /**
     * 分页配置
     */
    @Data
    public static class PageConfig {

        /**
         * 分页默认页大小
         */
        private int defaultSize = SimpleFeedbackServerConstant.PAGE_DEFAULT_SIZE;

        /**
         * 分页页大小上限
         */
        private int maxSize = SimpleFeedbackServerConstant.PAGE_MAX_SIZE;
    }
}
