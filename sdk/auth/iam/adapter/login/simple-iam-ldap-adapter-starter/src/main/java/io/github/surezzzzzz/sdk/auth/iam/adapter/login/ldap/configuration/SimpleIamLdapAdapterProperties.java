package io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.configuration;

import io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.constant.SimpleIamLdapAdapterConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple IAM LDAP Adapter 配置
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = SimpleIamLdapAdapterConstant.CONFIG_PREFIX)
public class SimpleIamLdapAdapterProperties {

    /**
     * LDAP 服务地址（含根 DN），如 ldap://localhost:1389/dc=example,dc=org
     */
    private String url;

    /**
     * 用于搜索用户的服务账号 DN
     */
    private String managerDn;

    /**
     * 用于搜索用户的服务账号密码
     */
    private String managerPassword;

    /**
     * 用户搜索基准 DN（相对 url 中的根 DN）
     */
    private String userSearchBase = "";

    /**
     * 用户搜索过滤器；OpenLDAP 默认 (uid={0})，Windows AD 建议 (sAMAccountName={0})
     */
    private String userSearchFilter = SimpleIamLdapAdapterConstant.DEFAULT_USER_SEARCH_FILTER;

    /**
     * 建议用户名属性（如 uid、sAMAccountName）
     */
    private String usernameAttribute = SimpleIamLdapAdapterConstant.DEFAULT_USERNAME_ATTRIBUTE;

    /**
     * 显示名属性（如 cn）
     */
    private String displayNameAttribute = SimpleIamLdapAdapterConstant.DEFAULT_DISPLAY_NAME_ATTRIBUTE;

    /**
     * 邮箱属性（如 mail）
     */
    private String emailAttribute = SimpleIamLdapAdapterConstant.DEFAULT_EMAIL_ATTRIBUTE;

    /**
     * 外部稳定 ID 属性；为空时使用用户条目 DN 作为 externalId
     */
    private String externalIdAttribute;
}
