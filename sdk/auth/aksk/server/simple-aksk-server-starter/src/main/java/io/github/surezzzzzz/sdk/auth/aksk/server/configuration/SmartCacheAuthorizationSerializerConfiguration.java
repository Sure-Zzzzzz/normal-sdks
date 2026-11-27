package io.github.surezzzzzz.sdk.auth.aksk.server.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheConfiguration;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.serializer.JacksonSmartCacheSerializer;
import io.github.surezzzzzz.sdk.cache.serializer.PackageSmartCacheTypeValidator;
import io.github.surezzzzzz.sdk.cache.serializer.SmartCacheSerializer;
import io.github.surezzzzzz.sdk.cache.serializer.SmartCacheTypeValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AKSK 授权对象缓存的序列化配置；必须在 SmartCache 自动配置前登记组合序列化器。
 *
 * <p>OAuth2Authorization 走专用授权载荷；非授权缓存继续使用 SmartCache 默认
 * JSON 载荷，避免全局替换序列化器破坏其他既有缓存格式。</p>
 *
 * @author surezzzzzz
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(SmartCacheConfiguration.class)
public class SmartCacheAuthorizationSerializerConfiguration {

    /**
     * SmartCache 默认 Mapper 的标准 Bean 名称。
     */
    private static final String SMART_CACHE_OBJECT_MAPPER_BEAN_NAME = "smartCacheObjectMapper";

    /**
     * 注册 SmartCache 组合序列化器。
     *
     * <p>不暴露 ObjectMapper Bean，避免改变宿主 MVC 默认 JSON 行为。</p>
     *
     * @param properties               SmartCache 配置
     * @param smartCacheMapperProvider 支持宿主覆盖 SmartCache Jackson Mapper
     * @param validatorProvider        支持宿主覆盖 L2 JSON 类型白名单
     * @return 支持授权对象完整回读、且不影响其他缓存格式的序列化器
     */
    @Bean
    @ConditionalOnMissingBean(SmartCacheSerializer.class)
    public SmartCacheSerializer smartCacheAuthorizationSerializer(
            SmartCacheProperties properties,
            @Qualifier(SMART_CACHE_OBJECT_MAPPER_BEAN_NAME)
            ObjectProvider<ObjectMapper> smartCacheMapperProvider,
            ObjectProvider<SmartCacheTypeValidator> validatorProvider) {
        ObjectMapper objectMapper = smartCacheMapperProvider.getIfAvailable(
                SmartCacheAuthorizationSerializerConfiguration::createDefaultObjectMapper);
        SmartCacheTypeValidator validator = validatorProvider.getIfAvailable(
                () -> new PackageSmartCacheTypeValidator(properties.getSerializer().getTrustedPackages()));
        return new AuthorizationSmartCacheSerializer(
                new JacksonSmartCacheSerializer(objectMapper, validator));
    }

    /**
     * SmartCache 自动配置关闭时提供兜底 Mapper；开启时优先复用既有 Bean。
     */
    private static ObjectMapper createDefaultObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
