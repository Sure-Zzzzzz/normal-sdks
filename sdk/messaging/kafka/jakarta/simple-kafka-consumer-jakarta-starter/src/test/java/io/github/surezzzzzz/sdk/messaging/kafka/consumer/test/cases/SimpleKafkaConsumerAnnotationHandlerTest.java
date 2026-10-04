package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation.SimpleKafkaConsumer;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.annotation.SimpleKafkaConsumerComponent;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception.KafkaConsumerConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.ConsumerRegistration;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.registrar.KafkaConsumerRegistrar;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.registrar.SimpleKafkaConsumerAnnotationHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 注解消费入口扫描测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleKafkaConsumerAnnotationHandlerTest {

    @Test
    public void testComponentMarkerIsNotNativeSpringStereotype() {
        boolean nativeComponent = SimpleKafkaConsumerComponent.class.isAnnotationPresent(Component.class);
        log.info("Consumer 扫描标记原生 Component 状态：{}", nativeComponent);

        assertFalse(nativeComponent,
                "自定义 Consumer 扫描标记不得叠加原生 Component stereotype");
    }

    @Test
    public void testOnlyMarkedComponentsAreFetchedAndRegistered() {
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        MarkedConsumer consumer = new MarkedConsumer();
        when(beanFactory.getBeanNamesForAnnotation(SimpleKafkaConsumerComponent.class))
                .thenReturn(new String[]{"markedConsumer"});
        when(beanFactory.getBean("markedConsumer")).thenReturn(consumer);
        KafkaConsumerRegistrar registrar = new KafkaConsumerRegistrar();

        new SimpleKafkaConsumerAnnotationHandler(beanFactory, registrar).afterSingletonsInstantiated();
        List<ConsumerRegistration> registrations = registrar.getRegistrations();
        log.info("标记组件扫描注册数：{}，registrationId={}", registrations.size(), registrations.get(0).getId());

        assertEquals(1, registrations.size());
        assertEquals("mock.topic", registrations.get(0).getTopic());
        try {
            assertEquals("markedConsumer#" + MarkedConsumer.class
                            .getDeclaredMethod("handle", KafkaConsumerRecord.class).toGenericString(),
                    registrations.get(0).getId());
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
        verify(beanFactory).getBean("markedConsumer");
        verify(beanFactory, never()).getBean("unrelatedLazyBean");
    }

    @Test
    public void testMultipleTopicsAreTrimmedDeduplicatedAndUseExplicitId() {
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBeanNamesForAnnotation(SimpleKafkaConsumerComponent.class))
                .thenReturn(new String[]{"multipleTopicConsumer"});
        when(beanFactory.getBean("multipleTopicConsumer")).thenReturn(new MultipleTopicConsumer());
        KafkaConsumerRegistrar registrar = new KafkaConsumerRegistrar();

        new SimpleKafkaConsumerAnnotationHandler(beanFactory, registrar).afterSingletonsInstantiated();

        List<ConsumerRegistration> registrations = registrar.getRegistrations();
        log.info("多 topic 注册结果：count={}，topics={}，ids={}", registrations.size(),
                registrations.stream().map(ConsumerRegistration::getTopic).collect(java.util.stream.Collectors.toList()),
                registrations.stream().map(ConsumerRegistration::getId).collect(java.util.stream.Collectors.toList()));

        assertEquals(2, registrations.size());
        assertEquals("topic-a", registrations.get(0).getTopic());
        assertEquals("topic-b", registrations.get(1).getTopic());
        assertEquals("explicit-id", registrations.get(0).getId());
        assertEquals("explicit-id", registrations.get(1).getId());
    }

    @Test
    public void testJdkProxyInvocationPreservesAdvice() throws Exception {
        AtomicInteger adviceInvocationCount = new AtomicInteger();
        ProxyFactory proxyFactory = new ProxyFactory(new ExposedInterfaceConsumer());
        proxyFactory.setInterfaces(ConsumerContract.class);
        proxyFactory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
            adviceInvocationCount.incrementAndGet();
            return invocation.proceed();
        });
        Object proxy = proxyFactory.getProxy();
        ConfigurableListableBeanFactory beanFactory = markedBeanFactory(proxy);
        KafkaConsumerRegistrar registrar = new KafkaConsumerRegistrar();

        new SimpleKafkaConsumerAnnotationHandler(beanFactory, registrar).afterSingletonsInstantiated();
        ConsumerRegistration registration = registrar.getRegistrations().get(0);
        invoke(registration, KafkaConsumerRecord.of(
                new ConsumerRecord<>("mock.topic", 0, 0L, "mock-key", "mock-value"),
                "mock-message", "mock-datasource", null));
        log.info("JDK 代理消费方法切面调用次数：{}", adviceInvocationCount.get());

        assertEquals(1, adviceInvocationCount.get());
    }

    @Test
    public void testJdkProxyRejectsAnnotatedMethodNotExposedByInterface() {
        ProxyFactory proxyFactory = new ProxyFactory(new HiddenMethodConsumer());
        proxyFactory.setInterfaces(PingContract.class);
        ConfigurableListableBeanFactory beanFactory = markedBeanFactory(proxyFactory.getProxy());
        KafkaConsumerRegistrar registrar = new KafkaConsumerRegistrar();

        KafkaConsumerConfigurationException exception = assertThrows(KafkaConsumerConfigurationException.class,
                () -> new SimpleKafkaConsumerAnnotationHandler(beanFactory, registrar).afterSingletonsInstantiated());
        log.info("JDK 代理未暴露消费方法的注册结果：errorCode={}，message={}",
                exception.getErrorCode(), exception.getMessage());

        assertEquals(ErrorCode.CONFIG_INVALID, exception.getErrorCode());
        assertEquals(0, registrar.getRegistrations().size());
    }

    @Test
    public void testInvalidTopicDeclarationAndMethodSignatureFailBeforeRegistration() {
        assertInvalid(new InvalidTopicConsumer());
        assertInvalid(new InvalidSignatureConsumer());
    }

    private void assertInvalid(Object consumer) {
        ConfigurableListableBeanFactory beanFactory = markedBeanFactory(consumer);
        KafkaConsumerRegistrar registrar = new KafkaConsumerRegistrar();

        KafkaConsumerConfigurationException exception = assertThrows(KafkaConsumerConfigurationException.class,
                () -> new SimpleKafkaConsumerAnnotationHandler(beanFactory, registrar).afterSingletonsInstantiated());
        log.info("非法 Consumer 声明：type={}，errorCode={}，registrationCount={}",
                consumer.getClass().getSimpleName(), exception.getErrorCode(), registrar.getRegistrations().size());

        assertEquals(ErrorCode.CONFIG_INVALID, exception.getErrorCode());
        assertTrue(registrar.getRegistrations().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private void invoke(ConsumerRegistration registration, KafkaConsumerRecord<String, String> record) throws Exception {
        ((io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler.KafkaConsumerHandler<String, String>)
                registration.getHandler()).handle(record);
    }

    private ConfigurableListableBeanFactory markedBeanFactory(Object bean) {
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBeanNamesForAnnotation(SimpleKafkaConsumerComponent.class))
                .thenReturn(new String[]{"markedConsumer"});
        when(beanFactory.getBean("markedConsumer")).thenReturn(bean);
        return beanFactory;
    }

    public interface ConsumerContract {

        void handle(KafkaConsumerRecord<String, String> record);
    }

    public interface PingContract {

        void ping();
    }

    public static class MarkedConsumer {

        @SimpleKafkaConsumer(topic = "mock.topic")
        public void handle(KafkaConsumerRecord<String, String> record) {
        }
    }

    public static class MultipleTopicConsumer {

        @SimpleKafkaConsumer(topics = {" topic-a ", "topic-b", "topic-a"}, id = "explicit-id")
        public void handle(KafkaConsumerRecord<String, String> record) {
        }
    }

    public static class InvalidTopicConsumer {

        @SimpleKafkaConsumer(topic = "topic-a", topics = "topic-b")
        public void handle(KafkaConsumerRecord<String, String> record) {
        }
    }

    public static class InvalidSignatureConsumer {

        @SimpleKafkaConsumer(topic = "topic-a")
        public String handle(KafkaConsumerRecord<String, String> record) {
            return "invalid";
        }
    }

    public static class ExposedInterfaceConsumer implements ConsumerContract {

        @Override
        @SimpleKafkaConsumer(topic = "mock.topic")
        public void handle(KafkaConsumerRecord<String, String> record) {
        }
    }

    public static class HiddenMethodConsumer implements PingContract {

        @Override
        public void ping() {
        }

        @SimpleKafkaConsumer(topic = "mock.topic")
        public void handle(KafkaConsumerRecord<String, String> record) {
        }
    }
}
