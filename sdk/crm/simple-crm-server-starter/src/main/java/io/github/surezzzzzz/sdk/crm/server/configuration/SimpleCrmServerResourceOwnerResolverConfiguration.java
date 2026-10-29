package io.github.surezzzzzz.sdk.crm.server.configuration;

import io.github.surezzzzzz.sdk.crm.server.constant.SimpleCrmServerConstant;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerOwnerResolverBridge;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerResourceOwnerResolverBridge;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resource Server 协作形态的可选 owner 桥。
 *
 * <p>必须同时存在 Resource core 和认证过滤器：DATA MVC 可能单独传递 Resource core，
 * 但那仍是散形态，不能因此抢走普通 owner 桥。</p>
 *
 * @author surezzzzzz
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(SimpleCrmServerAutoConfiguration.class)
@ConditionalOnProperty(prefix = SimpleCrmServerConstant.CONFIG_PREFIX,
        name = SimpleCrmServerConstant.CONFIG_PROPERTY_ENABLE,
        havingValue = SimpleCrmServerConstant.BOOLEAN_TRUE)
@ConditionalOnClass(name = {
        "io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext",
        "io.github.surezzzzzz.sdk.auth.resource.server.filter.ResourceAuthenticationFilter"
})
public class SimpleCrmServerResourceOwnerResolverConfiguration {

    @Bean("crmOwnerResolverBridge")
    @ConditionalOnMissingBean(SimpleCrmServerOwnerResolverBridge.class)
    public SimpleCrmServerOwnerResolverBridge crmResourceOwnerResolverBridge() {
        return new SimpleCrmServerResourceOwnerResolverBridge();
    }
}
