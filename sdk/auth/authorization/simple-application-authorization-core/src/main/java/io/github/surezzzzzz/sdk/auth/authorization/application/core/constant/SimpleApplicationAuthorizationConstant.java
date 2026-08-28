package io.github.surezzzzzz.sdk.auth.authorization.application.core.constant;

/**
 * 应用授权常量。
 *
 * @author surezzzzzz
 */
public final class SimpleApplicationAuthorizationConstant {

    /**
     * 授权协议名称。
     */
    public static final String PROTOCOL = "simple-application-authorization";
    /**
     * 授权协议版本。
     */
    public static final String VERSION = "1.0";
    /**
     * protocol 字段。
     */
    public static final String FIELD_PROTOCOL = "protocol";
    /**
     * version 字段。
     */
    public static final String FIELD_VERSION = "version";
    /**
     * 主体类型字段。
     */
    public static final String FIELD_SUBJECT_TYPE = "subjectType";
    /**
     * 主体标识字段。
     */
    public static final String FIELD_SUBJECT_ID = "subjectId";
    /**
     * 应用标识字段。
     */
    public static final String FIELD_APPLICATION_CODE = "applicationCode";
    /**
     * 应用准入字段。
     */
    public static final String FIELD_ADMITTED = "admitted";
    /**
     * 角色字段。
     */
    public static final String FIELD_ROLES = "roles";
    /**
     * 页面权限字段。
     */
    public static final String FIELD_PAGE_PERMISSIONS = "pagePermissions";
    /**
     * API 权限字段。
     */
    public static final String FIELD_API_PERMISSIONS = "apiPermissions";
    /**
     * 数据授权字段。
     */
    public static final String FIELD_DATA_GRANT_DOCUMENT = "dataGrantDocument";
    /**
     * 授权版本字段。
     */
    public static final String FIELD_AUTHORIZATION_VERSION = "authorizationVersion";
    /**
     * 权限清单版本字段。
     */
    public static final String FIELD_MANIFEST_VERSION = "manifestVersion";
    /**
     * 权限清单摘要字段。
     */
    public static final String FIELD_MANIFEST_DIGEST = "manifestDigest";
    /**
     * 签发时间字段。
     */
    public static final String FIELD_ISSUED_AT = "issuedAt";
    /**
     * 到期时间字段。
     */
    public static final String FIELD_EXPIRES_AT = "expiresAt";
    /**
     * 事件标识字段。
     */
    public static final String FIELD_EVENT_ID = "eventId";
    /**
     * 来源标识字段。
     */
    public static final String FIELD_SOURCE_ID = "sourceId";
    /**
     * 变更前授权版本字段。
     */
    public static final String FIELD_PREVIOUS_AUTHORIZATION_VERSION = "previousAuthorizationVersion";
    /**
     * 发生时间字段。
     */
    public static final String FIELD_OCCURRED_AT = "occurredAt";
    /**
     * 原因分类字段。
     */
    public static final String FIELD_REASON_CATEGORY = "reasonCategory";

    /**
     * 最大标识符 Unicode 码点数。
     */
    public static final int MAX_IDENTIFIER_CODE_POINT_COUNT = 128;
    /**
     * 应用标识最大 Unicode 码点数。
     */
    public static final int MAX_APPLICATION_CODE_POINT_COUNT = 64;
    /**
     * 权限清单摘要最大 Unicode 码点数。
     */
    public static final int MAX_MANIFEST_DIGEST_CODE_POINT_COUNT = 256;
    /**
     * 每类权限最大数量。
     */
    public static final int MAX_PERMISSION_COUNT = 512;
    /**
     * 结构化 Claim 最大节点数量。
     */
    public static final int MAX_CLAIM_NODE_COUNT = 16384;
    /**
     * 结构化 Claim 最大 UTF-8 文本字节数。
     */
    public static final int MAX_CLAIM_TEXT_BYTE_COUNT = 65536;

    /**
     * 属性占位符开始标记。
     */
    public static final String DYNAMIC_EXPRESSION_PROPERTY_PREFIX = "${";
    /**
     * SpEL 表达式开始标记。
     */
    public static final String DYNAMIC_EXPRESSION_SPEL_PREFIX = "#{";
    /**
     * 不允许出现在精确字面量中的模式字符。
     */
    public static final String FORBIDDEN_PATTERN_CHARACTERS = "*?[](){}^$|\\+";
    /**
     * 字段不能为空详情模板。
     */
    public static final String DETAIL_CANNOT_BE_NULL = "%s不能为null";
    /**
     * 字段不能为空或包含首尾空白详情模板。
     */
    public static final String DETAIL_CANNOT_BE_BLANK_OR_OUTER_WHITESPACE = "%s不能为空且不能包含首尾空白";
    /**
     * 字段不能包含孤立 Unicode 代理项详情模板。
     */
    public static final String DETAIL_CANNOT_CONTAIN_ISOLATED_SURROGATE = "%s不能包含孤立Unicode代理项";
    /**
     * 字段不能包含动态表达式详情模板。
     */
    public static final String DETAIL_CANNOT_CONTAIN_DYNAMIC_EXPRESSION = "%s不能包含动态表达式";
    /**
     * 字段不能包含模式字符详情模板。
     */
    public static final String DETAIL_CANNOT_CONTAIN_PATTERN_CHARACTER = "%s不能包含模式字符";
    /**
     * 字段长度超限详情模板。
     */
    public static final String DETAIL_MAXIMUM_CODE_POINT_COUNT = "%s长度不能超过%d个Unicode码点";
    /**
     * 集合不能为 null 详情模板。
     */
    public static final String DETAIL_COLLECTION_CANNOT_BE_NULL = "%s集合不能为null";
    /**
     * 集合元素不能为 null 详情模板。
     */
    public static final String DETAIL_COLLECTION_CANNOT_CONTAIN_NULL = "%s集合不能包含null";
    /**
     * 集合数量超限详情模板。
     */
    public static final String DETAIL_COLLECTION_COUNT_TOO_LARGE = "%s集合数量不能超过%d";
    /**
     * 授权上下文必须已准入详情。
     */
    public static final String DETAIL_CONTEXT_MUST_BE_ADMITTED = "授权上下文必须已通过应用准入";
    /**
     * 授权版本必须为正数详情。
     */
    public static final String DETAIL_AUTHORIZATION_VERSION_MUST_BE_POSITIVE = "授权版本必须为正数";
    /**
     * 授权时间窗无效详情。
     */
    public static final String DETAIL_INVALID_TIME_WINDOW = "签发时间必须早于到期时间";
    /**
     * 撤销前授权版本必须为正数详情。
     */
    public static final String DETAIL_PREVIOUS_AUTHORIZATION_VERSION_MUST_BE_POSITIVE = "撤销前授权版本必须为正数";
    /**
     * 判定时钟不能为空详情。
     */
    public static final String DETAIL_CLOCK_CANNOT_BE_NULL = "clock不能为null";
    /**
     * 默认时钟容差：覆盖签发端时间戳秒级取整偏差与跨机部署时钟偏差，只放宽时效下界。
     */
    public static final java.time.Duration DEFAULT_CLOCK_SKEW = java.time.Duration.ofSeconds(2L);
    /**
     * 时钟容差非法详情。
     */
    public static final String DETAIL_CLOCK_SKEW_INVALID = "clockSkew必须为非负Duration";
    /**
     * 常量类实例化提示。
     */
    public static final String MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE = "常量类不能实例化";
    /**
     * 帮助类实例化提示。
     */
    public static final String MESSAGE_HELPER_CLASS_CANNOT_INSTANTIATE = "帮助类不能实例化";

    private SimpleApplicationAuthorizationConstant() {
        throw new UnsupportedOperationException(MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
