package io.github.surezzzzzz.sdk.auth.resource.server.constant;

/**
 * 资源服务Starter常量。
 *
 * @author surezzzzzz
 */
public final class SimpleResourceServerStarterConstant {

    /**
     * 配置前缀。
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.resource.server";
    /**
     * 默认启用状态。
     */
    public static final boolean DEFAULT_ENABLED = true;
    /**
     * context-path感知默认值。
     */
    public static final boolean DEFAULT_CONTEXT_PATH_AWARE = true;
    /**
     * Authorization请求头名称。
     */
    public static final String HEADER_AUTHORIZATION = "Authorization";
    /**
     * Bearer方案。
     */
    public static final String AUTHORIZATION_SCHEME_BEARER = "Bearer";
    /**
     * Cookie请求头名称。
     */
    public static final String HEADER_COOKIE = "Cookie";
    /**
     * Header与凭据分隔空白。
     */
    public static final String HEADER_VALUE_SEPARATOR = " ";
    /**
     * Servlet context-path配置键。
     */
    public static final String PROPERTY_SERVER_SERVLET_CONTEXT_PATH = "server.servlet.context-path";
    /**
     * 任意路径Ant模式。
     */
    public static final String ANT_PATTERN_ALL = "/**";
    /**
     * URL路径分隔符。
     */
    public static final String URL_PATH_SEPARATOR = "/";
    /**
     * URL片段分隔符。
     */
    public static final String URL_FRAGMENT_SEPARATOR = "#";
    /**
     * Ant单字符通配符。
     */
    public static final String ANT_SINGLE_CHARACTER_WILDCARD = "?";
    /**
     * Ant多字符通配符。
     */
    public static final String ANT_MULTIPLE_CHARACTER_WILDCARD = "*";
    /**
     * 空文本。
     */
    public static final String EMPTY = "";
    /**
     * 自动配置类名。
     */
    public static final String AUTO_CONFIGURATION_CLASS_NAME = "io.github.surezzzzzz.sdk.auth.resource.server.configuration.ResourceServerAutoConfiguration";
    /**
     * 配置错误码。
     */
    public static final String ERROR_CODE_CONFIGURATION = "CONFIG_001";
    /**
     * 未配置认证适配器提示。
     */
    public static final String ERROR_MISSING_AUTHENTICATION_ADAPTER = "配置受保护路径时必须注册至少一个资源认证适配器";
    /**
     * 路径不得为空提示。
     */
    public static final String ERROR_SECURITY_PATH_EMPTY = "资源安全路径不能为空";
    /**
     * 未启用上下文路径归一化时不得包含上下文路径提示。
     */
    public static final String ERROR_SECURITY_PATH_CONTAINS_CONTEXT_PATH =
            "未启用context-path归一化时资源安全路径不能包含Servlet context-path：%s";
    /**
     * 路径不得包含片段提示。
     */
    public static final String ERROR_SECURITY_PATH_CONTAINS_FRAGMENT = "资源安全路径不能包含片段：%s";
    /**
     * 公开与受保护路径冲突提示。
     */
    public static final String ERROR_SECURITY_PATH_CONFLICT = "公开路径与受保护路径存在歧义交集：%s 与 %s";
    /**
     * 缺少受保护路径提示。
     */
    public static final String ERROR_MISSING_PROTECTED_PATH = "配置API权限规则时必须声明至少一个受保护路径";
    /**
     * API权限规则不能为空提示。
     */
    public static final String ERROR_API_PERMISSION_RULE_NULL = "API权限规则不能为null";
    /**
     * API权限规则字段不能为空提示。
     */
    public static final String ERROR_API_PERMISSION_RULE_FIELD_EMPTY = "API权限规则字段不能为空：%s";
    /**
     * API权限规则HTTP方法非法提示。
     */
    public static final String ERROR_API_PERMISSION_RULE_METHOD_INVALID = "API权限规则HTTP方法非法：%s";
    /**
     * API权限规则权限非法提示。
     */
    public static final String ERROR_API_PERMISSION_RULE_PERMISSION_INVALID = "API权限规则权限必须是精确非空文本：%s";
    /**
     * API权限规则未覆盖受保护路径提示。
     */
    public static final String ERROR_API_PERMISSION_RULE_OUTSIDE_PROTECTED_PATH =
            "API权限规则必须覆盖至少一个受保护路径：%s";
    /**
     * API权限规则与公开路径冲突提示。
     */
    public static final String ERROR_API_PERMISSION_RULE_CONFLICT_PERMIT_ALL_PATH =
            "API权限规则不能与公开路径交叠：%s 与 %s";
    /**
     * API权限规则路径歧义提示。
     */
    public static final String ERROR_API_PERMISSION_RULE_PATH_CONFLICT =
            "同一HTTP方法的API权限规则存在歧义交集：%s %s 与 %s";
    /**
     * 认证适配器集合为空提示。
     */
    public static final String ERROR_AUTHENTICATION_ADAPTER_COLLECTION_NULL = "认证适配器集合不能为null";
    /**
     * 认证适配器为空提示。
     */
    public static final String ERROR_AUTHENTICATION_ADAPTER_NULL = "认证适配器不能为null";
    /**
     * 认证适配器来源为空提示。
     */
    public static final String ERROR_AUTHENTICATION_ADAPTER_SOURCE_NULL = "认证适配器来源不能为null";
    /**
     * 认证来源重复提示。
     */
    public static final String ERROR_DUPLICATE_AUTHENTICATION_SOURCE = "认证来源重复：%s";
    /**
     * JOSE段分隔符。
     */
    public static final String JOSE_SEGMENT_SEPARATOR = ".";
    /**
     * JOSE kid字段。
     */
    public static final String JOSE_HEADER_FIELD_KID = "kid";
    /**
     * 正则表达式转义后的JOSE段分隔符。
     */
    public static final String JOSE_SEGMENT_SEPARATOR_REGEX = "\\.";
    /**
     * kid来源分隔符。
     */
    public static final String KID_SOURCE_SEPARATOR = "/";
    /**
     * JOSE header最大字节数。
     */
    public static final int MAX_JOSE_HEADER_BYTE_COUNT = 1024;
    /**
     * Base64URL编码的JOSE header最大字符数。
     */
    public static final int MAX_JOSE_HEADER_ENCODED_CHARACTER_COUNT = 1368;
    /**
     * JOSE kid最大字符数。
     */
    public static final int MAX_JOSE_KID_LENGTH = 256;
    /**
     * HTTP未认证状态。
     */
    public static final int HTTP_STATUS_UNAUTHORIZED = 401;
    /**
     * HTTP禁止状态。
     */
    public static final int HTTP_STATUS_FORBIDDEN = 403;
    /**
     * 缺少认证安全消息。
     */
    public static final String MESSAGE_UNAUTHORIZED = "未认证";
    /**
     * 缺少权限安全消息。
     */
    public static final String MESSAGE_FORBIDDEN = "无访问权限";
    /**
     * 匿名主体标识。
     */
    public static final String ANONYMOUS_PRINCIPAL = "anonymous";
    /**
     * 常量类实例化提示。
     */
    public static final String MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE = "常量类不能实例化";

    private SimpleResourceServerStarterConstant() {
        throw new UnsupportedOperationException(MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
