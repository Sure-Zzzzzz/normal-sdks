package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.support.KafkaProducerFactoryCompatibilityHelper;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaSpringVersionHelper;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaTemplateCompatibilityHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka route 兼容层 Helper 测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaCompatibilityHelperTest {

    @Test
    public void testApplyTransactionIdPrefixMakesFactoryTransactional() {
        DefaultKafkaProducerFactory<Object, Object> factory = new DefaultKafkaProducerFactory<>(
                Collections.<String, Object>emptyMap());

        KafkaProducerFactoryCompatibilityHelper.applyTransactionIdPrefix(factory, "kafka-route-tx-");

        KafkaTemplate<Object, Object> template = new KafkaTemplate<>(factory);
        assertEquals("kafka-route-tx-", KafkaTemplateCompatibilityHelper.getTransactionIdPrefix(template));
        assertTrue(factory.transactionCapable());
        assertTrue(template.isTransactional());
    }

    @Test
    public void testApplyTransactionIdPrefixSkipsBlankPrefix() {
        DefaultKafkaProducerFactory<Object, Object> factory = new DefaultKafkaProducerFactory<>(
                Collections.<String, Object>emptyMap());

        KafkaProducerFactoryCompatibilityHelper.applyTransactionIdPrefix(factory, "  ");

        assertFalse(factory.transactionCapable());
    }

    @Test
    public void testTemplateTransactionIdPrefixFallbackMatchesFactory() {
        DefaultKafkaProducerFactory<Object, Object> factory = new DefaultKafkaProducerFactory<>(
                Collections.<String, Object>emptyMap());
        factory.setTransactionIdPrefix("kafka-route-template-tx-");
        KafkaTemplate<Object, Object> template = new KafkaTemplate<>(factory);

        assertEquals("kafka-route-template-tx-",
                KafkaTemplateCompatibilityHelper.getTransactionIdPrefix(template));
        assertTrue(KafkaTemplateCompatibilityHelper.isTransactional(template));
    }

    @Test
    public void testTemplateTransactionIdPrefixNullForNonTransactionalTemplate() {
        DefaultKafkaProducerFactory<Object, Object> factory = new DefaultKafkaProducerFactory<>(
                Collections.<String, Object>emptyMap());
        KafkaTemplate<Object, Object> template = new KafkaTemplate<>(factory);

        assertNull(KafkaTemplateCompatibilityHelper.getTransactionIdPrefix(template));
        assertFalse(KafkaTemplateCompatibilityHelper.isTransactional(template));
    }

    @Test
    public void testDestroyProducerFactoryClosesDisposableFactory() {
        DefaultKafkaProducerFactory<Object, Object> factory = new DefaultKafkaProducerFactory<>(
                Collections.<String, Object>emptyMap());

        KafkaProducerFactoryCompatibilityHelper.destroyProducerFactory(factory);

        // 销毁后 transactionCapable 仍可读取，验证未抛异常即可
        assertFalse(factory.transactionCapable());
    }

    @Test
    public void testSpringKafkaCapabilityDetectionConsistentWithClasspath() {
        String version = KafkaSpringVersionHelper.detectSpringKafkaVersion();
        log.info("当前 Spring Kafka 版本: {}", version);
        assertNotNull(version);
        // KafkaTemplate#isTransactional 在 2.x 全程为 public，能力探测应返回 true
        assertTrue(KafkaSpringVersionHelper.supportsKafkaTemplateIsTransactional());
        // route 实际使用 DefaultKafkaProducerFactory；2.3.x 方法为 protected，兼容层通过反射可访问。
        boolean supports = KafkaSpringVersionHelper.supportsGetTransactionIdPrefix();
        log.info("DefaultKafkaProducerFactory#getTransactionIdPrefix 实际可访问能力: {}, version: {}", supports, version);
        assertTrue(supports, "DefaultKafkaProducerFactory#getTransactionIdPrefix 在支持版本中必须可反射访问");
    }

    @Test
    public void testGetConfigurationPropertiesNullSafe() {
        assertEquals(Collections.emptyMap(),
                KafkaProducerFactoryCompatibilityHelper.getConfigurationProperties(null));
    }
}
