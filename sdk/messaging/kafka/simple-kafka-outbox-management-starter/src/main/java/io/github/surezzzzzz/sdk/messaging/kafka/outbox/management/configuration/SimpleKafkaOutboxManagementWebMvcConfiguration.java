package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.configuration;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant.SimpleKafkaOutboxManagementConstant;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Management 页面资源配置。
 *
 * @author surezzzzzz
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = SimpleKafkaOutboxManagementConstant.CONFIG_PREFIX,
        name = SimpleKafkaOutboxManagementConstant.CONFIG_PROPERTY_UI_ENABLE, havingValue = "true", matchIfMissing = true)
public class SimpleKafkaOutboxManagementWebMvcConfiguration implements WebMvcConfigurer {
    private final SimpleKafkaOutboxManagementProperties properties;

    /**
     * 注册 Management 本地静态资源。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(properties.getUi().getBasePath() + SimpleKafkaOutboxManagementConstant.PATH_ASSETS_WILDCARD)
                .addResourceLocations("classpath:/static/outbox-management/assets/");
    }
}
