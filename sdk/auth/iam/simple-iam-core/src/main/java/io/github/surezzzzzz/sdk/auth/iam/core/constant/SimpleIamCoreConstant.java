package io.github.surezzzzzz.sdk.auth.iam.core.constant;

/**
 * IAM核心常量。
 *
 * @author surezzzzzz
 */
public final class SimpleIamCoreConstant {

    /**
     * IAM协议名称。
     */
    public static final String PROTOCOL = "simple-iam";
    /**
     * IAM协议版本。
     */
    public static final String VERSION = "1.0";
    /**
     * IAM资源认证来源标识。
     */
    public static final String RESOURCE_AUTHENTICATION_SOURCE_ID = "iam";
    /**
     * 路由键来源与密钥标识分隔符。
     */
    public static final String ROUTE_KEY_SEPARATOR = "/";
    /**
     * IAM路由键前缀。
     */
    public static final String ROUTE_KEY_PREFIX = RESOURCE_AUTHENTICATION_SOURCE_ID + ROUTE_KEY_SEPARATOR;
    /**
     * 路由键模板。
     */
    public static final String ROUTE_KEY_TEMPLATE = "%s%s%s";
    /**
     * 密钥标识字段。
     */
    public static final String FIELD_KEY_ID = "keyId";
    /**
     * 密钥标识允许字符。
     */
    public static final String KEY_ID_ALLOWED_CHARACTER_PATTERN = "[A-Za-z0-9._-]+";
    /**
     * 密钥标识最大Unicode码点数。
     */
    public static final int MAX_KEY_ID_CODE_POINT_COUNT = 128;

    /**
     * 标准主体标识Claim。
     */
    public static final String CLAIM_SUBJECT = "sub";
    /**
     * IAM会话标识Claim。
     */
    public static final String CLAIM_SESSION_ID = "sid";
    /**
     * IAM认证时间Claim。
     */
    public static final String CLAIM_AUTHENTICATION_TIME = "auth_time";
    /**
     * IAM应用授权快照Claim。
     */
    public static final String CLAIM_APPLICATION_AUTHORIZATION = "iam_authorization";
    /**
     * 兼容角色Claim。
     */
    public static final String CLAIM_COMPATIBILITY_ROLES = "roles";
    /**
     * 兼容权限Claim。
     */
    public static final String CLAIM_COMPATIBILITY_PERMISSIONS = "permissions";

    /**
     * 字段不能为空详情模板。
     */
    public static final String DETAIL_CANNOT_BE_NULL = "%s不能为null";
    /**
     * 字段不能为空或包含首尾空白详情模板。
     */
    public static final String DETAIL_CANNOT_BE_BLANK_OR_OUTER_WHITESPACE = "%s不能为空且不能包含首尾空白";
    /**
     * 密钥标识格式无效详情模板。
     */
    public static final String DETAIL_KEY_ID_INVALID = "keyId格式无效：%s";
    /**
     * 常量类实例化提示。
     */
    public static final String MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE = "常量类不能实例化";
    /**
     * 帮助类实例化提示。
     */
    public static final String MESSAGE_HELPER_CLASS_CANNOT_INSTANTIATE = "帮助类不能实例化";

    private SimpleIamCoreConstant() {
        throw new UnsupportedOperationException(MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
