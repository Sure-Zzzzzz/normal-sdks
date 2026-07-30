package io.github.surezzzzzz.sdk.messaging.kafka.consumer.registrar;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation.SimpleKafkaConsumer;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation.SimpleKafkaConsumerComponent;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.SimpleKafkaConsumerConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception.KafkaConsumerConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler.MethodKafkaConsumerHandler;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.ConsumerRegistration;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.support.KafkaConsumerStringHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 扫描 {@link SimpleKafkaConsumer} 注解方法并登记消费入口。
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleKafkaConsumerAnnotationHandler implements SmartInitializingSingleton {

    private final ConfigurableListableBeanFactory beanFactory;
    private final KafkaConsumerRegistrar registrar;

    public SimpleKafkaConsumerAnnotationHandler(ConfigurableListableBeanFactory beanFactory,
                                                KafkaConsumerRegistrar registrar) {
        this.beanFactory = beanFactory;
        this.registrar = registrar;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (String beanName : beanFactory.getBeanNamesForAnnotation(SimpleKafkaConsumerComponent.class)) {
            Object bean = beanFactory.getBean(beanName);
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            if (targetClass != null) {
                collectAndRegister(beanName, bean, targetClass);
            }
        }
        log.info("SimpleKafkaConsumer 注解扫描完成，共注册 [{}] 个消费入口", registrar.getRegistrations().size());
    }

    private void collectAndRegister(String beanName, Object bean, Class<?> targetClass) {
        ReflectionUtils.doWithMethods(targetClass, method -> {
            SimpleKafkaConsumer annotation = AnnotationUtils.findAnnotation(method, SimpleKafkaConsumer.class);
            if (annotation != null) {
                registerOne(beanName, bean, annotation, method);
            }
        }, method -> AnnotationUtils.findAnnotation(method, SimpleKafkaConsumer.class) != null);
    }

    private void registerOne(String beanName, Object bean, SimpleKafkaConsumer annotation, Method targetMethod) {
        validateMethod(targetMethod);
        Method invokeMethod = resolveInvokeMethod(bean, targetMethod);
        Set<String> topics = collectTopics(annotation);
        String datasource = KafkaConsumerStringHelper.trimToNull(annotation.datasource());
        String groupId = KafkaConsumerStringHelper.trimToNull(annotation.groupId());
        String autoOffsetReset = KafkaConsumerStringHelper.trimToNull(annotation.autoOffsetReset());
        String id = resolveId(annotation, beanName, targetMethod);
        MethodKafkaConsumerHandler handler = new MethodKafkaConsumerHandler(bean, invokeMethod);
        for (String topic : topics) {
            registrar.register(ConsumerRegistration.builder()
                    .topic(topic)
                    .datasource(datasource)
                    .groupId(groupId)
                    .autoOffsetReset(autoOffsetReset)
                    .id(id)
                    .handler(handler)
                    .build());
        }
    }

    private void validateMethod(Method method) {
        if (Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())
                || method.getReturnType() != Void.TYPE || method.getParameterTypes().length != 1
                || method.getParameterTypes()[0] != KafkaConsumerRecord.class) {
            throw configInvalid(String.format(ErrorMessage.CONFIG_INVALID_HANDLER_METHOD, method.toGenericString()));
        }
    }

    private Method resolveInvokeMethod(Object bean, Method targetMethod) {
        try {
            return bean.getClass().getMethod(targetMethod.getName(), targetMethod.getParameterTypes());
        } catch (NoSuchMethodException e) {
            throw configInvalid(String.format(ErrorMessage.CONFIG_INVALID_HANDLER_METHOD,
                    targetMethod.toGenericString()));
        }
    }

    private Set<String> collectTopics(SimpleKafkaConsumer annotation) {
        String single = KafkaConsumerStringHelper.trimToNull(annotation.topic());
        String[] multiple = annotation.topics();
        if (single != null && multiple != null && multiple.length > SimpleKafkaConsumerConstant.ZERO) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_TOPIC_EMPTY);
        }
        Set<String> topics = new LinkedHashSet<>();
        if (single != null) {
            topics.add(single);
        }
        if (multiple != null) {
            for (String topic : multiple) {
                String trimmed = KafkaConsumerStringHelper.trimToNull(topic);
                if (trimmed == null) {
                    throw configInvalid(SimpleKafkaConsumerConstant.REASON_TOPIC_EMPTY);
                }
                topics.add(trimmed);
            }
        }
        if (topics.isEmpty()) {
            throw configInvalid(SimpleKafkaConsumerConstant.REASON_TOPIC_EMPTY);
        }
        return topics;
    }

    private String resolveId(SimpleKafkaConsumer annotation, String beanName, Method method) {
        String id = KafkaConsumerStringHelper.trimToNull(annotation.id());
        if (id != null) {
            return id;
        }
        return String.format("%s#%s", beanName, method.toGenericString());
    }

    private KafkaConsumerConfigurationException configInvalid(String reason) {
        return new KafkaConsumerConfigurationException(ErrorCode.CONFIG_INVALID,
                String.format(ErrorMessage.CONFIG_INVALID, reason));
    }
}
