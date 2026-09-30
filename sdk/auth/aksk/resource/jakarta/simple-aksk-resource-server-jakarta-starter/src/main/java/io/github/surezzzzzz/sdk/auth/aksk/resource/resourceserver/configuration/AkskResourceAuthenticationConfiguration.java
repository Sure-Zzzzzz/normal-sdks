package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.converter.AkskIntrospectionAuthenticationConverter;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.exception.SimpleAkskResourceServerConfigurationException;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support.AkskResourceAuthenticationAdapter;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support.IntrospectLocalCacheHelper;
import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

/**
 * AKSK资源认证配置。
 *
 * @author surezzzzzz
 */
@Configuration
public class AkskResourceAuthenticationConfiguration {

    /**
     * 创建已认证的AKSK令牌内省器。
     *
     * @param properties  AKSK资源服务配置
     * @param cacheHelper AKSK本地内省缓存
     * @return AKSK令牌内省器
     */
    @Bean
    @ConditionalOnMissingBean(name = "akskOpaqueTokenIntrospector")
    public OpaqueTokenIntrospector akskOpaqueTokenIntrospector(
            SimpleAkskResourceServerProperties properties,
            IntrospectLocalCacheHelper cacheHelper) {
        SimpleAkskResourceServerProperties.Introspect introspect = properties.getIntrospect();
        boolean endpointConfigured = StringUtils.hasText(introspect.getEndpoint());
        boolean clientIdConfigured = StringUtils.hasText(introspect.getClientId());
        boolean clientSecretConfigured = StringUtils.hasText(introspect.getClientSecret());
        if (!endpointConfigured || !clientIdConfigured || !clientSecretConfigured) {
            throw new SimpleAkskResourceServerConfigurationException(
                    "AKSK introspect.endpoint、client-id和client-secret必须配置");
        }
        URI endpoint;
        try {
            endpoint = URI.create(introspect.getEndpoint());
        } catch (IllegalArgumentException exception) {
            throw new SimpleAkskResourceServerConfigurationException(
                    "AKSK introspect.endpoint必须是包含host的绝对HTTP(S) URI", exception);
        }
        String scheme = endpoint.getScheme();
        if (!endpoint.isAbsolute() || !StringUtils.hasText(endpoint.getHost())
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null
                || endpoint.getFragment() != null || endpoint.getPort() < -1 || endpoint.getPort() > 65535) {
            throw new SimpleAkskResourceServerConfigurationException(
                    "AKSK introspect.endpoint必须是包含host的绝对HTTP(S) URI");
        }
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor(
                introspect.getClientId(), introspect.getClientSecret()));
        return new AkskIntrospectionAuthenticationConverter(endpoint, restTemplate, cacheHelper);
    }

    /**
     * 注册AKSK资源认证适配器。
     *
     * @param introspector 已认证的AKSK令牌内省器
     * @return AKSK资源认证适配器
     */
    @Bean
    @ConditionalOnMissingBean(name = "akskResourceAuthenticationAdapter")
    public ResourceAuthenticationAdapter akskResourceAuthenticationAdapter(
            @Qualifier("akskOpaqueTokenIntrospector") OpaqueTokenIntrospector introspector) {
        return new AkskResourceAuthenticationAdapter(introspector);
    }
}
