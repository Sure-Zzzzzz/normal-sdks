package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.ops.middleware.redis.DefaultRedisOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.redis.RedisKeyReadRequest;
import io.github.surezzzzzz.sdk.ops.middleware.redis.RedisKeyReadResponse;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redis 只读适配器结果预算测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class DefaultRedisOperationsViewAdapterTest {

    @Test
    void shouldLimitStreamFieldAndValueWithinSharedBudget() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
        @SuppressWarnings("unchecked")
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        RecordId recordId = mock(RecordId.class);
        Map<Object, Object> fields = new LinkedHashMap<>();
        fields.put("abcd", "efgh");

        when(registry.containsDatasource("cache-reader")).thenReturn(true);
        when(registry.getStringRedisTemplate("cache-reader")).thenReturn(template);
        when(template.type("stream-key")).thenReturn(DataType.STREAM);
        when(template.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.read(any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(Collections.singletonList(record));
        when(record.getId()).thenReturn(recordId);
        when(recordId.getValue()).thenReturn("1-0");
        when(record.getValue()).thenReturn(fields);

        DefaultRedisOperationsViewAdapter adapter = new DefaultRedisOperationsViewAdapter(registry, 5);
        RedisKeyReadResponse response = adapter.readKey(RedisKeyReadRequest.builder()
                .datasourceKey("cache-reader").key("stream-key").offset(0).size(1).build());
        Map<String, String> limitedFields = response.getStreamEntries().get(0).getFields();
        log.info("Redis Stream 字段预算：fieldCount={}，valueLength={}", limitedFields.size(),
                limitedFields.get("abcd").length());

        assertEquals(1, limitedFields.size());
        assertEquals("e", limitedFields.get("abcd"));
    }

    @Test
    void shouldPreserveNullStreamFieldValue() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
        @SuppressWarnings("unchecked")
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        RecordId recordId = mock(RecordId.class);
        Map<Object, Object> fields = new LinkedHashMap<>();
        fields.put("state", null);

        when(registry.containsDatasource("cache-reader")).thenReturn(true);
        when(registry.getStringRedisTemplate("cache-reader")).thenReturn(template);
        when(template.type("stream-key")).thenReturn(DataType.STREAM);
        when(template.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.read(any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(Collections.singletonList(record));
        when(record.getId()).thenReturn(recordId);
        when(recordId.getValue()).thenReturn("1-0");
        when(record.getValue()).thenReturn(fields);

        DefaultRedisOperationsViewAdapter adapter = new DefaultRedisOperationsViewAdapter(registry, 16);
        RedisKeyReadResponse response = adapter.readKey(RedisKeyReadRequest.builder()
                .datasourceKey("cache-reader").key("stream-key").offset(0).size(1).build());
        Map<String, String> limitedFields = response.getStreamEntries().get(0).getFields();
        log.info("Redis Stream 空值字段：fieldCount={}", limitedFields.size());

        assertEquals(1, limitedFields.size());
        assertNull(limitedFields.get("state"));
    }
}
