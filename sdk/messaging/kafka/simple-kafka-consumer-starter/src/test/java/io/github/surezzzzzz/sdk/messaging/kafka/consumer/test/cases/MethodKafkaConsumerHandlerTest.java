package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception.KafkaConsumerException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler.MethodKafkaConsumerHandler;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 注解方法消费处理器异常契约测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class MethodKafkaConsumerHandlerTest {

    @Test
    public void testInaccessibleMethodUsesConsumerException() throws Exception {
        Method method = InaccessibleConsumer.class.getDeclaredMethod("handle", KafkaConsumerRecord.class);
        MethodKafkaConsumerHandler handler = new MethodKafkaConsumerHandler(new InaccessibleConsumer(), method);

        KafkaConsumerException exception = assertThrows(KafkaConsumerException.class,
                () -> handler.handle(record()));
        log.info("不可访问注解方法错误码：{}", exception.getErrorCode());

        assertEquals(ErrorCode.CONSUME_FATAL, exception.getErrorCode());
    }

    @Test
    public void testNonExceptionThrowableUsesConsumerException() throws Exception {
        Method method = ThrowableConsumer.class.getDeclaredMethod("handle", KafkaConsumerRecord.class);
        MethodKafkaConsumerHandler handler = new MethodKafkaConsumerHandler(new ThrowableConsumer(), method);

        KafkaConsumerException exception = assertThrows(KafkaConsumerException.class,
                () -> handler.handle(record()));
        log.info("非 Exception 注解方法错误码：{}", exception.getErrorCode());

        assertEquals(ErrorCode.CONSUME_FATAL, exception.getErrorCode());
    }

    private KafkaConsumerRecord<String, String> record() {
        return KafkaConsumerRecord.of(new ConsumerRecord<>("mock.topic", 0, 0L, "mock-key", "mock-value"),
                "mock-message", "mock-datasource", null);
    }

    private static class InaccessibleConsumer {

        private void handle(KafkaConsumerRecord<String, String> record) {
        }
    }

    public static class ThrowableConsumer {

        public void handle(KafkaConsumerRecord<String, String> record) throws Throwable {
            throw new Throwable("mock throwable");
        }
    }
}
