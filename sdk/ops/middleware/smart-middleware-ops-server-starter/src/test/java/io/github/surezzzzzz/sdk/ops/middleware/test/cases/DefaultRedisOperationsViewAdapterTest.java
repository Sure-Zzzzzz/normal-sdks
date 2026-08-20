package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.redis.adapter.DefaultRedisOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.discovery.RedisKeyDiscoveryRequest;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.discovery.RedisKeyDiscoveryResponse;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.read.RedisKeyReadRequest;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.read.RedisKeyReadResponse;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Redis 受控适配器结果预算与 key 发现测试。
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

        DefaultRedisOperationsViewAdapter adapter = new DefaultRedisOperationsViewAdapter(registry, 5000L, 5);
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

        DefaultRedisOperationsViewAdapter adapter = new DefaultRedisOperationsViewAdapter(registry, 5000L, 16);
        RedisKeyReadResponse response = adapter.readKey(RedisKeyReadRequest.builder()
                .datasourceKey("cache-reader").key("stream-key").offset(0).size(1).build());
        Map<String, String> limitedFields = response.getStreamEntries().get(0).getFields();
        log.info("Redis Stream 空值字段：fieldCount={}", limitedFields.size());

        assertEquals(1, limitedFields.size());
        assertNull(limitedFields.get("state"));
    }

    @Test
    void shouldDiscoverStandaloneKeysWithinBoundAndCloseRequestResources() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        Cursor<byte[]> cursor = cursor("ops:fixture:a", "ops:fixture:a", "ops:fixture:b", "ops:fixture:c");
        when(registry.containsDatasource("cache-reader")).thenReturn(true);
        when(registry.getConnectionFactory("cache-reader")).thenReturn(factory);
        when(factory.getClusterConnection()).thenThrow(new InvalidDataAccessApiUsageException("Cluster is not configured!"));
        when(factory.getConnection()).thenReturn(connection);
        when(connection.scan(any(ScanOptions.class))).thenReturn(cursor);

        RedisKeyDiscoveryResponse response = new DefaultRedisOperationsViewAdapter(registry, 5000L, 64)
                .discoverKeys(discovery("ops:fixture:", 2));
        log.info("Redis standalone key 发现：returned={}，stopReason={}", response.getReturned(),
                response.getStopReason());

        assertEquals(Arrays.asList("ops:fixture:a", "ops:fixture:b"), response.getItems());
        assertEquals(2, response.getLimit());
        assertEquals(2, response.getReturned());
        assertTrue(response.getTruncated());
        assertFalse(response.getTraversalComplete());
        assertEquals("RESULT_LIMIT", response.getStopReason());
        verify(connection).close();
        verify(cursor).close();
        verify(connection).scan(any(ScanOptions.class));
        verify(registry, never()).getStringRedisTemplate(anyString());
    }

    @Test
    void shouldUseValidatedPrefixAndFixedScanCount() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        Cursor<byte[]> cursor = cursor();
        when(registry.containsDatasource("cache-reader")).thenReturn(true);
        when(registry.getConnectionFactory("cache-reader")).thenReturn(factory);
        when(factory.getClusterConnection()).thenThrow(new InvalidDataAccessApiUsageException("Cluster is not configured!"));
        when(factory.getConnection()).thenReturn(connection);
        when(connection.scan(any(ScanOptions.class))).thenReturn(cursor);

        new DefaultRedisOperationsViewAdapter(registry, 5000L, 64).discoverKeys(discovery("ops:fixture:", 2));

        org.mockito.ArgumentCaptor<ScanOptions> options = org.mockito.ArgumentCaptor.forClass(ScanOptions.class);
        verify(connection).scan(options.capture());
        assertEquals("ops:fixture:*", options.getValue().getPattern());
        assertEquals(50L, options.getValue().getCount());
    }

    @Test
    void shouldReturnCompletedForEmptyStandaloneDiscovery() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        Cursor<byte[]> cursor = cursor();
        when(registry.containsDatasource("cache-reader")).thenReturn(true);
        when(registry.getConnectionFactory("cache-reader")).thenReturn(factory);
        when(factory.getClusterConnection()).thenThrow(new InvalidDataAccessApiUsageException("Cluster is not configured!"));
        when(factory.getConnection()).thenReturn(connection);
        when(connection.scan(any(ScanOptions.class))).thenReturn(cursor);

        RedisKeyDiscoveryResponse response = new DefaultRedisOperationsViewAdapter(registry, 5000L, 64)
                .discoverKeys(discovery("ops:fixture:", 2));

        assertEquals(Collections.emptyList(), response.getItems());
        assertFalse(response.getTruncated());
        assertTrue(response.getTraversalComplete());
        assertEquals("COMPLETED", response.getStopReason());
        verify(connection).close();
        verify(cursor).close();
    }

    @Test
    void shouldStopBeforeReturningKeyBeyondResponseBudget() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        Cursor<byte[]> cursor = cursor("ops:fixture:too-large");
        when(registry.containsDatasource("cache-reader")).thenReturn(true);
        when(registry.getConnectionFactory("cache-reader")).thenReturn(factory);
        when(factory.getClusterConnection()).thenThrow(new InvalidDataAccessApiUsageException("Cluster is not configured!"));
        when(factory.getConnection()).thenReturn(connection);
        when(connection.scan(any(ScanOptions.class))).thenReturn(cursor);

        RedisKeyDiscoveryResponse response = new DefaultRedisOperationsViewAdapter(registry, 5000L, 4)
                .discoverKeys(discovery("ops:fixture:", 2));

        assertEquals(Collections.emptyList(), response.getItems());
        assertTrue(response.getTruncated());
        assertFalse(response.getTraversalComplete());
        assertEquals("RESPONSE_LIMIT", response.getStopReason());
        verify(connection).close();
        verify(cursor).close();
    }

    @Test
    void shouldStopAtFixedObservedEntryLimit() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        Cursor<byte[]> cursor = repeatedCursor("ops:other:", 1001);
        when(registry.containsDatasource("cache-reader")).thenReturn(true);
        when(registry.getConnectionFactory("cache-reader")).thenReturn(factory);
        when(factory.getClusterConnection()).thenThrow(new InvalidDataAccessApiUsageException("Cluster is not configured!"));
        when(factory.getConnection()).thenReturn(connection);
        when(connection.scan(any(ScanOptions.class))).thenReturn(cursor);

        RedisKeyDiscoveryResponse response = new DefaultRedisOperationsViewAdapter(registry, 5000L, 64)
                .discoverKeys(discovery("ops:fixture:", 2));

        assertEquals(Collections.emptyList(), response.getItems());
        assertTrue(response.getTruncated());
        assertFalse(response.getTraversalComplete());
        assertEquals("WORK_LIMIT", response.getStopReason());
        verify(connection).close();
        verify(cursor).close();
    }

    @Test
    void shouldStopWhenKeyCannotBeRepresentedAsUtf8() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        Cursor<byte[]> cursor = bytesCursor(new byte[]{(byte) 0xc3, (byte) 0x28});
        when(registry.containsDatasource("cache-reader")).thenReturn(true);
        when(registry.getConnectionFactory("cache-reader")).thenReturn(factory);
        when(factory.getClusterConnection()).thenThrow(new InvalidDataAccessApiUsageException("Cluster is not configured!"));
        when(factory.getConnection()).thenReturn(connection);
        when(connection.scan(any(ScanOptions.class))).thenReturn(cursor);

        RedisKeyDiscoveryResponse response = new DefaultRedisOperationsViewAdapter(registry, 5000L, 64)
                .discoverKeys(discovery("ops:fixture:", 2));

        assertEquals(Collections.emptyList(), response.getItems());
        assertTrue(response.getTruncated());
        assertFalse(response.getTraversalComplete());
        assertEquals("UNSUPPORTED_KEY_ENCODING", response.getStopReason());
        verify(connection).close();
        verify(cursor).close();
    }

    @Test
    void shouldRejectUnknownDatasourceBeforeObtainingConnectionFactory() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        when(registry.containsDatasource("cache-reader")).thenReturn(false);

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> new DefaultRedisOperationsViewAdapter(registry, 5000L, 64)
                        .discoverKeys(discovery("ops:fixture:", 2)));

        assertEquals(404, exception.getStatus().value());
        assertEquals("目标数据源不存在", exception.getMessage());
        verify(registry, never()).getConnectionFactory(anyString());
    }

    @Test
    void shouldScanOnlyClusterMastersAndCloseClusterResources() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisClusterConnection connection = mock(RedisClusterConnection.class);
        RedisClusterNode master = mock(RedisClusterNode.class);
        RedisClusterNode replica = mock(RedisClusterNode.class);
        Cursor<byte[]> cursor = cursor("ops:fixture:cluster:a", "ops:fixture:cluster:b");
        when(registry.containsDatasource("cache-reader")).thenReturn(true);
        when(registry.getConnectionFactory("cache-reader")).thenReturn(factory);
        when(factory.getClusterConnection()).thenReturn(connection);
        when(connection.clusterGetNodes()).thenReturn(Arrays.asList(master, replica));
        when(master.getFlags()).thenReturn(Collections.singleton(RedisClusterNode.Flag.MASTER));
        when(replica.getFlags()).thenReturn(Collections.singleton(RedisClusterNode.Flag.SLAVE));
        when(connection.scan(eq(master), any(ScanOptions.class))).thenReturn(cursor);

        RedisKeyDiscoveryResponse response = new DefaultRedisOperationsViewAdapter(registry, 5000L, 64)
                .discoverKeys(discovery("ops:fixture:", 1));

        assertEquals(Collections.singletonList("ops:fixture:cluster:a"), response.getItems());
        assertEquals("RESULT_LIMIT", response.getStopReason());
        verify(connection).scan(eq(master), any(ScanOptions.class));
        verify(connection, never()).scan(eq(replica), any(ScanOptions.class));
        verify(connection).close();
        verify(cursor).close();
    }

    @Test
    void shouldDiscardClusterCandidatesWhenMasterScanFails() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisClusterConnection connection = mock(RedisClusterConnection.class);
        RedisClusterNode master = mock(RedisClusterNode.class);
        Cursor<byte[]> cursor = mock(Cursor.class);
        when(registry.containsDatasource("cache-reader")).thenReturn(true);
        when(registry.getConnectionFactory("cache-reader")).thenReturn(factory);
        when(factory.getClusterConnection()).thenReturn(connection);
        when(connection.clusterGetNodes()).thenReturn(Collections.singletonList(master));
        when(master.getFlags()).thenReturn(Collections.singleton(RedisClusterNode.Flag.MASTER));
        when(connection.scan(eq(master), any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenThrow(new IllegalStateException("fixture failure"));

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> new DefaultRedisOperationsViewAdapter(registry, 5000L, 64)
                        .discoverKeys(discovery("ops:fixture:", 10)));

        assertEquals(503, exception.getStatus().value());
        assertEquals("Redis 运维查询暂不可用", exception.getMessage());
        verify(connection).close();
        verify(cursor).close();
    }

    @Test
    void shouldDiscardCandidatesWhenDiscoveryDeadlineExpires() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        Cursor<byte[]> cursor = cursor("ops:fixture:a");
        when(registry.containsDatasource("cache-reader")).thenReturn(true);
        when(registry.getConnectionFactory("cache-reader")).thenReturn(factory);
        when(factory.getClusterConnection()).thenThrow(new InvalidDataAccessApiUsageException("Cluster is not configured!"));
        when(factory.getConnection()).thenReturn(connection);
        when(connection.scan(any(ScanOptions.class))).thenReturn(cursor);

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> new DefaultRedisOperationsViewAdapter(registry, 0L, 64)
                        .discoverKeys(discovery("ops:fixture:", 10)));

        assertEquals(504, exception.getStatus().value());
        assertEquals("Redis 运维查询已超时", exception.getMessage());
        verify(connection).close();
        verify(cursor).close();
    }

    @Test
    void shouldScanAllClusterMastersBeforeCompletingDiscovery() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisClusterConnection connection = mock(RedisClusterConnection.class);
        RedisClusterNode firstMaster = mock(RedisClusterNode.class);
        RedisClusterNode secondMaster = mock(RedisClusterNode.class);
        Cursor<byte[]> firstCursor = cursor();
        Cursor<byte[]> secondCursor = cursor("ops:fixture:cluster:second");
        when(registry.containsDatasource("cache-reader")).thenReturn(true);
        when(registry.getConnectionFactory("cache-reader")).thenReturn(factory);
        when(factory.getClusterConnection()).thenReturn(connection);
        when(connection.clusterGetNodes()).thenReturn(Arrays.asList(firstMaster, secondMaster));
        when(firstMaster.getFlags()).thenReturn(Collections.singleton(RedisClusterNode.Flag.MASTER));
        when(secondMaster.getFlags()).thenReturn(Collections.singleton(RedisClusterNode.Flag.MASTER));
        when(connection.scan(eq(firstMaster), any(ScanOptions.class))).thenReturn(firstCursor);
        when(connection.scan(eq(secondMaster), any(ScanOptions.class))).thenReturn(secondCursor);

        RedisKeyDiscoveryResponse response = new DefaultRedisOperationsViewAdapter(registry, 5000L, 64)
                .discoverKeys(discovery("ops:fixture:", 2));

        assertEquals(Collections.singletonList("ops:fixture:cluster:second"), response.getItems());
        assertFalse(response.getTruncated());
        assertTrue(response.getTraversalComplete());
        assertEquals("COMPLETED", response.getStopReason());
        verify(connection).scan(eq(firstMaster), any(ScanOptions.class));
        verify(connection).scan(eq(secondMaster), any(ScanOptions.class));
        verify(firstCursor).close();
        verify(secondCursor).close();
        verify(connection).close();
    }

    @Test
    void shouldReturnUnavailableWhenClusterTopologyCannotBeRead() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisClusterConnection connection = mock(RedisClusterConnection.class);
        when(registry.containsDatasource("cache-reader")).thenReturn(true);
        when(registry.getConnectionFactory("cache-reader")).thenReturn(factory);
        when(factory.getClusterConnection()).thenReturn(connection);
        when(connection.clusterGetNodes()).thenThrow(new IllegalStateException("fixture failure"));

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> new DefaultRedisOperationsViewAdapter(registry, 5000L, 64)
                        .discoverKeys(discovery("ops:fixture:", 10)));

        assertEquals(503, exception.getStatus().value());
        assertEquals("Redis 运维查询暂不可用", exception.getMessage());
        verify(connection).close();
    }

    @Test
    void shouldReturnUnavailableWhenClusterHasNoMaster() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisClusterConnection connection = mock(RedisClusterConnection.class);
        when(registry.containsDatasource("cache-reader")).thenReturn(true);
        when(registry.getConnectionFactory("cache-reader")).thenReturn(factory);
        when(factory.getClusterConnection()).thenReturn(connection);
        when(connection.clusterGetNodes()).thenReturn(Collections.emptyList());

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> new DefaultRedisOperationsViewAdapter(registry, 5000L, 64)
                        .discoverKeys(discovery("ops:fixture:", 10)));

        assertEquals(503, exception.getStatus().value());
        assertEquals("Redis 运维查询暂不可用", exception.getMessage());
        verify(connection, never()).scan(any(RedisClusterNode.class), any(ScanOptions.class));
        verify(connection).close();
    }

    @Test
    void shouldMapCursorCloseFailureToUnavailable() throws Exception {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        Cursor<byte[]> cursor = cursor();
        when(registry.containsDatasource("cache-reader")).thenReturn(true);
        when(registry.getConnectionFactory("cache-reader")).thenReturn(factory);
        when(factory.getClusterConnection()).thenThrow(new InvalidDataAccessApiUsageException("Cluster is not configured!"));
        when(factory.getConnection()).thenReturn(connection);
        when(connection.scan(any(ScanOptions.class))).thenReturn(cursor);
        doThrow(new IOException("fixture failure")).when(cursor).close();

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> new DefaultRedisOperationsViewAdapter(registry, 5000L, 64)
                        .discoverKeys(discovery("ops:fixture:", 10)));

        assertEquals(503, exception.getStatus().value());
        assertEquals("Redis 运维查询暂不可用", exception.getMessage());
        assertEquals(IOException.class, exception.getCause().getClass());
        assertEquals("fixture failure", exception.getCause().getMessage());
        verify(connection).close();
        verify(cursor).close();
    }

    @Test
    void shouldMapStandaloneConnectionFailureToUnavailable() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(registry.containsDatasource("cache-reader")).thenReturn(true);
        when(registry.getConnectionFactory("cache-reader")).thenReturn(factory);
        when(factory.getClusterConnection()).thenThrow(new InvalidDataAccessApiUsageException("Cluster is not configured!"));
        when(factory.getConnection()).thenThrow(new IllegalStateException("fixture failure"));

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> new DefaultRedisOperationsViewAdapter(registry, 5000L, 64)
                        .discoverKeys(discovery("ops:fixture:", 10)));

        assertEquals(503, exception.getStatus().value());
        assertEquals("Redis 运维查询暂不可用", exception.getMessage());
        assertNotNull(exception.getCause());
        assertEquals("fixture failure", exception.getCause().getMessage());
        verify(registry, never()).getStringRedisTemplate(anyString());
    }

    @SuppressWarnings("unchecked")
    private Cursor<byte[]> cursor(String... values) {
        Cursor<byte[]> cursor = mock(Cursor.class);
        Iterator<String> iterator = Arrays.asList(values).iterator();
        when(cursor.hasNext()).thenAnswer(invocation -> iterator.hasNext());
        when(cursor.next()).thenAnswer(invocation -> iterator.next().getBytes(StandardCharsets.UTF_8));
        return cursor;
    }

    @SuppressWarnings("unchecked")
    private Cursor<byte[]> bytesCursor(byte[]... values) {
        Cursor<byte[]> cursor = mock(Cursor.class);
        Iterator<byte[]> iterator = Arrays.asList(values).iterator();
        when(cursor.hasNext()).thenAnswer(invocation -> iterator.hasNext());
        when(cursor.next()).thenAnswer(invocation -> iterator.next());
        return cursor;
    }

    @SuppressWarnings("unchecked")
    private Cursor<byte[]> repeatedCursor(String value, int count) {
        Cursor<byte[]> cursor = mock(Cursor.class);
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int[] remaining = {count};
        when(cursor.hasNext()).thenAnswer(invocation -> remaining[0] > 0);
        when(cursor.next()).thenAnswer(invocation -> {
            remaining[0]--;
            return bytes;
        });
        return cursor;
    }

    private RedisKeyDiscoveryRequest discovery(String prefix, int size) {
        return RedisKeyDiscoveryRequest.builder().datasourceKey("cache-reader").prefix(prefix).size(size).build();
    }
}
