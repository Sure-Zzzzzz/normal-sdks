package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ConsumerEventType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 消费事件类型测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@ResourceLock("default-locale")
public class ConsumerEventTypeTest {

    @Test
    public void testFromCodeIgnoresDefaultLocale() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));

            ConsumerEventType eventType = ConsumerEventType.fromCode(" IDEMPOTENT_REJECT ");
            log.info("土耳其 Locale 下事件类型解析结果：{}", eventType);

            assertEquals(ConsumerEventType.IDEMPOTENT_REJECT, eventType);
        } finally {
            Locale.setDefault(originalLocale);
        }
    }
}
