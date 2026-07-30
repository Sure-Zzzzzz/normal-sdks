package io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 注解方法消费处理器
 *
 * @author surezzzzzz
 */
public class MethodKafkaConsumerHandler implements KafkaConsumerHandler<String, String> {

    private final Object bean;
    private final Method method;

    /**
     * 构造注解方法消费处理器
     *
     * @param bean   Spring bean 代理对象
     * @param method 可调用方法
     */
    public MethodKafkaConsumerHandler(Object bean, Method method) {
        this.bean = bean;
        this.method = method;
    }

    @Override
    public void handle(KafkaConsumerRecord<String, String> record) throws Exception {
        try {
            method.invoke(bean, record);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(ErrorMessage.ANNOTATED_HANDLER_INACCESSIBLE, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(ErrorMessage.ANNOTATED_HANDLER_INVOCATION_FAILED, cause);
        }
    }
}
