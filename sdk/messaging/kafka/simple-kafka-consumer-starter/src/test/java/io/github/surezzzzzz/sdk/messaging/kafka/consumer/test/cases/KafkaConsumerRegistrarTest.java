package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception.KafkaConsumerConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.registrar.KafkaConsumerRegistrar;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 消费注册中心测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaConsumerRegistrarTest {

    @Test
    public void testNullRegistrationFailsWithConfigurationError() {
        KafkaConsumerRegistrar registrar = new KafkaConsumerRegistrar();

        KafkaConsumerConfigurationException exception = assertThrows(KafkaConsumerConfigurationException.class,
                () -> registrar.register(null));
        log.info("空注册项错误码={}，消息={}", exception.getErrorCode(), exception.getMessage());

        assertEquals(ErrorCode.CONFIG_INVALID, exception.getErrorCode());
        assertEquals("消费配置或注册非法：registration-missing", exception.getMessage());
    }
}
