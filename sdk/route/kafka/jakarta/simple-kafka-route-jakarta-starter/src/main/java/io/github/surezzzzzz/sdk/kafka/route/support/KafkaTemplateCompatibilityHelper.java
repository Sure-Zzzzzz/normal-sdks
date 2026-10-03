package io.github.surezzzzzz.sdk.kafka.route.support;

import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * KafkaTemplate 兼容 Helper
 *
 * @author surezzzzzz
 */
public final class KafkaTemplateCompatibilityHelper {

    private KafkaTemplateCompatibilityHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 判断 KafkaTemplate 是否启用事务
     *
     * @param template KafkaTemplate
     * @return true 表示启用事务
     */
    public static boolean isTransactional(Object template) {
        if (template instanceof KafkaTemplate) {
            return ((KafkaTemplate<?, ?>) template).isTransactional();
        }
        Object result = KafkaReflectionHelper.invokeIfPresent(template, SimpleKafkaRouteConstant.REFLECT_METHOD_IS_TRANSACTIONAL);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 获取 KafkaTemplate 对应的 transaction id prefix
     *
     * @param template KafkaTemplate
     * @return transaction id prefix，不存在返回 null
     */
    public static String getTransactionIdPrefix(Object template) {
        if (!(template instanceof KafkaTemplate)) {
            return null;
        }
        Object templatePrefix = KafkaReflectionHelper.invokeIfPresent(template, SimpleKafkaRouteConstant.REFLECT_METHOD_GET_TRANSACTION_ID_PREFIX);
        if (templatePrefix != null) {
            return String.valueOf(templatePrefix);
        }
        ProducerFactory<?, ?> producerFactory = ((KafkaTemplate<?, ?>) template).getProducerFactory();
        Object prefix = KafkaReflectionHelper.invokeIfPresent(producerFactory, SimpleKafkaRouteConstant.REFLECT_METHOD_GET_TRANSACTION_ID_PREFIX);
        return prefix == null ? null : String.valueOf(prefix);
    }
}
