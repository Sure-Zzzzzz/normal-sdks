package io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.constant;

/**
 * Simple IAM LDAP Adapter 常量
 *
 * @author surezzzzzz
 */
public final class SimpleIamLdapAdapterConstant {

    /**
     * 配置前缀（包名层级，与仓库其他 starter 一致）
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap";

    /**
     * 登录方式编码（与 IAM Server /providers 契约一致）
     */
    public static final String PROVIDER_CODE = "ldap-password";

    /**
     * 默认用户搜索过滤器（OpenLDAP 生态标准 uid；对接 Windows AD 时应改用 (sAMAccountName={0})）
     */
    public static final String DEFAULT_USER_SEARCH_FILTER = "(uid={0})";

    /**
     * 默认建议用户名属性
     */
    public static final String DEFAULT_USERNAME_ATTRIBUTE = "uid";

    /**
     * 默认显示名属性
     */
    public static final String DEFAULT_DISPLAY_NAME_ATTRIBUTE = "cn";

    /**
     * 默认邮箱属性
     */
    public static final String DEFAULT_EMAIL_ATTRIBUTE = "mail";

    /**
     * 错误码：已引入但配置不完整
     */
    public static final String ERROR_CONFIG_INCOMPLETE = "LDAP_ADAPTER_001";

    /**
     * 错误码：ContextSource 初始化失败（地址非法 / 连接工厂构建失败）
     */
    public static final String ERROR_CONTEXT_SOURCE_INIT = "LDAP_ADAPTER_002";

    /**
     * 模板：已引入但配置不完整
     * 参数: 配置前缀 CONFIG_PREFIX
     */
    public static final String TEMPLATE_CONFIG_INCOMPLETE = "%s 已引入但配置不完整："
            + "url 为必填（base DN 直接写在 URL 路径中）";

    private SimpleIamLdapAdapterConstant() {
        throw new UnsupportedOperationException("常量类不允许实例化");
    }
}
