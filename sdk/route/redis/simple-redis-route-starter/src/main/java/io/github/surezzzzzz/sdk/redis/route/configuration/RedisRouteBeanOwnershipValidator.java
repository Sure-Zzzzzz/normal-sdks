package io.github.surezzzzzz.sdk.redis.route.configuration;

import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.redis.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Redis Route Bean 所有权校验器
 *
 * @author surezzzzzz
 */
final class RedisRouteBeanOwnershipValidator implements BeanPostProcessor {

    private final RedisConnectionFactory routeConnectionFactory;

    RedisRouteBeanOwnershipValidator(SimpleRedisRouteRegistry registry) {
        this.routeConnectionFactory = registry.getConnectionFactory();
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        return validate(bean, beanName);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        return validate(bean, beanName);
    }

    private Object validate(Object bean, String beanName) {
        if (bean instanceof RedisConnectionFactory && bean != routeConnectionFactory) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_015,
                    String.format(ErrorMessage.REDIS_CONNECTION_FACTORY_CONFLICT, beanName));
        }
        if (bean instanceof RedisTemplate
                && ((RedisTemplate<?, ?>) bean).getConnectionFactory() != routeConnectionFactory) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_016,
                    String.format(ErrorMessage.REDIS_TEMPLATE_CONNECTION_FACTORY_MISMATCH, beanName));
        }
        return bean;
    }
}
