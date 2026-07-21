package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.annotation.SimpleKafkaOutboxComponent;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorMessage;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka Outbox 常量和组件注解契约测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaOutboxConstantContractTest {

    @Test
    public void testErrorCodeAndErrorMessageMatchOneToOne() throws IllegalAccessException {
        Map<String, String> codes = publicStringConstants(ErrorCode.class);
        Map<String, String> messages = publicStringConstants(ErrorMessage.class);

        log.info("ErrorCode 字段: {}, ErrorMessage 字段: {}", codes.keySet(), messages.keySet());
        assertEquals(codes.keySet(), messages.keySet(), "ErrorCode 与 ErrorMessage 字段必须反射一一对应");
        assertEquals(9, codes.size(), "Kafka Outbox 应定义九组错误码");
        for (Map.Entry<String, String> entry : codes.entrySet()) {
            String name = entry.getKey();
            String expectedCode = name;
            log.info("错误常量字段: {}, 错误码: {}, 错误消息: {}", name, entry.getValue(), messages.get(name));
            assertEquals(expectedCode, entry.getValue(), "错误码值应与字段名精确一致");
            assertFalse(messages.get(name).trim().isEmpty(), "每个错误码都必须有非空错误消息");
        }
    }

    @Test
    public void testSimpleKafkaOutboxComponentIsPureMarker() {
        Set<Class<?>> metaAnnotations = Arrays.stream(SimpleKafkaOutboxComponent.class.getAnnotations())
                .map(annotation -> annotation.annotationType())
                .collect(Collectors.toCollection(HashSet::new));
        Field[] fields = SimpleKafkaOutboxComponent.class.getDeclaredFields();

        log.info("组件标记元注解: {}, 声明字段数量: {}", metaAnnotations, fields.length);
        assertTrue(SimpleKafkaOutboxComponent.class.isAnnotation(), "SimpleKafkaOutboxComponent 必须是注解");
        assertFalse(SimpleKafkaOutboxComponent.class.isAnnotationPresent(Component.class),
                "SimpleKafkaOutboxComponent 必须是纯标记，不能叠加 @Component");
        assertEquals(0, SimpleKafkaOutboxComponent.class.getDeclaredMethods().length,
                "SimpleKafkaOutboxComponent 不应声明配置属性");
        assertEquals(0, fields.length, "SimpleKafkaOutboxComponent 不应声明字段");
    }

    private Map<String, String> publicStringConstants(Class<?> type) throws IllegalAccessException {
        Map<String, String> values = new TreeMap<>();
        for (Field field : type.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (field.getType() == String.class && Modifier.isPublic(modifiers)
                    && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                values.put(field.getName(), (String) field.get(null));
            }
        }
        return values;
    }
}
