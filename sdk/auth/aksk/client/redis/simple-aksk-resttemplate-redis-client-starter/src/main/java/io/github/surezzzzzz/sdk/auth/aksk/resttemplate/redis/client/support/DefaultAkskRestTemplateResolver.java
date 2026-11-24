package io.github.surezzzzzz.sdk.auth.aksk.resttemplate.redis.client.support;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 默认 AKSK RestTemplate 解析器
 *
 * @author surezzzzzz
 */
public class DefaultAkskRestTemplateResolver implements AkskRestTemplateResolver {

    private final ObjectProvider<RestTemplate> defaultTemplate;
    private final AkskProfileRestTemplateFactory profileFactory;

    public DefaultAkskRestTemplateResolver(
            @Qualifier("akskClientRestTemplate") ObjectProvider<RestTemplate> defaultTemplate,
            AkskProfileRestTemplateFactory profileFactory) {
        this.defaultTemplate = defaultTemplate;
        this.profileFactory = profileFactory;
    }

    /**
     * 按 Profile 解析 RestTemplate：空值或 default 返回默认模板，命名 Profile 返回工厂模板
     */
    @Override
    public RestTemplate resolve(String profile) {
        if (!StringUtils.hasText(profile) || SimpleAkskClientCoreConstant.DEFAULT_PROFILE.equals(profile)) {
            return defaultTemplate.getObject();
        }
        return profileFactory.create(profile);
    }
}
