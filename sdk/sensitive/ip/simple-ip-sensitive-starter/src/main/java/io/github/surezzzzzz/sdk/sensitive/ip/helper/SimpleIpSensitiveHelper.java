package io.github.surezzzzzz.sdk.sensitive.ip.helper;

import io.github.surezzzzzz.sdk.sensitive.ip.annotation.SimpleIpSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.ip.exception.InvalidCidrFormatException;
import io.github.surezzzzzz.sdk.sensitive.ip.exception.InvalidIpFormatException;
import io.github.surezzzzzz.sdk.sensitive.ip.validator.InetAddressValidator;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * IP 自动识别脱敏 Helper
 *
 * @author surezzzzzz
 */
@SimpleIpSensitiveComponent
public class SimpleIpSensitiveHelper {

    @Autowired
    private SimpleIpv4SensitiveHelper ipv4Helper;

    @Autowired
    private SimpleIpv6SensitiveHelper ipv6Helper;

    private final InetAddressValidator validator = InetAddressValidator.getInstance();

    /**
     * 自动识别 IP 类型并脱敏（使用默认策略）
     *
     * @param ip IP 地址（自动识别 IPv4 或 IPv6）
     * @return 脱敏后的 IP 地址
     * @example mask(" 192.168.1.1 ") → "192.168.*.*" (默认脱敏第3、4段)
     * mask("2001:db8::1") → "2001:0db8:****:****:****:****:****:****" (默认脱敏后4组)
     */
    public String mask(String ip) {
        if (validator.isValidInet4Address(ip)) {
            return ipv4Helper.mask(ip);
        } else if (validator.isValidInet6Address(ip)) {
            return ipv6Helper.mask(ip);
        }
        // 无法识别，返回原值（不抛异常，避免影响业务）
        return ip;
    }

    /**
     * 自动识别 IP 类型并脱敏（指定脱敏位置）
     *
     * @param ip            IP 地址（自动识别 IPv4 或 IPv6）
     * @param maskPositions 要脱敏的位置（1-based 索引）
     * @return 脱敏后的 IP 地址
     * @example mask(" 192.168.1.1 ", new int[] { 1, 2 }) → "*.*.1.1"
     * mask("2001:db8::1", new int[]{1, 2}) → "****:****:0000:0000:0000:0000:0000:0001"
     */
    public String mask(String ip, int[] maskPositions) {
        if (validator.isValidInet4Address(ip)) {
            // 跳过重复验证
            return ipv4Helper.maskWithoutValidation(ip, maskPositions);
        } else if (validator.isValidInet6Address(ip)) {
            // 跳过重复验证
            return ipv6Helper.maskWithoutValidation(ip, maskPositions);
        }
        // 无法识别，返回原值
        return ip;
    }

    /**
     * 自动识别 IP 类型并脱敏（指定脱敏位置和掩码字符）
     *
     * @param ip            IP 地址
     * @param maskPositions 要脱敏的位置（1-based 索引）
     * @param maskChar      掩码字符
     * @return 脱敏后的 IP 地址
     */
    public String mask(String ip, int[] maskPositions, String maskChar) {
        if (validator.isValidInet4Address(ip)) {
            // 跳过重复验证
            return ipv4Helper.maskWithoutValidation(ip, maskPositions, maskChar);
        } else if (validator.isValidInet6Address(ip)) {
            // 跳过重复验证
            return ipv6Helper.maskWithoutValidation(ip, maskPositions, maskChar);
        }
        // 无法识别，返回原值
        return ip;
    }

    /**
     * 自动识别并脱敏 CIDR 格式
     *
     * @param cidr CIDR 格式（如 "192.168.1.0/24" 或 "2001:db8::/32"）
     * @return 脱敏后的 CIDR
     */
    public String maskCidr(String cidr) {
        // 解析 CIDR 获取 IP 部分
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            throw new InvalidCidrFormatException(cidr);
        }

        String ip = parts[0];

        // 自动识别 IP 类型
        if (validator.isValidInet4Address(ip)) {
            return ipv4Helper.maskCidr(cidr);
        } else if (validator.isValidInet6Address(ip)) {
            return ipv6Helper.maskCidr(cidr);
        } else {
            throw new InvalidIpFormatException(ip);
        }
    }
}
