package io.github.surezzzzzz.sdk.feedback.server.constant;

/**
 * Error Code Constants（按配置、校验、附件、状态、幂等、资源、频控、依赖和来源分组；401/403 由底座给出）
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    /**
     * 身份协作装配不完整
     */
    public static final String COLLABORATION_ASSEMBLY_INCOMPLETE = "CONFIG_001";

    // ==================== 配置类 ====================
    /**
     * 评分越界
     */
    public static final String SCORE_OUT_OF_RANGE = "VALIDATION_001";

    // ==================== 校验类 ====================
    /**
     * 反馈文本为空或超长
     */
    public static final String CONTENT_INVALID = "VALIDATION_002";
    /**
     * 联系邮箱格式非法
     */
    public static final String EMAIL_INVALID = "VALIDATION_003";
    /**
     * 字段长度超限（contextPath/environment/note/reply/displayName 等）
     */
    public static final String FIELD_TOO_LONG = "VALIDATION_004";
    /**
     * 环境快照含非法键
     */
    public static final String ENVIRONMENT_KEY_REJECTED = "VALIDATION_005";
    /**
     * 应用编码格式非法
     */
    public static final String APPLICATION_CODE_INVALID = "VALIDATION_006";
    /**
     * 枚举字段值非法（kind/type 等）
     */
    public static final String ENUM_VALUE_INVALID = "VALIDATION_007";
    /**
     * 必填字段缺失
     */
    public static final String REQUIRED_FIELD_MISSING = "VALIDATION_008";
    /**
     * 请求参数或请求体无法解析
     */
    public static final String INVALID_REQUEST = "VALIDATION_009";
    /**
     * 附件能力未开放（散形态）
     */
    public static final String ATTACHMENT_NOT_ENABLED = "ATTACHMENT_001";

    // ==================== 附件类 ====================
    /**
     * 附件媒体类型不支持
     */
    public static final String ATTACHMENT_MEDIA_TYPE_REJECTED = "ATTACHMENT_002";
    /**
     * 附件单文件大小超限
     */
    public static final String ATTACHMENT_SIZE_EXCEEDED = "ATTACHMENT_003";
    /**
     * 附件总大小超限
     */
    public static final String ATTACHMENT_TOTAL_SIZE_EXCEEDED = "ATTACHMENT_004";
    /**
     * 附件数量超限
     */
    public static final String ATTACHMENT_COUNT_EXCEEDED = "ATTACHMENT_005";
    /**
     * 非法状态迁移
     */
    public static final String ILLEGAL_STATUS_TRANSITION = "STATE_001";

    // ==================== 状态类 ====================
    /**
     * 行版本冲突
     */
    public static final String ROW_VERSION_CONFLICT = "STATE_002";
    /**
     * 同幂等键异摘要
     */
    public static final String IDEMPOTENCY_DIGEST_MISMATCH = "IDEMPOTENCY_001";

    // ==================== 幂等类 ====================
    /**
     * 注册项未命中或已停用（提交时）
     */
    public static final String APPLICATION_NOT_AVAILABLE = "RESOURCE_001";

    // ==================== 资源类 ====================
    /**
     * 资源不存在（管理详情，安全 404）
     */
    public static final String SUBMISSION_NOT_FOUND = "RESOURCE_002";
    /**
     * 注册项不存在（管理侧）
     */
    public static final String APPLICATION_NOT_FOUND = "RESOURCE_003";
    /**
     * 应用编码已登记（注册表唯一键冲突）
     */
    public static final String APPLICATION_ALREADY_REGISTERED = "RESOURCE_004";
    /**
     * 频控窗口超限
     */
    public static final String RATE_LIMITED = "RATE_LIMIT_001";

    // ==================== 频控类 ====================
    /**
     * 外部依赖故障（introspect/MySQL/S3，fail-close）
     */
    public static final String DEPENDENCY_UNAVAILABLE = "DEPENDENCY_001";

    // ==================== 依赖类 ====================
    /**
     * IAM 同源字段改写被拒（同源行展示名由 IAM 单一事实源承担）
     */
    public static final String IAM_SOURCED_FIELD_LOCKED = "SOURCE_001";

    // ==================== 来源类 ====================

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }
}
