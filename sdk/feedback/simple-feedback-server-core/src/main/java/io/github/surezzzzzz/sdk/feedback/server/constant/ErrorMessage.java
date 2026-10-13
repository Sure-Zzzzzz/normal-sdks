package io.github.surezzzzzz.sdk.feedback.server.constant;

/**
 * Error Message Constants（与 ErrorCode 一一对应）
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 校验类 ====================

    /**
     * 评分越界
     */
    public static final String SCORE_OUT_OF_RANGE = "满意度打分必须在 %d-%d 之间";

    /**
     * 反馈文本为空或超长
     */
    public static final String CONTENT_INVALID = "反馈文本不能为空且不超过 %d 字符";

    /**
     * 联系邮箱格式非法
     */
    public static final String EMAIL_INVALID = "联系邮箱格式非法";

    /**
     * 字段长度超限
     */
    public static final String FIELD_TOO_LONG = "字段 %s 超过长度上限 %d";

    /**
     * 环境快照含非法键
     */
    public static final String ENVIRONMENT_KEY_REJECTED = "环境快照仅允许键：%s";

    /**
     * 应用编码格式非法
     */
    public static final String APPLICATION_CODE_INVALID = "应用编码格式非法（小写字母数字与连字符，2-64 位，首尾为字母数字）";

    /**
     * 枚举字段值非法
     */
    public static final String ENUM_VALUE_INVALID = "字段 %s 的值非法，仅允许：%s";

    // ==================== 附件类 ====================

    /**
     * 附件能力未开放
     */
    public static final String ATTACHMENT_NOT_ENABLED = "附件能力未开放";

    /**
     * 附件媒体类型不支持
     */
    public static final String ATTACHMENT_MEDIA_TYPE_REJECTED = "附件媒体类型不支持，仅允许：%s";

    /**
     * 附件单文件大小超限
     */
    public static final String ATTACHMENT_SIZE_EXCEEDED = "附件单文件大小超过上限 %d 字节";

    /**
     * 附件总大小超限
     */
    public static final String ATTACHMENT_TOTAL_SIZE_EXCEEDED = "附件总大小超过上限 %d 字节";

    /**
     * 附件数量超限
     */
    public static final String ATTACHMENT_COUNT_EXCEEDED = "附件数量超过上限 %d";

    // ==================== 状态类 ====================

    /**
     * 非法状态迁移
     */
    public static final String ILLEGAL_STATUS_TRANSITION = "非法状态迁移：%s 不能迁移到 %s";

    /**
     * 行版本冲突
     */
    public static final String ROW_VERSION_CONFLICT = "数据已被他人修改，请刷新后重试";

    // ==================== 幂等类 ====================

    /**
     * 同幂等键异摘要
     */
    public static final String IDEMPOTENCY_DIGEST_MISMATCH = "幂等键已关联其他提交内容";

    // ==================== 资源类 ====================

    /**
     * 注册项未命中或已停用
     */
    public static final String APPLICATION_NOT_AVAILABLE = "目标应用未登记或已停用";

    /**
     * 资源不存在
     */
    public static final String SUBMISSION_NOT_FOUND = "反馈不存在";

    /**
     * 注册项不存在
     */
    public static final String APPLICATION_NOT_FOUND = "应用不存在";

    /**
     * 应用编码已登记
     */
    public static final String APPLICATION_ALREADY_REGISTERED = "应用编码已登记：%s";

    // ==================== 频控类 ====================

    /**
     * 频控窗口超限
     */
    public static final String RATE_LIMITED = "提交过于频繁，请稍后再试";

    // ==================== 依赖类 ====================

    /**
     * 外部依赖故障
     */
    public static final String DEPENDENCY_UNAVAILABLE = "服务暂不可用，请稍后再试";

    /**
     * 请求体过大（multipart 层承载）
     */
    public static final String REQUEST_TOO_LARGE = "请求体过大";

    // ==================== 来源类 ====================

    /**
     * IAM 同源字段改写被拒（同步请显式传 syncFromIam=true）
     */
    public static final String IAM_SOURCED_FIELD_LOCKED =
            "该应用与 IAM 可信应用同编码，展示名以 IAM 为准不可改写（同步请传 syncFromIam=true）";
}
