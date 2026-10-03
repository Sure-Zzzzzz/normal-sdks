package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.constant.KafkaRouteInputType;
import io.github.surezzzzzz.sdk.kafka.route.constant.KafkaRouteOperationType;
import io.github.surezzzzzz.sdk.kafka.route.constant.RouteMatchType;
import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import io.github.surezzzzzz.sdk.kafka.route.support.KafkaRouteSensitiveLogHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka route 敏感日志 Helper 测试
 *
 * @author surezzzzzz
 */
@Slf4j
@ResourceLock("default-locale")
public class KafkaRouteSensitiveLogHelperTest {

    @Test
    public void testSensitiveKeyDetectionIsCaseInsensitive() {
        assertTrue(KafkaRouteSensitiveLogHelper.isSensitiveKey("ssl.keystore.password"));
        assertTrue(KafkaRouteSensitiveLogHelper.isSensitiveKey("SASL.JAAS.CONFIG"));
        assertTrue(KafkaRouteSensitiveLogHelper.isSensitiveKey("client.credential"));
        assertTrue(KafkaRouteSensitiveLogHelper.isSensitiveKey("mock.secret.key"));
        assertTrue(KafkaRouteSensitiveLogHelper.isSensitiveKey("ssl.key.location"));
        assertFalse(KafkaRouteSensitiveLogHelper.isSensitiveKey("bootstrap.servers"));
    }

    @Test
    public void testLocaleRootKeepsSensitiveKeyAndEnumParsing() {
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            log.info("土耳其 Locale 下准备验证敏感 key 与 route 枚举解析");

            assertTrue(KafkaRouteSensitiveLogHelper.isSensitiveKey("SASL.JAAS.CONFIG"));
            assertEquals(KafkaRouteInputType.TOPIC, KafkaRouteInputType.fromCode("TOPIC"));
            assertEquals(KafkaRouteOperationType.EXECUTE, KafkaRouteOperationType.fromCode("EXECUTE"));
            assertEquals(RouteMatchType.PREFIX, RouteMatchType.fromCode("PREFIX"));
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    public void testMaskSensitiveKeysReturnsNewMapAndKeepsOriginalValues() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("bootstrap.servers", "localhost:19092");
        config.put("sasl.jaas.config", "${KAFKA_ROUTE_JAAS_CONFIG}");
        config.put("ssl.key.password", "${KAFKA_ROUTE_SSL_KEY_PASSWORD}");
        config.put("metadata.max.age.ms", "30000");

        Map<String, Object> masked = KafkaRouteSensitiveLogHelper.maskSensitiveKeys(config);
        log.info("脱敏后的配置: {}", masked);

        assertNotSame(config, masked);
        assertEquals("localhost:19092", masked.get("bootstrap.servers"));
        assertEquals(SimpleKafkaRouteConstant.MASKED_VALUE, masked.get("sasl.jaas.config"));
        assertEquals(SimpleKafkaRouteConstant.MASKED_VALUE, masked.get("ssl.key.password"));
        assertEquals("30000", masked.get("metadata.max.age.ms"));
        assertEquals("${KAFKA_ROUTE_JAAS_CONFIG}", config.get("sasl.jaas.config"),
                "脱敏 Helper 不应修改原始 config map");
    }
}
