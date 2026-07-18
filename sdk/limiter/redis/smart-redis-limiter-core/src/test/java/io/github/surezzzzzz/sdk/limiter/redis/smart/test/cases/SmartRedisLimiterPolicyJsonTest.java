package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterManagementEventPayload;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SmartRedisLimiter 动态策略 JSON 契约测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterPolicyJsonTest {

    private static final String SNAPSHOT_JSON = "{"
            + "\"schemaVersion\":\"1\","
            + "\"serviceCode\":\"test-service\","
            + "\"revision\":3,"
            + "\"publishedAt\":\"2026-07-17T10:00:00Z\","
            + "\"policies\":[{"
            + "\"key\":{\"serviceCode\":\"test-service\",\"resourceCode\":\"test-resource\",\"subject\":\"test-subject\"},"
            + "\"limits\":["
            + "{\"count\":100,\"window\":1,\"unit\":\"MINUTES\"},"
            + "{\"count\":10,\"window\":1,\"unit\":\"SECONDS\"}"
            + "]}]}";

    private static final String MANAGEMENT_CREATE_JSON = "{"
            + "\"operation\":\"CREATE\","
            + "\"policyKey\":{\"serviceCode\":\"test-service\",\"resourceCode\":\"test-resource\",\"subject\":\"test-subject\"},"
            + "\"afterPolicy\":{"
            + "\"key\":{\"serviceCode\":\"test-service\",\"resourceCode\":\"test-resource\",\"subject\":\"test-subject\"},"
            + "\"limits\":[{\"count\":10,\"window\":1,\"unit\":\"MINUTES\"}]},"
            + "\"afterEnabled\":true,"
            + "\"revision\":3,"
            + "\"operator\":\"admin\","
            + "\"occurredAt\":\"2026-07-17T10:00:00Z\","
            + "\"attributes\":{\"tags\":[\"tag-1\"]}"
            + "}";

    @Test
    public void testSnapshotJsonRoundTrip() throws Exception {
        log.info("开始测试动态策略快照 JSON 双向转换");
        ObjectMapper objectMapper = objectMapper();
        SmartRedisLimiterPolicySnapshot snapshot = objectMapper.readValue(
                SNAPSHOT_JSON, SmartRedisLimiterPolicySnapshot.class);

        assertEquals(3L, snapshot.getRevision());
        assertEquals(1L, snapshot.getPolicies().get(0).getLimits().get(0).getWindowSeconds());
        assertEquals(60L, snapshot.getPolicies().get(0).getLimits().get(1).getWindowSeconds());

        JsonNode serialized = objectMapper.readTree(objectMapper.writeValueAsString(snapshot));
        assertFalse(serialized.at("/policies/0/limits/0").has("windowSeconds"));
        assertEquals("SECONDS", serialized.at("/policies/0/limits/0/unit").asText());
        assertEquals("2026-07-17T10:00:00Z", serialized.get("publishedAt").asText());

        SmartRedisLimiterPolicySnapshot roundTrip = objectMapper.treeToValue(
                serialized, SmartRedisLimiterPolicySnapshot.class);
        assertEquals(snapshot, roundTrip, "JSON 双向转换后快照应保持一致");
        log.info("动态策略快照 JSON 双向转换测试通过");
    }

    @Test
    public void testUnknownTimeUnitFails() {
        log.info("开始测试未知时间单位反序列化失败");
        String invalidJson = SNAPSHOT_JSON.replace("MINUTES", "UNKNOWN");
        assertThrows(Exception.class,
                () -> objectMapper().readValue(invalidJson, SmartRedisLimiterPolicySnapshot.class),
                "未知时间单位应导致反序列化失败");
        log.info("未知时间单位反序列化失败测试通过");
    }

    @Test
    public void testRequiredSnapshotFieldsFailWhenMissingOrNull() {
        log.info("开始测试快照必填字段缺失或为空");
        String missingRevision = SNAPSHOT_JSON.replace("\"revision\":3,", "");
        String nullRevision = SNAPSHOT_JSON.replace("\"revision\":3", "\"revision\":null");
        String negativeRevision = SNAPSHOT_JSON.replace("\"revision\":3", "\"revision\":-1");
        String missingSchemaVersion = SNAPSHOT_JSON.replace("\"schemaVersion\":\"1\",", "");
        String missingPublishedAt = SNAPSHOT_JSON.replace(
                "\"publishedAt\":\"2026-07-17T10:00:00Z\",", "");
        String missingPolicies = SNAPSHOT_JSON.replace(
                ",\"policies\":[{\"key\"", ",\"ignoredPolicies\":[{\"key\"");

        ObjectMapper objectMapper = objectMapper();
        assertThrows(Exception.class,
                () -> objectMapper.readValue(missingRevision, SmartRedisLimiterPolicySnapshot.class),
                "缺少 revision 应导致反序列化失败");
        assertThrows(Exception.class,
                () -> objectMapper.readValue(nullRevision, SmartRedisLimiterPolicySnapshot.class),
                "revision 为 null 应导致反序列化失败");
        assertThrows(Exception.class,
                () -> objectMapper.readValue(negativeRevision, SmartRedisLimiterPolicySnapshot.class),
                "revision 为负数应导致反序列化失败");
        assertThrows(Exception.class,
                () -> objectMapper.readValue(missingSchemaVersion, SmartRedisLimiterPolicySnapshot.class),
                "缺少 schemaVersion 应导致反序列化失败");
        assertThrows(Exception.class,
                () -> objectMapper.readValue(missingPublishedAt, SmartRedisLimiterPolicySnapshot.class),
                "缺少 publishedAt 应导致反序列化失败");
        assertThrows(Exception.class,
                () -> objectMapper.readValue(missingPolicies, SmartRedisLimiterPolicySnapshot.class),
                "缺少 policies 应导致反序列化失败");
        log.info("快照必填字段缺失或为空测试通过");
    }

    @Test
    public void testManagementPayloadJsonContract() throws Exception {
        log.info("开始测试管理事件 JSON 契约");
        ObjectMapper objectMapper = objectMapper();
        SmartRedisLimiterManagementEventPayload payload = objectMapper.readValue(
                MANAGEMENT_CREATE_JSON, SmartRedisLimiterManagementEventPayload.class);
        assertEquals("CREATE", payload.getOperation().getCode(), "管理操作编码应保持稳定");
        assertEquals(3L, payload.getRevision(), "管理事件 revision 应正确反序列化");
        assertEquals("admin", payload.getOperator(), "管理事件 operator 应正确反序列化");
        assertEquals("tag-1", ((List<?>) payload.getAttributes().get("tags")).get(0),
                "管理事件嵌套 attributes 应正确反序列化");

        JsonNode serialized = objectMapper.readTree(objectMapper.writeValueAsString(payload));
        assertEquals("CREATE", serialized.get("operation").asText(), "管理操作序列化编码应稳定");
        assertEquals(3L, serialized.get("revision").asLong(), "管理事件 revision 序列化应稳定");

        assertManagementJsonFails(objectMapper,
                MANAGEMENT_CREATE_JSON.replace("\"revision\":3,", ""),
                "缺少 revision 应失败");
        assertManagementJsonFails(objectMapper,
                MANAGEMENT_CREATE_JSON.replace("\"revision\":3", "\"revision\":null"),
                "revision 为 null 应失败");
        assertManagementJsonFails(objectMapper,
                MANAGEMENT_CREATE_JSON.replace("\"operator\":\"admin\",", ""),
                "缺少 operator 应失败");
        assertManagementJsonFails(objectMapper,
                MANAGEMENT_CREATE_JSON.replace("\"occurredAt\":\"2026-07-17T10:00:00Z\",", ""),
                "缺少 occurredAt 应失败");
        assertManagementJsonFails(objectMapper,
                MANAGEMENT_CREATE_JSON.replace("\"operation\":\"CREATE\"", "\"operation\":\"UNKNOWN\""),
                "未知 operation 应失败");
        assertManagementJsonFails(objectMapper,
                MANAGEMENT_CREATE_JSON.replace("\"afterEnabled\":true", "\"afterEnabled\":false")
                        .replace("\"afterPolicy\":{", "\"beforePolicy\":{"),
                "CREATE 状态矩阵非法应失败");
        log.info("管理事件 JSON 契约测试通过");
    }

    private void assertManagementJsonFails(ObjectMapper objectMapper, String json, String message) {
        assertThrows(Exception.class,
                () -> objectMapper.readValue(json, SmartRedisLimiterManagementEventPayload.class),
                message);
    }

    private ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }
}
