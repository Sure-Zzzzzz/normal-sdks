package io.github.surezzzzzz.sdk.sensitive.ip.test.cases;

import io.github.surezzzzzz.sdk.sensitive.ip.helper.SimpleIpv6SensitiveHelper;
import io.github.surezzzzzz.sdk.sensitive.ip.test.SimpleIpSensitiveTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * IPv6 脱敏端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIpSensitiveTestApplication.class)
class Ipv6SensitiveHelperTest {

    @Autowired
    private SimpleIpv6SensitiveHelper helper;

    @Test
    void testDefaultMask() {
        String ip = "2001:db8::1";
        String masked = helper.mask(ip);
        log.info("======================================");
        log.info("IPv6 默认脱敏测试");
        log.info("原始IP: {}", ip);
        log.info("脱敏后: {}", masked);
        log.info("======================================");
    }

    @Test
    void testFullFormatIpv6() {
        String ip = "2001:0db8:85a3:0000:0000:8a2e:0370:7334";
        String masked = helper.mask(ip);
        log.info("======================================");
        log.info("IPv6 完整格式脱敏测试");
        log.info("原始IP: {}", ip);
        log.info("脱敏后: {}", masked);
        log.info("======================================");
    }

    @Test
    void testCompressedIpv6() {
        log.info("======================================");
        log.info("IPv6 压缩格式脱敏测试");

        String ip1 = "::1";
        String masked1 = helper.mask(ip1);
        log.info("{} → {}", ip1, masked1);

        String ip2 = "fe80::";
        String masked2 = helper.mask(ip2);
        log.info("{} → {}", ip2, masked2);

        String ip3 = "2001:db8::8a2e:370:7334";
        String masked3 = helper.mask(ip3);
        log.info("{} → {}", ip3, masked3);

        log.info("======================================");
    }

    @Test
    void testCustomPositions() {
        String ip = "2001:db8::1";

        log.info("======================================");
        log.info("IPv6 自定义位置脱敏测试");
        log.info("原始IP: {}", ip);

        String masked1 = helper.mask(ip, new int[]{1, 2});
        log.info("脱敏 [1,2]: {}", masked1);

        String masked2 = helper.mask(ip, new int[]{1, 8});
        log.info("脱敏 [1,8]: {}", masked2);

        String masked3 = helper.mask(ip, new int[]{4, 5, 6});
        log.info("脱敏 [4,5,6]: {}", masked3);

        log.info("======================================");
    }

    @Test
    void testCustomMaskChar() {
        String ip = "2001:db8::1";
        String masked = helper.mask(ip, new int[]{5, 6, 7, 8}, "XXXX");

        log.info("======================================");
        log.info("IPv6 自定义掩码字符测试");
        log.info("原始IP: {}", ip);
        log.info("脱敏后(XXXX): {}", masked);
        log.info("======================================");
    }

    @Test
    void testCidrMask() {
        log.info("======================================");
        log.info("IPv6 CIDR 脱敏测试");

        String cidr1 = "2001:db8::/32";
        String masked1 = helper.maskCidr(cidr1);
        log.info("{} → {}", cidr1, masked1);

        String cidr2 = "2001:db8:85a3::/48";
        String masked2 = helper.maskCidr(cidr2);
        log.info("{} → {}", cidr2, masked2);

        String cidr3 = "2001:db8:85a3:8d3::/64";
        String masked3 = helper.maskCidr(cidr3);
        log.info("{} → {}", cidr3, masked3);

        String cidr4 = "2001:db8::1/128";
        String masked4 = helper.maskCidr(cidr4);
        log.info("{} → {} (使用默认策略)", cidr4, masked4);

        log.info("======================================");
    }
}
