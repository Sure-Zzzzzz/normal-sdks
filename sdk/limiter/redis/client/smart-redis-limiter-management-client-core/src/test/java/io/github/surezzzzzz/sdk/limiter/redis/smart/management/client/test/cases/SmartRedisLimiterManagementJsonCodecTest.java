package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.support.JacksonSmartRedisLimiterManagementJsonCodec;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicySnapshot;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 策略快照独立 JSON 编解码测试（v1 + v2 双协议）
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterManagementJsonCodecTest {

    private final JacksonSmartRedisLimiterManagementJsonCodec codec =
            new JacksonSmartRedisLimiterManagementJsonCodec();

    private static InputStream stream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testDecodeValidV1Snapshot() {
        String json = "{\"schemaVersion\":\"1\",\"serviceCode\":\"test-service\","
                + "\"revision\":1,\"publishedAt\":\"2026-07-18T00:00:00Z\","
                + "\"policies\":[{\"key\":{\"serviceCode\":\"test-service\","
                + "\"resourceCode\":\"query\",\"subject\":\"tenant-a\"},"
                + "\"limits\":[{\"count\":3,\"window\":1,\"unit\":\"SECONDS\"}]}]}";
        SmartRedisLimiterPolicySnapshot snapshot = codec.decodePolicy(stream(json));
        log.info("解析 v1 快照: serviceCode={}, revision={}", snapshot.getServiceCode(), snapshot.getRevision());
        assertEquals("test-service", snapshot.getServiceCode(), "服务编码应精确解析");
        assertEquals(1L, snapshot.getRevision(), "版本应精确解析");
    }

    @Test
    public void testDecodeValidV2TypedSnapshot() {
        String json = "{\"schemaVersion\":\"2\",\"serviceCode\":\"test-service\","
                + "\"policyEpoch\":1,\"revision\":5,\"publishedAt\":\"2026-10-06T00:00:00Z\","
                + "\"rules\":[{\"key\":{\"serviceCode\":\"test-service\",\"resourceCode\":\"query\","
                + "\"dimension\":\"CUSTOMER\",\"selector\":\"EXACT\",\"namespace\":\"customer-root\","
                + "\"objectId\":\"cust-1\"},\"enabled\":true,"
                + "\"limits\":[{\"count\":100,\"window\":1,\"unit\":\"MINUTES\"}]}]}";
        SmartRedisLimiterTypedPolicySnapshot snapshot = codec.decodeTypedPolicy(stream(json));
        log.info("解析 v2 快照: serviceCode={}, epoch={}, revision={}",
                snapshot.getServiceCode(), snapshot.getPolicyEpoch(), snapshot.getRevision());
        assertEquals("test-service", snapshot.getServiceCode());
        assertEquals(1L, snapshot.getPolicyEpoch());
        assertEquals(5L, snapshot.getRevision());
        assertEquals(1, snapshot.getRules().size());
    }

    @Test
    public void testUnknownFieldFailsStrictly() {
        String json = "{\"schemaVersion\":\"1\",\"serviceCode\":\"test-service\","
                + "\"revision\":1,\"publishedAt\":\"2026-07-18T00:00:00Z\","
                + "\"policies\":[],\"unexpected\":\"x\"}";
        assertThrows(Exception.class, () -> codec.decodePolicy(stream(json)), "未知字段必须严格拒绝");
    }

    @Test
    public void testV1BodyRejectedByTypedDecoderAndViceVersa() {
        String v1 = "{\"schemaVersion\":\"1\",\"serviceCode\":\"s\",\"revision\":1,"
                + "\"publishedAt\":\"2026-07-18T00:00:00Z\",\"policies\":[]}";
        String v2 = "{\"schemaVersion\":\"2\",\"serviceCode\":\"s\",\"policyEpoch\":1,"
                + "\"revision\":1,\"publishedAt\":\"2026-10-06T00:00:00Z\",\"rules\":[]}";
        assertThrows(Exception.class, () -> codec.decodeTypedPolicy(stream(v1)), "v1 内容不得解析为 v2");
        assertThrows(Exception.class, () -> codec.decodePolicy(stream(v2)), "v2 内容不得解析为 v1");
        assertTrue(true, "双协议严格隔离");
    }
}
