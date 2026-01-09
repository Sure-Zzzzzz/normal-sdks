package io.github.surezzzzzz.sdk.sensitive.ip.test.cases;

import io.github.surezzzzzz.sdk.sensitive.ip.helper.SimpleIpv4SensitiveHelper;
import io.github.surezzzzzz.sdk.sensitive.ip.test.SimpleIpSensitiveTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * IPv4 脱敏端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIpSensitiveTestApplication.class)
class Ipv4SensitiveHelperTest {

    @Autowired
    private SimpleIpv4SensitiveHelper helper;

    @Test
    void testDefaultMask() {
        String ip = "192.168.1.1";
        String masked = helper.mask(ip);
        log.info("======================================");
        log.info("IPv4 默认脱敏测试");
        log.info("原始IP: {}", ip);
        log.info("脱敏后: {}", masked);
        log.info("======================================");
    }

    @Test
    void testCustomPositions() {
        String ip = "192.168.1.1";

        log.info("======================================");
        log.info("IPv4 自定义位置脱敏测试");
        log.info("原始IP: {}", ip);

        String masked1 = helper.mask(ip, new int[]{1, 2});
        log.info("脱敏 [1,2]: {}", masked1);

        String masked2 = helper.mask(ip, new int[]{2, 3});
        log.info("脱敏 [2,3]: {}", masked2);

        String masked3 = helper.mask(ip, new int[]{1, 4});
        log.info("脱敏 [1,4]: {}", masked3);

        log.info("======================================");
    }

    @Test
    void testCustomMaskChar() {
        String ip = "10.0.0.1";
        String masked = helper.mask(ip, new int[]{1}, "X");

        log.info("======================================");
        log.info("IPv4 自定义掩码字符测试");
        log.info("原始IP: {}", ip);
        log.info("脱敏后(X): {}", masked);
        log.info("======================================");
    }

    @Test
    void testCidrMask() {
        log.info("======================================");
        log.info("CIDR 脱敏测试");

        String cidr1 = "10.0.0.0/8";
        String masked1 = helper.maskCidr(cidr1);
        log.info("{} → {}", cidr1, masked1);

        String cidr2 = "192.168.0.0/16";
        String masked2 = helper.maskCidr(cidr2);
        log.info("{} → {}", cidr2, masked2);

        String cidr3 = "192.168.1.0/24";
        String masked3 = helper.maskCidr(cidr3);
        log.info("{} → {}", cidr3, masked3);

        String cidr4 = "192.168.1.128/25";
        String masked4 = helper.maskCidr(cidr4);
        log.info("{} → {}", cidr4, masked4);

        String cidr5 = "192.168.1.1/32";
        String masked5 = helper.maskCidr(cidr5);
        log.info("{} → {} (使用默认策略)", cidr5, masked5);

        log.info("======================================");
    }
}
