package io.github.surezzzzzz.sdk.cache.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.surezzzzzz.sdk.cache.constant.ErrorCode;
import io.github.surezzzzzz.sdk.cache.exception.CacheSerializationException;
import io.github.surezzzzzz.sdk.cache.serializer.JacksonSmartCacheSerializer;
import io.github.surezzzzzz.sdk.cache.serializer.PackageSmartCacheTypeValidator;
import io.github.surezzzzzz.sdk.cache.serializer.SmartCacheSerializer;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Smart Cache 序列化测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
class SmartCacheSerializerTest {

    @Test
    @DisplayName("测试按声明类型反序列化")
    void shouldDeserializeByDeclaredType() {
        SmartCacheSerializer serializer = createSerializer(Collections.singletonList("java.lang"));

        String payload = serializer.serialize("hello", String.class);
        String value = (String) serializer.deserialize(payload, Object.class);

        log.info("序列化结果: {}", payload);
        assertEquals("hello", value, "应按 payload 中的可信类型恢复字符串");
    }

    @Test
    @DisplayName("测试按调用方传入类型反序列化")
    void shouldDeserializeByExpectedType() {
        SmartCacheSerializer serializer = createSerializer(Collections.singletonList("java.lang"));
        CacheUser user = new CacheUser("u-001", LocalDateTime.of(2026, 1, 1, 10, 30));

        String payload = serializer.serialize(user, Object.class);
        CacheUser value = (CacheUser) serializer.deserialize(payload, CacheUser.class);

        assertEquals(user, value, "应按调用方传入类型恢复对象");
    }

    @Test
    @DisplayName("测试不可信 payload 类型会被拒绝")
    void shouldRejectUntrustedPayloadType() {
        SmartCacheSerializer serializer = createSerializer(Collections.singletonList("java.lang"));
        String payload = "{\"type\":\"io.github.surezzzzzz.sdk.cache.test.cases.SmartCacheSerializerTest$CacheUser\",\"data\":{\"name\":\"u-001\"}}";

        CacheSerializationException exception = assertThrows(CacheSerializationException.class,
                () -> serializer.deserialize(payload, Object.class),
                "不可信类型应被拒绝");

        assertEquals(ErrorCode.SMART_CACHE_SERIALIZATION_FAILED, exception.getErrorCode(), "错误码应正确");
    }

    @Test
    @DisplayName("测试空 payload 返回 null")
    void shouldReturnNullWhenPayloadBlank() {
        SmartCacheSerializer serializer = createSerializer(Arrays.asList("java.lang", "java.time"));

        assertNull(serializer.deserialize(null, Object.class), "null payload 应返回 null");
        assertNull(serializer.deserialize(" ", Object.class), "空白 payload 应返回 null");
    }

    private SmartCacheSerializer createSerializer(java.util.List<String> trustedPackages) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new JacksonSmartCacheSerializer(objectMapper, new PackageSmartCacheTypeValidator(trustedPackages));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CacheUser {
        private String name;
        private LocalDateTime createTime;
    }
}
