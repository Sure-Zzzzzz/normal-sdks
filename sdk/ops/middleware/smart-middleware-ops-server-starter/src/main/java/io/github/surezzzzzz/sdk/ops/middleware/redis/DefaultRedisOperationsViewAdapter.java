package io.github.surezzzzzz.sdk.ops.middleware.redis;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.redis.route.model.RedisServerInfo;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.HttpStatus;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 仅通过 Redis Route Registry 获取安全视图的适配器。
 *
 * @author surezzzzzz
 */
public class DefaultRedisOperationsViewAdapter implements RedisOperationsViewAdapter {

    private static final String TYPE_STRING = "string";
    private static final String TYPE_HASH = "hash";
    private static final String TYPE_LIST = "list";
    private static final String TYPE_SET = "set";
    private static final String TYPE_ZSET = "zset";
    private static final String TYPE_STREAM = "stream";
    private static final String STOP_REASON_COMPLETED = "COMPLETED";
    private static final String STOP_REASON_RESULT_LIMIT = "RESULT_LIMIT";
    private static final String STOP_REASON_RESPONSE_LIMIT = "RESPONSE_LIMIT";
    private static final String STOP_REASON_WORK_LIMIT = "WORK_LIMIT";
    private static final String STOP_REASON_UNSUPPORTED_KEY_ENCODING = "UNSUPPORTED_KEY_ENCODING";
    private static final int DISCOVERY_SCAN_COUNT = 50;
    private static final int DISCOVERY_MAX_OBSERVED_ENTRIES = 1000;

    private final SimpleRedisRouteRegistry registry;
    private final long deadlineMillis;
    private final int maxValueLength;

    /**
     * 创建 Redis Route 适配器。
     *
     * @param registry       Redis Route Registry
     * @param deadlineMillis 单次查询截止时间
     * @param maxValueLength 响应最大字符数
     */
    public DefaultRedisOperationsViewAdapter(SimpleRedisRouteRegistry registry, long deadlineMillis, int maxValueLength) {
        this.registry = registry;
        this.deadlineMillis = deadlineMillis;
        this.maxValueLength = maxValueLength;
    }

    @Override
    public RedisDatasourceListResponse listDatasources() {
        List<RedisDatasourceResponse> items = new ArrayList<>();
        for (String datasourceKey : registry.getDatasourceKeys()) {
            items.add(toResponse(registry.getServerInfo(datasourceKey)));
        }
        return RedisDatasourceListResponse.builder().items(items).build();
    }

    @Override
    public RedisDatasourceResponse getSummary(String datasourceKey) {
        requireDatasource(datasourceKey);
        return toResponse(registry.getServerInfo(datasourceKey));
    }

    @Override
    public RedisKeyMetadataResponse getKeyMetadata(RedisKeyMetadataRequest request) {
        StringRedisTemplate template = template(request.getDatasourceKey());
        Boolean exists = template.hasKey(request.getKey());
        if (!Boolean.TRUE.equals(exists)) {
            return RedisKeyMetadataResponse.builder().exists(false).ttlState("NOT_FOUND").build();
        }
        DataType dataType = template.type(request.getKey());
        Long ttlSeconds = template.getExpire(request.getKey());
        if (ttlSeconds == null || ttlSeconds == -1L) {
            return RedisKeyMetadataResponse.builder().exists(true).dataType(dataTypeName(dataType))
                    .ttlState("PERSISTENT").build();
        }
        if (ttlSeconds < 0L) {
            return RedisKeyMetadataResponse.builder().exists(false).ttlState("NOT_FOUND").build();
        }
        return RedisKeyMetadataResponse.builder().exists(true).dataType(dataTypeName(dataType))
                .ttlState("EXPIRING").ttlSeconds(ttlSeconds).build();
    }

    @Override
    public RedisKeyReadResponse readKey(RedisKeyReadRequest request) {
        StringRedisTemplate template = template(request.getDatasourceKey());
        DataType dataType = template.type(request.getKey());
        if (dataType == null || dataType == DataType.NONE) {
            throw new MiddlewareOpsException(HttpStatus.NOT_FOUND, "目标 Redis key 不存在");
        }
        String type = dataTypeName(dataType);
        if (TYPE_STRING.equals(type)) {
            return RedisKeyReadResponse.builder().dataType(type)
                    .stringValue(limitValue(template.opsForValue().get(request.getKey()))).build();
        }
        if (TYPE_HASH.equals(type)) {
            return readHash(template, request);
        }
        if (TYPE_LIST.equals(type)) {
            return readList(template, request);
        }
        if (TYPE_SET.equals(type)) {
            return readSet(template, request);
        }
        if (TYPE_ZSET.equals(type)) {
            return readZSet(template, request);
        }
        if (TYPE_STREAM.equals(type)) {
            return readStream(template, request);
        }
        throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "当前 Redis key 类型不支持读取");
    }

    @Override
    public RedisKeyDiscoveryResponse discoverKeys(RedisKeyDiscoveryRequest request) {
        requireDatasource(request.getDatasourceKey());
        long deadlineNanos = operationDeadlineNanos();
        RedisConnectionFactory factory = registry.getConnectionFactory(request.getDatasourceKey());
        try {
            RedisClusterConnection clusterConnection = clusterConnection(factory);
            if (clusterConnection != null) {
                return discoverCluster(clusterConnection, request, deadlineNanos);
            }
            return discoverStandalone(factory.getConnection(), request, deadlineNanos);
        } catch (MiddlewareOpsException e) {
            throw e;
        } catch (RuntimeException e) {
            throw unavailable();
        }
    }

    private RedisClusterConnection clusterConnection(RedisConnectionFactory factory) {
        try {
            return factory.getClusterConnection();
        } catch (InvalidDataAccessApiUsageException e) {
            return null;
        } catch (RuntimeException e) {
            throw unavailable();
        }
    }

    private RedisKeyDiscoveryResponse discoverStandalone(RedisConnection connection, RedisKeyDiscoveryRequest request,
                                                         long deadlineNanos) {
        Cursor<byte[]> cursor = connection.scan(discoveryScanOptions(request.getPrefix()));
        try {
            return scan(cursor, request, deadlineNanos);
        } finally {
            try {
                closeCursor(cursor);
            } finally {
                connection.close();
            }
        }
    }

    private RedisKeyDiscoveryResponse discoverCluster(RedisClusterConnection connection, RedisKeyDiscoveryRequest request,
                                                      long deadlineNanos) {
        try {
            List<RedisClusterNode> masters = masterNodes(connection.clusterGetNodes());
            if (masters.isEmpty()) {
                throw unavailable();
            }
            DiscoveryAccumulator accumulator = new DiscoveryAccumulator(request.getSize(), maxValueLength);
            for (RedisClusterNode master : masters) {
                checkDeadline(deadlineNanos);
                Cursor<byte[]> cursor = connection.scan(master, discoveryScanOptions(request.getPrefix()));
                try {
                    RedisKeyDiscoveryResponse response = scan(cursor, request, deadlineNanos, accumulator);
                    if (response != null) {
                        return response;
                    }
                } finally {
                    closeCursor(cursor);
                }
            }
            return accumulator.response(STOP_REASON_COMPLETED);
        } finally {
            connection.close();
        }
    }

    private List<RedisClusterNode> masterNodes(Iterable<RedisClusterNode> nodes) {
        List<RedisClusterNode> masters = new ArrayList<>();
        if (nodes == null) {
            return masters;
        }
        for (RedisClusterNode node : nodes) {
            if (node != null && node.getFlags() != null && node.getFlags().contains(RedisClusterNode.Flag.MASTER)) {
                masters.add(node);
            }
        }
        return masters;
    }

    private RedisKeyDiscoveryResponse scan(Cursor<byte[]> cursor, RedisKeyDiscoveryRequest request, long deadlineNanos) {
        DiscoveryAccumulator accumulator = new DiscoveryAccumulator(request.getSize(), maxValueLength);
        RedisKeyDiscoveryResponse response = scan(cursor, request, deadlineNanos, accumulator);
        return response == null ? accumulator.response(STOP_REASON_COMPLETED) : response;
    }

    private RedisKeyDiscoveryResponse scan(Cursor<byte[]> cursor, RedisKeyDiscoveryRequest request, long deadlineNanos,
                                           DiscoveryAccumulator accumulator) {
        while (true) {
            checkDeadline(deadlineNanos);
            if (!cursor.hasNext()) {
                return null;
            }
            if (accumulator.isResultLimitReached()) {
                return accumulator.response(STOP_REASON_RESULT_LIMIT);
            }
            if (!accumulator.canObserve()) {
                return accumulator.response(STOP_REASON_WORK_LIMIT);
            }
            checkDeadline(deadlineNanos);
            byte[] key = cursor.next();
            accumulator.observe();
            String decoded = decodeKey(key);
            if (decoded == null) {
                return accumulator.response(STOP_REASON_UNSUPPORTED_KEY_ENCODING);
            }
            if (!decoded.startsWith(request.getPrefix()) || accumulator.contains(decoded)) {
                continue;
            }
            if (!accumulator.canAdd(decoded)) {
                return accumulator.response(STOP_REASON_RESPONSE_LIMIT);
            }
            accumulator.add(decoded);
        }
    }

    private ScanOptions discoveryScanOptions(String prefix) {
        return ScanOptions.scanOptions().match(prefix + "*").count(DISCOVERY_SCAN_COUNT).build();
    }

    private void closeCursor(Cursor<?> cursor) {
        try {
            cursor.close();
        } catch (Exception e) {
            throw unavailable();
        }
    }

    private String decodeKey(byte[] value) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(value)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private long operationDeadlineNanos() {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(deadlineMillis);
    }

    private void checkDeadline(long deadlineNanos) {
        if (System.nanoTime() >= deadlineNanos) {
            throw new MiddlewareOpsException(HttpStatus.GATEWAY_TIMEOUT, "Redis 运维查询已超时");
        }
    }

    private MiddlewareOpsException unavailable() {
        return new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "Redis 运维查询暂不可用");
    }

    private RedisKeyReadResponse readHash(StringRedisTemplate template, RedisKeyReadRequest request) {
        List<RedisKeyReadResponse.FieldValue> entries = new ArrayList<>();
        if (request.getField() != null && !request.getField().trim().isEmpty()) {
            String value = (String) template.opsForHash().get(request.getKey(), request.getField());
            if (value != null) {
                entries.add(RedisKeyReadResponse.FieldValue.builder().field(limitValue(request.getField()))
                        .value(limitValue(value)).build());
            }
            return RedisKeyReadResponse.builder().dataType(TYPE_HASH).hashEntries(entries).build();
        }
        Cursor<Map.Entry<Object, Object>> cursor = template.opsForHash().scan(request.getKey(),
                ScanOptions.scanOptions().count(request.getSize()).build());
        try {
            while (cursor.hasNext() && entries.size() < request.getSize()) {
                Map.Entry<Object, Object> entry = cursor.next();
                entries.add(RedisKeyReadResponse.FieldValue.builder().field(limitValue(String.valueOf(entry.getKey())))
                        .value(limitValue(entry.getValue() == null ? null : String.valueOf(entry.getValue()))).build());
            }
        } finally {
            closeCursor(cursor);
        }
        return RedisKeyReadResponse.builder().dataType(TYPE_HASH).hashEntries(entries).build();
    }

    private RedisKeyReadResponse readList(StringRedisTemplate template, RedisKeyReadRequest request) {
        long end = request.getOffset() + request.getSize() - 1L;
        List<String> values = template.opsForList().range(request.getKey(), request.getOffset(), end);
        List<RedisKeyReadResponse.IndexValue> entries = new ArrayList<>();
        if (values != null) {
            for (int index = 0; index < values.size(); index++) {
                entries.add(RedisKeyReadResponse.IndexValue.builder().index(request.getOffset() + index)
                        .value(limitValue(values.get(index))).build());
            }
        }
        return RedisKeyReadResponse.builder().dataType(TYPE_LIST).listEntries(entries).build();
    }

    private RedisKeyReadResponse readSet(StringRedisTemplate template, RedisKeyReadRequest request) {
        if (request.getOffset() != 0) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "Set 不支持偏移分页");
        }
        List<String> values = template.opsForSet().randomMembers(request.getKey(), request.getSize());
        List<String> members = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                members.add(limitValue(value));
            }
        }
        return RedisKeyReadResponse.builder().dataType(TYPE_SET).setMembers(members).build();
    }

    private RedisKeyReadResponse readZSet(StringRedisTemplate template, RedisKeyReadRequest request) {
        Set<ZSetOperations.TypedTuple<String>> values = template.opsForZSet().rangeWithScores(request.getKey(),
                request.getOffset(), request.getOffset() + request.getSize() - 1L);
        List<RedisKeyReadResponse.ScoreMember> entries = new ArrayList<>();
        if (values != null) {
            for (ZSetOperations.TypedTuple<String> value : values) {
                entries.add(RedisKeyReadResponse.ScoreMember.builder().member(limitValue(value.getValue()))
                        .score(value.getScore()).build());
            }
        }
        return RedisKeyReadResponse.builder().dataType(TYPE_ZSET).zSetEntries(entries).build();
    }

    private RedisKeyReadResponse readStream(StringRedisTemplate template, RedisKeyReadRequest request) {
        if (request.getOffset() != 0) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "Stream 不支持偏移分页");
        }
        List<MapRecord<String, Object, Object>> records = template.opsForStream().read(
                StreamReadOptions.empty().count(request.getSize()), StreamOffset.fromStart(request.getKey()));
        List<RedisKeyReadResponse.StreamEntry> entries = new ArrayList<>();
        if (records != null) {
            for (MapRecord<String, Object, Object> record : records) {
                entries.add(RedisKeyReadResponse.StreamEntry.builder().id(record.getId().getValue())
                        .fields(limitFields(toStringFields(record.getValue()))).build());
            }
        }
        return RedisKeyReadResponse.builder().dataType(TYPE_STREAM).streamEntries(entries).build();
    }

    private Map<String, String> toStringFields(Map<Object, Object> fields) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : fields.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
        }
        return result;
    }

    private Map<String, String> limitFields(Map<String, String> fields) {
        Map<String, String> result = new LinkedHashMap<>();
        int remaining = maxValueLength;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (remaining <= 0) {
                break;
            }
            String field = limitValue(entry.getKey(), remaining);
            remaining -= valueLength(field);
            String value = limitValue(entry.getValue(), remaining);
            result.put(field, value);
            remaining -= valueLength(value);
        }
        return result;
    }

    private String limitValue(String value) {
        return limitValue(value, maxValueLength);
    }

    private int valueLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String limitValue(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }

    private StringRedisTemplate template(String datasourceKey) {
        requireDatasource(datasourceKey);
        return registry.getStringRedisTemplate(datasourceKey);
    }

    private void requireDatasource(String datasourceKey) {
        if (!registry.containsDatasource(datasourceKey)) {
            throw new MiddlewareOpsException(HttpStatus.NOT_FOUND, "目标数据源不存在");
        }
    }

    private String dataTypeName(DataType dataType) {
        if (dataType == null || dataType == DataType.NONE) {
            return "unknown";
        }
        return dataType.code();
    }

    private RedisDatasourceResponse toResponse(RedisServerInfo info) {
        return RedisDatasourceResponse.builder().datasourceKey(info.getDatasourceKey()).versionKnown(info.isKnown())
                .version(info.getVersion() == null ? null : info.getVersion().toString())
                .deploymentMode(info.getRedisMode()).build();
    }

    private static class DiscoveryAccumulator {

        private final int limit;
        private final int responseLengthLimit;
        private final Set<String> items = new LinkedHashSet<>();
        private int observedEntries;
        private int responseLength;

        private DiscoveryAccumulator(int limit, int responseLengthLimit) {
            this.limit = limit;
            this.responseLengthLimit = responseLengthLimit;
        }

        private boolean canObserve() {
            return observedEntries < DISCOVERY_MAX_OBSERVED_ENTRIES;
        }

        private void observe() {
            observedEntries++;
        }

        private boolean contains(String key) {
            return items.contains(key);
        }

        private boolean canAdd(String key) {
            return key.length() <= responseLengthLimit - responseLength;
        }

        private void add(String key) {
            items.add(key);
            responseLength += key.length();
        }

        private boolean isResultLimitReached() {
            return items.size() >= limit;
        }

        private RedisKeyDiscoveryResponse response(String stopReason) {
            boolean traversalComplete = STOP_REASON_COMPLETED.equals(stopReason);
            return RedisKeyDiscoveryResponse.builder().items(new ArrayList<>(items)).limit(limit)
                    .returned(items.size()).truncated(!traversalComplete).traversalComplete(traversalComplete)
                    .stopReason(stopReason).build();
        }
    }
}
