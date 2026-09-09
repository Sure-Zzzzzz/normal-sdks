package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalBrowserLoginProvider;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalCredentialAuthenticator;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.ConfigurationException;
import lombok.Getter;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 外部身份源登录方式注册表
 *
 * <p>聚合容器中已装配的两类 SPI 实现；同一登录方式编码被两个实现注册属于启动配置错误，
 * 不按 Bean 顺序隐式选择。
 *
 * @author surezzzzzz
 */
public final class ExternalProviderRegistry {

    @Getter
    private final Map<String, ExternalCredentialAuthenticator> credentialAuthenticators;

    @Getter
    private final Map<String, ExternalBrowserLoginProvider> browserLoginProviders;

    private ExternalProviderRegistry(Map<String, ExternalCredentialAuthenticator> credentialAuthenticators,
                                     Map<String, ExternalBrowserLoginProvider> browserLoginProviders) {
        this.credentialAuthenticators = Collections.unmodifiableMap(credentialAuthenticators);
        this.browserLoginProviders = Collections.unmodifiableMap(browserLoginProviders);
    }

    /**
     * 从已注册 SPI 实例创建注册表，编码冲突时快速失败。
     *
     * @param credentialAuthenticators 已注册的凭证校验型认证器
     * @param browserLoginProviders    已注册的跳转型登录提供方
     * @return 外部登录方式注册表
     */
    public static ExternalProviderRegistry create(
            Collection<ExternalCredentialAuthenticator> credentialAuthenticators,
            Collection<ExternalBrowserLoginProvider> browserLoginProviders) {
        Map<String, ExternalCredentialAuthenticator> byCode = index(credentialAuthenticators, "凭证校验型");
        Map<String, ExternalBrowserLoginProvider> byBrowserCode = index(browserLoginProviders, "跳转型");
        for (String code : byBrowserCode.keySet()) {
            if (byCode.containsKey(code)) {
                throw new ConfigurationException("外部登录方式编码冲突（同时注册为凭证校验型与跳转型）：" + code);
            }
        }
        return new ExternalProviderRegistry(byCode, byBrowserCode);
    }

    private static <T> Map<String, T> index(Collection<T> providers, String kind) {
        Map<String, T> indexed = new LinkedHashMap<>();
        for (T provider : providers == null ? Collections.<T>emptyList() : providers) {
            String code = providerCode(provider);
            T previous = indexed.put(code, provider);
            if (previous != null) {
                throw new ConfigurationException("外部登录方式编码冲突（" + kind + "）：" + code);
            }
        }
        return indexed;
    }

    private static String providerCode(Object provider) {
        String code = provider instanceof ExternalCredentialAuthenticator
                ? ((ExternalCredentialAuthenticator) provider).providerCode()
                : ((ExternalBrowserLoginProvider) provider).providerCode();
        if (code == null || code.trim().isEmpty()) {
            throw new ConfigurationException("外部登录方式编码不能为空：" + provider.getClass().getName());
        }
        return code.trim();
    }

    /**
     * 查询凭证校验型认证器。
     *
     * @param providerCode 登录方式编码
     * @return 对应认证器
     */
    public ExternalCredentialAuthenticator getCredentialAuthenticator(String providerCode) {
        return credentialAuthenticators.get(providerCode);
    }

    /**
     * 查询跳转型登录提供方。
     *
     * @param providerCode 登录方式编码
     * @return 对应登录提供方
     */
    public ExternalBrowserLoginProvider getBrowserLoginProvider(String providerCode) {
        return browserLoginProviders.get(providerCode);
    }

    /**
     * 判断登录方式编码是否已装配（任意类型）。
     *
     * @param providerCode 登录方式编码
     * @return 是否已装配
     */
    public boolean contains(String providerCode) {
        return credentialAuthenticators.containsKey(providerCode)
                || browserLoginProviders.containsKey(providerCode);
    }
}
