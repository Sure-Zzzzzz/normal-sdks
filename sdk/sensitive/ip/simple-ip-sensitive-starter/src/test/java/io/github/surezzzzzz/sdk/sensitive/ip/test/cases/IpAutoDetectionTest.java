package io.github.surezzzzzz.sdk.sensitive.ip.test.cases;

import io.github.surezzzzzz.sdk.sensitive.ip.helper.SimpleIpSensitiveHelper;
import io.github.surezzzzzz.sdk.sensitive.ip.test.SimpleIpSensitiveTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * IP 自动识别脱敏端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIpSensitiveTestApplication.class)
class IpAutoDetectionTest {

    @Autowired
    private SimpleIpSensitiveHelper helper;

    @Test
    void testAutoDetectIpv4() {
        log.info("======================================");
        log.info("自动识别 IPv4 测试");

        String ip1 = "192.168.1.1";
        String masked1 = helper.mask(ip1);
        log.info("{} → {}", ip1, masked1);

        String ip2 = "10.0.0.1";
        String masked2 = helper.mask(ip2);
        log.info("{} → {}", ip2, masked2);

        String ip3 = "172.16.0.1";
        String masked3 = helper.mask(ip3);
        log.info("{} → {}", ip3, masked3);

        log.info("======================================");
    }

    @Test
    void testAutoDetectIpv6() {
        log.info("======================================");
        log.info("自动识别 IPv6 测试");

        String ip1 = "2001:db8::1";
        String masked1 = helper.mask(ip1);
        log.info("{} → {}", ip1, masked1);

        String ip2 = "::1";
        String masked2 = helper.mask(ip2);
        log.info("{} → {}", ip2, masked2);

        String ip3 = "fe80::1";
        String masked3 = helper.mask(ip3);
        log.info("{} → {}", ip3, masked3);

        log.info("======================================");
    }

    @Test
    void testMixedIpAddresses() {
        log.info("======================================");
        log.info("混合 IP 地址自动识别测试");

        String[] ips = {
                "192.168.1.1",
                "2001:db8::1",
                "10.0.0.1",
                "::1",
                "172.16.0.1",
                "fe80::1"
        };

        for (String ip : ips) {
            String masked = helper.mask(ip);
            log.info("{} → {}", ip, masked);
        }

        log.info("======================================");
    }

    @Test
    void testCustomPositionsWithAutoDetect() {
        log.info("======================================");
        log.info("自动识别 + 自定义位置测试");

        String ipv4 = "192.168.1.1";
        String maskedV4 = helper.mask(ipv4, new int[]{1, 2});
        log.info("IPv4 {} 脱敏 [1,2] → {}", ipv4, maskedV4);

        String ipv6 = "2001:db8::1";
        String maskedV6 = helper.mask(ipv6, new int[]{1, 2});
        log.info("IPv6 {} 脱敏 [1,2] → {}", ipv6, maskedV6);

        log.info("======================================");
    }

    @Test
    void testInvalidIpHandling() {
        log.info("======================================");
        log.info("无效 IP 处理测试");

        String invalidIp = "not-an-ip";
        String result = helper.mask(invalidIp);
        log.info("无效IP: {} → {} (返回原值)", invalidIp, result);

        log.info("======================================");
    }

    @Test
    void testCidr() {
        log.info("======================================");
        log.info("CIDR 自动处理测试");

        String cidr1 = "192.168.0.0/16";
        String masked1 = helper.maskCidr(cidr1);
        log.info("{} → {}", cidr1, masked1);

        String cidr2 = "10.0.0.0/8";
        String masked2 = helper.maskCidr(cidr2);
        log.info("{} → {}", cidr2, masked2);

        String cidr3 = "2001:db8::/32";
        String masked3 = helper.maskCidr(cidr3);
        log.info("{} → {}", cidr3, masked3);

        String cidr4 = "2001:db8:85a3::/48";
        String masked4 = helper.maskCidr(cidr4);
        log.info("{} → {}", cidr4, masked4);

        log.info("======================================");
    }
}
