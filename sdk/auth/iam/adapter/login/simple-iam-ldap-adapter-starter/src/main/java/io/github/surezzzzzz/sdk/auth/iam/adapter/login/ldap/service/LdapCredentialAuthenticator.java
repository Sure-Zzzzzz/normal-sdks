package io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.service;

import io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.annotation.SimpleIamLdapAdapterComponent;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.configuration.SimpleIamLdapAdapterProperties;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.constant.SimpleIamLdapAdapterConstant;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.ldap.exception.SimpleIamLdapAdapterException;
import io.github.surezzzzzz.sdk.auth.iam.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.core.exception.IamProtocolException;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalCredentialAuthenticator;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalIdentity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LDAP bind 凭证校验认证器
 *
 * <p>搜索用户条目后以用户 DN + 凭证执行 bind 验证；凭据错误抛
 * {@link ErrorCode#EXTERNAL_BAD_CREDENTIALS}，目录服务不可达抛
 * {@link ErrorCode#EXTERNAL_PROVIDER_UNAVAILABLE}，不将可用性问题伪装为凭据错误。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamLdapAdapterComponent
public class LdapCredentialAuthenticator implements ExternalCredentialAuthenticator {

    private final BindAuthenticator bindAuthenticator;
    private final SimpleIamLdapAdapterProperties properties;

    /**
     * 由适配器精准扫描装配；ContextSource / BindAuthenticator 的第三方组装
     * 在构造内完成（原 AutoConfiguration @Bean 组装逻辑平移）。
     *
     * @param properties LDAP 适配器配置
     */
    public LdapCredentialAuthenticator(SimpleIamLdapAdapterProperties properties) {
        validate(properties);
        this.properties = properties;
        DefaultSpringSecurityContextSource contextSource =
                new DefaultSpringSecurityContextSource(properties.getUrl());
        contextSource.setUserDn(properties.getManagerDn());
        contextSource.setPassword(properties.getManagerPassword());
        try {
            contextSource.afterPropertiesSet();
        } catch (Exception exception) {
            throw new SimpleIamLdapAdapterException(
                    SimpleIamLdapAdapterConstant.ERROR_CONTEXT_SOURCE_INIT,
                    "LDAP ContextSource 初始化失败：" + properties.getUrl(), exception);
        }
        this.bindAuthenticator = new BindAuthenticator(contextSource);
        this.bindAuthenticator.setUserSearch(new FilterBasedLdapUserSearch(
                properties.getUserSearchBase(), properties.getUserSearchFilter(), contextSource));
        log.info("LDAP 登录适配器就绪：url={}, managerBind={}, userSearchBase={}",
                properties.getUrl(),
                StringUtils.hasText(properties.getManagerDn()) ? "是" : "匿名",
                properties.getUserSearchBase());
    }

    /**
     * 登录方式编码（ldap-password）
     */
    @Override
    public String providerCode() {
        return SimpleIamLdapAdapterConstant.PROVIDER_CODE;
    }

    private void validate(SimpleIamLdapAdapterProperties properties) {
        if (!StringUtils.hasText(properties.getUrl())) {
            throw new SimpleIamLdapAdapterException(
                    SimpleIamLdapAdapterConstant.ERROR_CONFIG_INCOMPLETE,
                    String.format(SimpleIamLdapAdapterConstant.TEMPLATE_CONFIG_INCOMPLETE,
                            SimpleIamLdapAdapterConstant.CONFIG_PREFIX));
        }
    }

    /**
     * 以服务账号搜索用户条目后用用户 DN + 密码 bind 验证，装载外部身份属性
     */
    @Override
    public ExternalIdentity authenticate(String username, String credential) {
        DirContextOperations user;
        try {
            user = bindAuthenticator.authenticate(
                    new UsernamePasswordAuthenticationToken(username, credential));
        } catch (UsernameNotFoundException | BadCredentialsException exception) {
            throw new IamProtocolException(ErrorCode.EXTERNAL_BAD_CREDENTIALS,
                    ErrorMessage.EXTERNAL_BAD_CREDENTIALS);
        } catch (InternalAuthenticationServiceException
                 | org.springframework.ldap.CommunicationException
                 | org.springframework.ldap.ServiceUnavailableException exception) {
            log.warn("LDAP 目录服务不可用", exception);
            throw new IamProtocolException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE,
                    ErrorMessage.EXTERNAL_PROVIDER_UNAVAILABLE);
        }
        log.debug("LDAP bind 认证成功：username={}", username);
        return toIdentity(username, user);
    }

    private ExternalIdentity toIdentity(String loginUsername, DirContextOperations user) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        javax.naming.directory.Attributes rawAttributes = user.getAttributes();
        if (rawAttributes != null) {
            javax.naming.NamingEnumeration<? extends javax.naming.directory.Attribute> enumeration =
                    rawAttributes.getAll();
            try {
                while (enumeration.hasMoreElements()) {
                    javax.naming.directory.Attribute attribute = enumeration.nextElement();
                    Object value = attribute.get();
                    // LDAP 属性值可能是 byte[]（userPassword、GUID 等）：只透传字符串值，
                    // 二进制属性既无法强转（ClassCastException）也不应进入身份属性（凭据泄漏）
                    if (value instanceof String) {
                        attributes.put(attribute.getID(), value);
                    }
                }
            } catch (javax.naming.NamingException exception) {
                log.warn("LDAP 属性读取失败", exception);
                throw new IamProtocolException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE,
                        ErrorMessage.EXTERNAL_PROVIDER_UNAVAILABLE);
            }
        }

        String externalId = StringUtils.hasText(properties.getExternalIdAttribute())
                ? user.getStringAttribute(properties.getExternalIdAttribute())
                : null;
        if (!StringUtils.hasText(externalId)) {
            externalId = user.getDn().toString();
        }

        String usernameSuggestion = StringUtils.hasText(properties.getUsernameAttribute())
                ? user.getStringAttribute(properties.getUsernameAttribute())
                : null;
        if (!StringUtils.hasText(usernameSuggestion)) {
            usernameSuggestion = loginUsername;
        }

        return ExternalIdentity.builder()
                .providerCode(providerCode())
                .externalId(externalId)
                .usernameSuggestion(usernameSuggestion)
                .displayName(user.getStringAttribute(properties.getDisplayNameAttribute()))
                .email(user.getStringAttribute(properties.getEmailAttribute()))
                .attributes(attributes)
                .build();
    }
}
