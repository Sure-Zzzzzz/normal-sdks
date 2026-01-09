package io.github.surezzzzzz.sdk.sensitive.ip.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.sensitive.ip.annotation.SimpleIpSensitize;
import io.github.surezzzzzz.sdk.sensitive.ip.test.SimpleIpSensitiveTestApplication;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Jackson 序列化器端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIpSensitiveTestApplication.class)
class JacksonSerializerTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testDefaultAnnotation() throws Exception {
        AccessLog accessLog = new AccessLog("192.168.1.1", "2001:db8::1", "user123");
        String json = objectMapper.writeValueAsString(accessLog);

        log.info("======================================");
        log.info("默认注解脱敏测试");
        log.info("原始对象: {}", accessLog);
        log.info("序列化后: {}", json);
        log.info("======================================");
    }

    @Test
    void testCustomPositionAnnotation() throws Exception {
        ServerConfig config = new ServerConfig("10.0.0.1", "fe80::1", 8080);
        String json = objectMapper.writeValueAsString(config);

        log.info("======================================");
        log.info("自定义位置注解脱敏测试");
        log.info("原始对象: {}", config);
        log.info("序列化后: {}", json);
        log.info("======================================");
    }

    @Test
    void testCustomMaskCharAnnotation() throws Exception {
        NetworkDevice device = new NetworkDevice("device-001", "172.16.0.1", "2001:db8:85a3::1");
        String json = objectMapper.writeValueAsString(device);

        log.info("======================================");
        log.info("自定义掩码字符注解脱敏测试");
        log.info("原始对象: {}", device);
        log.info("序列化后: {}", json);
        log.info("======================================");
    }

    @Test
    void testMixedAnnotations() throws Exception {
        MixedConfig config = new MixedConfig(
                "192.168.1.100",
                "10.20.30.40",
                "2001:db8::1",
                "fe80::1"
        );
        String json = objectMapper.writeValueAsString(config);

        log.info("======================================");
        log.info("混合注解脱敏测试");
        log.info("原始对象: {}", config);
        log.info("序列化后: {}", json);
        log.info("======================================");
    }

    @Test
    void testNullIpHandling() throws Exception {
        AccessLog accessLog = new AccessLog(null, null, "user123");
        String json = objectMapper.writeValueAsString(accessLog);

        log.info("======================================");
        log.info("NULL IP 处理测试");
        log.info("原始对象: {}", accessLog);
        log.info("序列化后: {}", json);
        log.info("======================================");
    }

    // ============== 测试用数据类 ==============

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AccessLog {
        @SimpleIpSensitize
        private String clientIp;

        @SimpleIpSensitize
        private String serverIp;

        private String userId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ServerConfig {
        @SimpleIpSensitize(mask = {1, 2})
        private String ipv4Address;

        @SimpleIpSensitize(mask = {1, 2, 3, 4})
        private String ipv6Address;

        private int port;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class NetworkDevice {
        private String deviceId;

        @SimpleIpSensitize(mask = {2, 3}, maskChar = "X")
        private String managementIp;

        @SimpleIpSensitize(mask = {1, 2}, maskChar = "XXXX")
        private String monitorIp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class MixedConfig {
        @SimpleIpSensitize
        private String defaultMaskIp;

        @SimpleIpSensitize(mask = {1})
        private String customPositionIp;

        @SimpleIpSensitize(maskChar = "****")
        private String customCharIp;

        @SimpleIpSensitize(mask = {1, 8}, maskChar = "####")
        private String fullCustomIp;
    }
}
