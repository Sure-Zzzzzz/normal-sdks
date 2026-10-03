package io.github.surezzzzzz.sdk.kafka.route.support;

import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

import java.io.Closeable;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kafka ProducerFactory 兼容 Helper
 *
 * @author surezzzzzz
 */
@Slf4j
public final class KafkaProducerFactoryCompatibilityHelper {

    private KafkaProducerFactoryCompatibilityHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 应用 transaction id prefix
     *
     * @param factory producer factory
     * @param prefix  transaction id prefix
     */
    public static void applyTransactionIdPrefix(Object factory, String prefix) {
        if (factory == null || !KafkaRouteStringHelper.hasText(prefix)) {
            return;
        }
        if (factory instanceof DefaultKafkaProducerFactory) {
            ((DefaultKafkaProducerFactory<?, ?>) factory).setTransactionIdPrefix(prefix.trim());
            return;
        }
        KafkaReflectionHelper.invokeIfPresent(factory, SimpleKafkaRouteConstant.REFLECT_METHOD_SET_TRANSACTION_ID_PREFIX, prefix.trim());
    }

    /**
     * 获取 producer factory 配置
     *
     * @param factory producer factory
     * @return 配置快照
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getConfigurationProperties(Object factory) {
        if (factory == null) {
            return Collections.emptyMap();
        }
        Object result = KafkaReflectionHelper.invokeIfPresent(factory, SimpleKafkaRouteConstant.REFLECT_METHOD_GET_CONFIGURATION_PROPERTIES);
        if (!(result instanceof Map)) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>((Map<String, Object>) result));
    }

    /**
     * 安全销毁 producer factory
     *
     * @param factory producer factory
     */
    public static void destroyProducerFactory(Object factory) {
        destroyObject(factory, "Kafka producer factory");
    }

    private static void destroyObject(Object target, String name) {
        if (target == null) {
            return;
        }
        if (target instanceof DisposableBean) {
            try {
                ((DisposableBean) target).destroy();
                return;
            } catch (Exception e) {
                log.warn("{} destroy 调用失败", name, e);
                return;
            }
        }
        if (target instanceof Closeable) {
            try {
                ((Closeable) target).close();
                return;
            } catch (Exception e) {
                log.warn("{} close 调用失败", name, e);
                return;
            }
        }
        Method destroyMethod = KafkaReflectionHelper.findMethod(target.getClass(), SimpleKafkaRouteConstant.REFLECT_METHOD_DESTROY);
        if (destroyMethod != null) {
            try {
                KafkaReflectionHelper.invoke(destroyMethod, target);
            } catch (RuntimeException e) {
                log.warn("{} 反射 destroy 调用失败", name, e);
            }
        }
    }
}
