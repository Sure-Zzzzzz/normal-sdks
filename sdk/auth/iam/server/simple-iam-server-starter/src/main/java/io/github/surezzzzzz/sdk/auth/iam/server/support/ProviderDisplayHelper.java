package io.github.surezzzzzz.sdk.auth.iam.server.support;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties.ProviderDisplayConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 外部登录方式展示文案辅助
 *
 * <p>从 simple-iam-server.external-identity.providers.{code} 读取部署方配置的展示名与描述，
 * 未配置时回退为登录方式编码本身。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class ProviderDisplayHelper {

    private final SimpleIamServerProperties properties;

    /**
     * 登录方式展示名。
     *
     * @param providerCode 登录方式编码
     * @return 配置的展示名，未配置时为编码本身
     */
    public String displayName(String providerCode) {
        ProviderDisplayConfig config = properties.getExternalIdentity()
                .getProviders().get(providerCode);
        return config != null && StringUtils.hasText(config.getName())
                ? config.getName() : providerCode;
    }

    /**
     * 登录方式描述。
     *
     * @param providerCode 登录方式编码
     * @return 配置的描述，未配置时为 null
     */
    public String displayDescription(String providerCode) {
        ProviderDisplayConfig config = properties.getExternalIdentity()
                .getProviders().get(providerCode);
        return config == null ? null : config.getDescription();
    }
}
