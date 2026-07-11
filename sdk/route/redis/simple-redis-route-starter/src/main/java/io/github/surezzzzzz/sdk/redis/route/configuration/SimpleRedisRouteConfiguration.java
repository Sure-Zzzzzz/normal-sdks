package io.github.surezzzzzz.sdk.redis.route.configuration;

import io.github.surezzzzzz.sdk.redis.route.SimpleRedisRoutePackage;
import io.github.surezzzzzz.sdk.redis.route.annotation.SimpleRedisRouteComponent;
import io.github.surezzzzzz.sdk.redis.route.constant.SimpleRedisRouteConstant;
import io.github.surezzzzzz.sdk.redis.route.factory.DefaultRedisConnectionFactoryFactory;
import io.github.surezzzzzz.sdk.redis.route.factory.RedisConnectionFactoryFactory;
import io.github.surezzzzzz.sdk.redis.route.matcher.RedisRoutePatternMatcher;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import io.github.surezzzzzz.sdk.redis.route.resolver.DefaultRedisRouteResolver;
import io.github.surezzzzzz.sdk.redis.route.resolver.RedisRouteResolver;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import io.github.surezzzzzz.sdk.redis.route.validator.RedisRoutePropertiesValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Simple Redis Route 自动配置
 *
 * @author surezzzzzz
 */
@Configuration
@EnableConfigurationProperties(SimpleRedisRouteProperties.class)
@ComponentScan(
        basePackageClasses = SimpleRedisRoutePackage.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SimpleRedisRouteComponent.class)
)
@ConditionalOnClass({RedisConnectionFactory.class, LettuceConnectionFactory.class})
@ConditionalOnProperty(prefix = SimpleRedisRouteConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
public class SimpleRedisRouteConfiguration {

    @Bean
    @ConditionalOnMissingBean(RedisRoutePropertiesValidator.class)
    public RedisRoutePropertiesValidator redisRoutePropertiesValidator(RedisRoutePatternMatcher patternMatcher) {
        return new RedisRoutePropertiesValidator(patternMatcher);
    }

    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactoryFactory.class)
    public RedisConnectionFactoryFactory redisConnectionFactoryFactory() {
        return new DefaultRedisConnectionFactoryFactory();
    }

    @Bean
    public SimpleRedisRouteRegistry simpleRedisRouteRegistry(SimpleRedisRouteProperties properties,
                                                             RedisRoutePropertiesValidator validator,
                                                             RedisConnectionFactoryFactory factoryFactory) {
        return new SimpleRedisRouteRegistry(properties, validator, factoryFactory);
    }

    @Bean
    @ConditionalOnMissingBean(RedisRouteResolver.class)
    public RedisRouteResolver redisRouteResolver(SimpleRedisRouteProperties properties,
                                                 RedisRoutePatternMatcher patternMatcher) {
        return new DefaultRedisRouteResolver(properties, patternMatcher);
    }

    @Bean
    @ConditionalOnMissingBean(RedisRouteTemplate.class)
    public RedisRouteTemplate redisRouteTemplate(SimpleRedisRouteRegistry registry,
                                                 RedisRouteResolver routeResolver) {
        return new RedisRouteTemplate(registry, routeResolver);
    }
}
