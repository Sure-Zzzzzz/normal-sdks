package io.github.surezzzzzz.sdk.redis.route.configuration;

import io.github.surezzzzzz.sdk.redis.route.SimpleRedisRoutePackage;
import io.github.surezzzzzz.sdk.redis.route.annotation.SimpleRedisRouteComponent;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.redis.route.constant.SimpleRedisRouteConstant;
import io.github.surezzzzzz.sdk.redis.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.redis.route.factory.DefaultRedisConnectionFactoryFactory;
import io.github.surezzzzzz.sdk.redis.route.factory.RedisConnectionFactoryFactory;
import io.github.surezzzzzz.sdk.redis.route.matcher.RedisRoutePatternMatcher;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import io.github.surezzzzzz.sdk.redis.route.resolver.DefaultRedisRouteResolver;
import io.github.surezzzzzz.sdk.redis.route.resolver.RedisRouteResolver;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import io.github.surezzzzzz.sdk.redis.route.validator.RedisRoutePropertiesValidator;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.type.MethodMetadata;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Simple Redis Route 自动配置
 *
 * @author surezzzzzz
 */
@Configuration
@AutoConfigureBefore(RedisAutoConfiguration.class)
@EnableConfigurationProperties(SimpleRedisRouteProperties.class)
@ComponentScan(
        basePackageClasses = SimpleRedisRoutePackage.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(SimpleRedisRouteComponent.class)
)
@ConditionalOnClass({RedisConnectionFactory.class, LettuceConnectionFactory.class})
@ConditionalOnProperty(prefix = SimpleRedisRouteConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
public class SimpleRedisRouteConfiguration {

    /**
     * 创建 RedisConnectionFactory 所有权校验器。
     *
     * @return RedisConnectionFactory 所有权校验器
     */
    @Bean
    public static org.springframework.beans.factory.config.BeanFactoryPostProcessor redisConnectionFactoryOwnershipValidator() {
        return SimpleRedisRouteConfiguration::validateRedisConnectionFactoryDefinitions;
    }

    private static void validateRedisConnectionFactoryDefinitions(ConfigurableListableBeanFactory beanFactory) {
        String[] beanNames = beanFactory.getBeanNamesForType(RedisConnectionFactory.class, true, false);
        for (String beanName : beanNames) {
            if (!isRouteRedisConnectionFactoryBean(beanFactory, beanName)) {
                throw new ConfigurationException(ErrorCode.REDIS_ROUTE_015,
                        String.format(ErrorMessage.REDIS_CONNECTION_FACTORY_CONFLICT, beanName));
            }
        }
    }

    private static boolean isRouteRedisConnectionFactoryBean(ConfigurableListableBeanFactory beanFactory,
                                                             String beanName) {
        if (!SimpleRedisRouteConstant.REDIS_CONNECTION_FACTORY_BEAN_NAME.equals(beanName)
                || !beanFactory.containsBeanDefinition(beanName)) {
            return false;
        }
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
        if (!(beanDefinition.getSource() instanceof MethodMetadata)) {
            return false;
        }
        MethodMetadata methodMetadata = (MethodMetadata) beanDefinition.getSource();
        return SimpleRedisRouteConfiguration.class.getName().equals(methodMetadata.getDeclaringClassName())
                && beanName.equals(methodMetadata.getMethodName());
    }

    /**
     * 创建 Redis Bean 运行期所有权校验器。
     *
     * @param registry Route 数据源注册表
     * @return Redis Bean 运行期所有权校验器
     */
    @Bean
    public BeanPostProcessor redisRouteBeanOwnershipValidator(SimpleRedisRouteRegistry registry) {
        return new RedisRouteBeanOwnershipValidator(registry);
    }

    /**
     * 创建 Route 配置校验器。
     *
     * @param patternMatcher 路由规则匹配器
     * @return Route 配置校验器
     */
    @Bean
    @ConditionalOnMissingBean(RedisRoutePropertiesValidator.class)
    public RedisRoutePropertiesValidator redisRoutePropertiesValidator(RedisRoutePatternMatcher patternMatcher) {
        return new RedisRoutePropertiesValidator(patternMatcher);
    }

    /**
     * 创建默认 Redis 连接工厂构造器。
     *
     * @return Redis 连接工厂构造器
     */
    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactoryFactory.class)
    public RedisConnectionFactoryFactory redisConnectionFactoryFactory() {
        return new DefaultRedisConnectionFactoryFactory();
    }

    /**
     * 创建并初始化 Route 数据源注册表。
     *
     * @param properties     Route 配置
     * @param validator      Route 配置校验器
     * @param factoryFactory Redis 连接工厂构造器
     * @return 已初始化的数据源注册表
     */
    @Bean
    public SimpleRedisRouteRegistry simpleRedisRouteRegistry(SimpleRedisRouteProperties properties,
                                                             RedisRoutePropertiesValidator validator,
                                                             RedisConnectionFactoryFactory factoryFactory) {
        return new SimpleRedisRouteRegistry(properties, validator, factoryFactory);
    }

    /**
     * 发布 Route default-source 的标准 RedisConnectionFactory。
     * 连接工厂生命周期由 Route 注册表统一管理，发布器不接管该实例的销毁。
     *
     * @param registry Route 注册表
     * @return default-source 连接工厂发布器
     */
    @Bean(name = SimpleRedisRouteConstant.REDIS_CONNECTION_FACTORY_BEAN_NAME)
    @Primary
    @ConditionalOnMissingBean(name = SimpleRedisRouteConstant.REDIS_CONNECTION_FACTORY_BEAN_NAME)
    public FactoryBean<RedisConnectionFactory> redisConnectionFactory(SimpleRedisRouteRegistry registry) {
        return new RedisConnectionFactoryPublisher(registry.getConnectionFactory());
    }

    /**
     * 发布 Route default-source 的标准 StringRedisTemplate。
     *
     * @param registry Route 注册表
     * @return default-source StringRedisTemplate
     */
    @Bean(name = SimpleRedisRouteConstant.STRING_REDIS_TEMPLATE_BEAN_NAME)
    @ConditionalOnMissingBean(name = SimpleRedisRouteConstant.STRING_REDIS_TEMPLATE_BEAN_NAME)
    public StringRedisTemplate stringRedisTemplate(SimpleRedisRouteRegistry registry) {
        return registry.getStringRedisTemplate();
    }

    /**
     * 发布绑定 Route default-source 的标准 RedisTemplate。
     *
     * @param registry Route 注册表
     * @return default-source RedisTemplate
     */
    @Bean(name = SimpleRedisRouteConstant.REDIS_TEMPLATE_BEAN_NAME)
    @Primary
    @ConditionalOnMissingBean(name = SimpleRedisRouteConstant.REDIS_TEMPLATE_BEAN_NAME)
    public RedisTemplate<Object, Object> redisTemplate(SimpleRedisRouteRegistry registry) {
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(registry.getConnectionFactory());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 创建默认 Route 路由解析器。
     *
     * @param properties     Route 配置
     * @param patternMatcher 路由规则匹配器
     * @return 路由解析器
     */
    @Bean
    @ConditionalOnMissingBean(RedisRouteResolver.class)
    public RedisRouteResolver redisRouteResolver(SimpleRedisRouteProperties properties,
                                                 RedisRoutePatternMatcher patternMatcher) {
        return new DefaultRedisRouteResolver(properties, patternMatcher);
    }

    /**
     * 创建显式 Route 路由门面。
     *
     * @param registry      Route 数据源注册表
     * @param routeResolver Route 路由解析器
     * @return 显式路由门面
     */
    @Bean
    @ConditionalOnMissingBean(RedisRouteTemplate.class)
    public RedisRouteTemplate redisRouteTemplate(SimpleRedisRouteRegistry registry,
                                                 RedisRouteResolver routeResolver) {
        return new RedisRouteTemplate(registry, routeResolver);
    }

    private static final class RedisConnectionFactoryPublisher implements FactoryBean<RedisConnectionFactory> {

        private final RedisConnectionFactory connectionFactory;

        private RedisConnectionFactoryPublisher(RedisConnectionFactory connectionFactory) {
            this.connectionFactory = connectionFactory;
        }

        @Override
        public RedisConnectionFactory getObject() {
            return connectionFactory;
        }

        @Override
        public Class<?> getObjectType() {
            return RedisConnectionFactory.class;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }
    }
}
