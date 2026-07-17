package io.github.surezzzzzz.sdk.retry.redis.smart.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.retry.redis.smart.serializer.JacksonRetryContextSerializer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link JacksonRetryContextSerializer} 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
class JacksonRetryContextSerializerTest {

    private JacksonRetryContextSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new JacksonRetryContextSerializer(new ObjectMapper());
        log.info("初始化 JacksonRetryContextSerializer 测试");
    }

    @Test
    void serializeNullShouldReturnNull() {
        assertNull(serializer.serialize(null));
    }

    @Test
    void serializeEmptyShouldReturnNull() {
        assertNull(serializer.serialize(Collections.<String, Object>emptyMap()));
    }

    @Test
    void serializeAndDeserializeShouldBeSymmetric() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("extraField", "value");
        context.put("count", 3);
        String json = serializer.serialize(context);
        Map<String, Object> result = serializer.deserialize(json);
        assertEquals("value", result.get("extraField"));
        assertEquals(Integer.valueOf(3), result.get("count"));
    }

    @Test
    void deserializeNullShouldReturnEmptyMap() {
        assertTrue(serializer.deserialize(null).isEmpty());
        assertTrue(serializer.deserialize("").isEmpty());
        assertTrue(serializer.deserialize("  ").isEmpty());
    }
}
