package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.KafkaPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishException;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishResult;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.SimpleKafkaPublisherTestApplication;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.support.KafkaPublisherEndToEndHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kafka Publisher 真实 broker 失败传播测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleKafkaPublisherTestApplication.class)
public class KafkaPublisherEndToEndFailureTest {

    private static final String DEAD_DATASOURCE = "dead";

    @Autowired
    private KafkaPublisher publisher;

    @Test
    public void testAsyncFailureFromDeadBrokerPropagatesAsKafkaPublishException()
            throws Exception {
        KafkaPublishMessage<String> message = KafkaPublishMessage.<String>builder()
                .topic("mock.publisher.dead.e2e")
                .key("dead-key")
                .messageId("dead-message")
                .messageType(KafkaPublisherEndToEndHelper.MESSAGE_TYPE)
                .payload(KafkaPublisherEndToEndHelper.PAYLOAD)
                .build();

        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> publisher.publishOn(DEAD_DATASOURCE, message)
                        .get(KafkaPublisherEndToEndHelper.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        log.info("真实 broker 失败 cause: {}", exception.getCause().getMessage());
        assertTrue(exception.getCause() instanceof KafkaPublishException,
                "真实 broker 失败应通过 Future 传播为 KafkaPublishException");
        assertEquals(ErrorCode.KAFKA_PUBLISHER_007,
                ((KafkaPublishException) exception.getCause()).getErrorCode(),
                "真实 broker 失败应使用发送失败错误码");
    }

    @Test
    public void testPublishAndWaitSyncFailureFromDeadBroker() {
        KafkaPublishMessage<String> message = KafkaPublishMessage.<String>builder()
                .topic("mock.publisher.dead.e2e.sync")
                .key("dead-sync-key")
                .messageId("dead-sync-message")
                .messageType(KafkaPublisherEndToEndHelper.MESSAGE_TYPE)
                .payload(KafkaPublisherEndToEndHelper.PAYLOAD)
                .build();

        KafkaPublishException exception = assertThrows(KafkaPublishException.class,
                () -> publisher.publishAndWait(message));

        log.info("publishAndWait 同步真实 broker 失败: {}", exception.getMessage());
        assertEquals(ErrorCode.KAFKA_PUBLISHER_007, exception.getErrorCode(),
                "同步 publishAndWait 真实 broker 失败应使用发送失败错误码");
    }
}
