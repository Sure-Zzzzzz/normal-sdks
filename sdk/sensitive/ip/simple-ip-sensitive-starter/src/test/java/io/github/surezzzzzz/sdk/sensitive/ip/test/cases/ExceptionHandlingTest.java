package io.github.surezzzzzz.sdk.sensitive.ip.test.cases;

import io.github.surezzzzzz.sdk.sensitive.ip.exception.*;
import io.github.surezzzzzz.sdk.sensitive.ip.helper.SimpleIpv4SensitiveHelper;
import io.github.surezzzzzz.sdk.sensitive.ip.helper.SimpleIpv6SensitiveHelper;
import io.github.surezzzzzz.sdk.sensitive.ip.test.SimpleIpSensitiveTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 异常处理端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIpSensitiveTestApplication.class)
class ExceptionHandlingTest {

    @Autowired
    private SimpleIpv4SensitiveHelper ipv4Helper;

    @Autowired
    private SimpleIpv6SensitiveHelper ipv6Helper;

    @Test
    void testInvalidIpv4Format() {
        log.info("======================================");
        log.info("无效 IPv4 格式异常测试");

        assertThrows(InvalidIpFormatException.class, () -> {
            ipv4Helper.mask("256.1.1.1");
        });
        log.info("✓ 成功捕获 InvalidIpFormatException (256.1.1.1)");

        assertThrows(InvalidIpFormatException.class, () -> {
            ipv4Helper.mask("192.168.1");
        });
        log.info("✓ 成功捕获 InvalidIpFormatException (192.168.1)");

        assertThrows(InvalidIpFormatException.class, () -> {
            ipv4Helper.mask("not-an-ip");
        });
        log.info("✓ 成功捕获 InvalidIpFormatException (not-an-ip)");

        log.info("======================================");
    }

    @Test
    void testInvalidIpv6Format() {
        log.info("======================================");
        log.info("无效 IPv6 格式异常测试");

        assertThrows(InvalidIpFormatException.class, () -> {
            ipv6Helper.mask("gggg::1");
        });
        log.info("✓ 成功捕获 InvalidIpFormatException (gggg::1)");

        assertThrows(InvalidIpFormatException.class, () -> {
            ipv6Helper.mask("not-an-ipv6");
        });
        log.info("✓ 成功捕获 InvalidIpFormatException (not-an-ipv6)");

        log.info("======================================");
    }

    @Test
    void testEmptyMaskPositions() {
        log.info("======================================");
        log.info("空位置数组异常测试");

        assertThrows(EmptyMaskPositionsException.class, () -> {
            ipv4Helper.mask("192.168.1.1", new int[]{});
        });
        log.info("✓ 成功捕获 EmptyMaskPositionsException (IPv4)");

        assertThrows(EmptyMaskPositionsException.class, () -> {
            ipv6Helper.mask("2001:db8::1", new int[]{});
        });
        log.info("✓ 成功捕获 EmptyMaskPositionsException (IPv6)");

        log.info("======================================");
    }

    @Test
    void testMaskPositionOutOfBounds() {
        log.info("======================================");
        log.info("位置越界异常测试");

        assertThrows(MaskPositionOutOfBoundsException.class, () -> {
            ipv4Helper.mask("192.168.1.1", new int[]{5});
        });
        log.info("✓ 成功捕获 MaskPositionOutOfBoundsException (IPv4 位置5)");

        assertThrows(MaskPositionOutOfBoundsException.class, () -> {
            ipv4Helper.mask("192.168.1.1", new int[]{0});
        });
        log.info("✓ 成功捕获 MaskPositionOutOfBoundsException (IPv4 位置0)");

        assertThrows(MaskPositionOutOfBoundsException.class, () -> {
            ipv6Helper.mask("2001:db8::1", new int[]{9});
        });
        log.info("✓ 成功捕获 MaskPositionOutOfBoundsException (IPv6 位置9)");

        assertThrows(MaskPositionOutOfBoundsException.class, () -> {
            ipv6Helper.mask("2001:db8::1", new int[]{0});
        });
        log.info("✓ 成功捕获 MaskPositionOutOfBoundsException (IPv6 位置0)");

        log.info("======================================");
    }

    @Test
    void testInvalidCidrFormat() {
        log.info("======================================");
        log.info("无效 CIDR 格式异常测试");

        assertThrows(InvalidCidrFormatException.class, () -> {
            ipv4Helper.maskCidr("192.168.1.1");
        });
        log.info("✓ 成功捕获 InvalidCidrFormatException (无斜杠)");

        assertThrows(InvalidCidrFormatException.class, () -> {
            ipv4Helper.maskCidr("192.168.1.1/abc");
        });
        log.info("✓ 成功捕获 InvalidCidrFormatException (非数字前缀)");

        log.info("======================================");
    }

    @Test
    void testInvalidCidrPrefix() {
        log.info("======================================");
        log.info("无效 CIDR 前缀异常测试");

        assertThrows(InvalidCidrPrefixException.class, () -> {
            ipv4Helper.maskCidr("192.168.1.1/33");
        });
        log.info("✓ 成功捕获 InvalidCidrPrefixException (/33)");

        assertThrows(InvalidCidrPrefixException.class, () -> {
            ipv4Helper.maskCidr("192.168.1.1/-1");
        });
        log.info("✓ 成功捕获 InvalidCidrPrefixException (/-1)");

        log.info("======================================");
    }

    @Test
    void testAllPositionsMasked() {
        log.info("======================================");
        log.info("全部位置脱敏测试（不应抛异常）");

        String ipv4 = "192.168.1.1";
        String maskedV4 = ipv4Helper.mask(ipv4, new int[]{1, 2, 3, 4});
        log.info("IPv4 全脱敏: {} → {}", ipv4, maskedV4);

        String ipv6 = "2001:db8::1";
        String maskedV6 = ipv6Helper.mask(ipv6, new int[]{1, 2, 3, 4, 5, 6, 7, 8});
        log.info("IPv6 全脱敏: {} → {}", ipv6, maskedV6);

        log.info("✓ 全部位置脱敏正常执行");
        log.info("======================================");
    }
}
