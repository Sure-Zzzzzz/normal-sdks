package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.policy.json.JacksonSmartRedisLimiterPolicyJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 远程策略独立 JSON 编解码测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterPolicyJsonCodecTest {

    @Test
    public void testDecodeValidSnapshot() {
        String json = "{\"schemaVersion\":\"1\",\"serviceCode\":\"test-service\","
                + "\"revision\":1,\"publishedAt\":\"2026-07-18T00:00:00Z\","
                + "\"policies\":[{\"key\":{\"serviceCode\":\"test-service\","
                + "\"resourceCode\":\"query\",\"subject\":\"tenant-a\"},"
                + "\"limits\":[{\"count\":3,\"window\":1,\"unit\":\"SECONDS\"}]}]}";

        SmartRedisLimiterPolicySnapshot snapshot = new JacksonSmartRedisLimiterPolicyJsonCodec().decode(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        log.info("解析快照: serviceCode={}, revision={}", snapshot.getServiceCode(), snapshot.getRevision());
        assertEquals("test-service", snapshot.getServiceCode(), "服务编码应精确解析");
        assertEquals(1L, snapshot.getRevision(), "版本应精确解析");
    }

    @Test
    public void testUnknownFieldFailsStrictly() {
        String json = "{\"schemaVersion\":\"1\",\"serviceCode\":\"test-service\","
                + "\"revision\":1,\"publishedAt\":\"2026-07-18T00:00:00Z\","
                + "\"policies\":[],\"unknownField\":true}";

        log.info("验证未知字段严格失败");
        assertThrows(RuntimeException.class,
                () -> new JacksonSmartRedisLimiterPolicyJsonCodec().decode(
                        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))),
                "同 schema 未知字段必须拒绝");
    }
}
