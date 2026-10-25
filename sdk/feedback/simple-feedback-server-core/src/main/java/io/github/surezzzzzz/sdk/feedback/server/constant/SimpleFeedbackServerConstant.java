package io.github.surezzzzzz.sdk.feedback.server.constant;

/**
 * Simple Feedback Server 常量
 *
 * @author surezzzzzz
 */
public final class SimpleFeedbackServerConstant {

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.feedback.server";

    // ==================== 配置相关常量 ====================
    /**
     * REST 根路径默认值
     */
    public static final String DEFAULT_API_BASE_PATH = "/api/feedback";
    /**
     * 满意度打分下限
     */
    public static final int SCORE_MIN = 1;

    // ==================== 评分常量 ====================
    /**
     * 满意度打分上限
     */
    public static final int SCORE_MAX = 10;
    /**
     * 反馈文本长度上限（字符）
     */
    public static final int CONTENT_MAX_LENGTH = 2000;

    // ==================== 字段长度上限 ====================
    /**
     * 联系邮箱长度上限（字符，RFC 上限）
     */
    public static final int EMAIL_MAX_LENGTH = 254;
    /**
     * 应用显示名长度上限（字符）
     */
    public static final int APPLICATION_DISPLAY_NAME_MAX_LENGTH = 64;
    /**
     * 应用描述长度上限（字符）
     */
    public static final int APPLICATION_DESCRIPTION_MAX_LENGTH = 200;
    /**
     * 页面上下文路径长度上限（字符）
     */
    public static final int CONTEXT_PATH_MAX_LENGTH = 500;
    /**
     * 环境快照 JSON 总长度上限（字符；容纳超长 UA）
     */
    public static final int ENVIRONMENT_MAX_LENGTH = 2000;
    /**
     * 处理备注长度上限（字符）
     */
    public static final int HANDLER_NOTE_MAX_LENGTH = 1000;
    /**
     * 处理回复长度上限（字符）
     */
    public static final int REPLY_MAX_LENGTH = 1000;
    /**
     * 提交人显示名快照长度上限（字符）
     */
    public static final int SUBMITTER_DISPLAY_NAME_MAX_LENGTH = 64;
    /**
     * 附件原始文件名长度上限（字符）
     */
    public static final int ATTACHMENT_FILENAME_MAX_LENGTH = 255;
    /**
     * 附件存储键（objectKey）长度上限（字符）
     */
    public static final int ATTACHMENT_STORAGE_KEY_MAX_LENGTH = 500;
    /**
     * 幂等键长度上限（与提交表字段一致）
     */
    public static final int IDEMPOTENCY_KEY_MAX_LENGTH = 64;
    /**
     * 附件兜底文件名（原始文件名缺失时）
     */
    public static final String DEFAULT_ATTACHMENT_FILENAME = "attachment";
    /**
     * 应用编码正则：小写字母数字与连字符，2-64 位，首尾为字母数字
     */
    public static final String APPLICATION_CODE_PATTERN = "^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$";

    // ==================== 应用编码规则 ====================
    /**
     * 环境快照允许的键（ua/视口/主题/语言，多余键服务端丢弃）
     */
    public static final String[] ENVIRONMENT_ALLOWED_KEYS = {"ua", "viewport", "theme", "locale"};

    // ==================== 环境快照白名单 ====================
    /**
     * 频控短窗时长（秒）
     */
    public static final int RATE_LIMIT_SHORT_WINDOW_SECONDS = 60;

    // ==================== 频控默认档位 ====================
    /**
     * 频控短窗条数上限
     */
    public static final int RATE_LIMIT_SHORT_WINDOW_MAX = 1;
    /**
     * 频控长窗时长（秒）
     */
    public static final int RATE_LIMIT_LONG_WINDOW_SECONDS = 3600;
    /**
     * 频控长窗条数上限
     */
    public static final int RATE_LIMIT_LONG_WINDOW_MAX = 10;
    /**
     * 附件数量上限
     */
    public static final int ATTACHMENT_MAX_COUNT = 3;

    // ==================== 附件默认上限 ====================
    /**
     * 附件单文件大小上限（字节，5MB）
     */
    public static final long ATTACHMENT_MAX_SIZE_BYTES = 5 * 1024 * 1024L;
    /**
     * 附件总大小上限（字节，10MB）
     */
    public static final long ATTACHMENT_MAX_TOTAL_SIZE_BYTES = 10 * 1024 * 1024L;
    /**
     * 附件媒体类型白名单（jpg/png/webp）
     */
    public static final String[] ATTACHMENT_ALLOWED_CONTENT_TYPES = {
            "image/jpeg",
            "image/png",
            "image/webp"
    };
    /**
     * 分页默认页大小
     */
    public static final int PAGE_DEFAULT_SIZE = 20;

    // ==================== 分页默认值 ====================
    /**
     * 分页页大小上限
     */
    public static final int PAGE_MAX_SIZE = 100;
    /**
     * 模板：人员 owner 形态 "iam:{subjectId}"
     * 参数: subjectId
     */
    public static final String TEMPLATE_OWNER_IAM = "iam:%s";

    // ==================== owner 模板 ====================
    /**
     * 模板：机器 owner 形态 "aksk:{clientId}"
     * 参数: clientId
     */
    public static final String TEMPLATE_OWNER_AKSK = "aksk:%s";
    /**
     * 模板：附件存储键 "feedback/{submissionId}/{uuid}.{ext}"
     * 参数: submissionId, uuid, ext
     */
    public static final String TEMPLATE_ATTACHMENT_STORAGE_KEY = "feedback/%s/%s.%s";
    /**
     * 邮箱格式正则（务实校验：local@domain，域名至少两段）
     */
    public static final String EMAIL_PATTERN = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    private SimpleFeedbackServerConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
