package io.github.surezzzzzz.sdk.sensitive.ip.helper;

import io.github.surezzzzzz.sdk.sensitive.ip.annotation.SimpleIpSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.ip.configuration.SimpleIpSensitiveProperties;
import io.github.surezzzzzz.sdk.sensitive.ip.constant.SimpleIpSensitiveConstant;
import io.github.surezzzzzz.sdk.sensitive.ip.exception.*;
import io.github.surezzzzzz.sdk.sensitive.ip.validator.InetAddressValidator;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

/**
 * IPv6 脱敏 Helper
 *
 * @author surezzzzzz
 */
@SimpleIpSensitiveComponent
public class SimpleIpv6SensitiveHelper {

    @Autowired
    private SimpleIpSensitiveProperties properties;

    private final InetAddressValidator validator = InetAddressValidator.getInstance();

    /**
     * 脱敏 IPv6 地址（使用默认策略）
     *
     * @param ip IPv6 地址（支持完整格式和简写格式）
     * @return 脱敏后的 IPv6 地址
     */
    public String mask(String ip) {
        return mask(ip, properties.getDefaultMaskPositions().getIpv6(), properties.getIpv6MaskChar());
    }

    /**
     * 脱敏 IPv6 地址（指定脱敏位置）
     *
     * @param ip         IPv6 地址
     * @param maskGroups 要脱敏的组位置（1-based 索引）
     *                   示例：
     *                   - [5, 6, 7, 8] → "2001:0db8:****:****:****:****:****:****"
     *                   - [1, 8] → "****:0db8:85a3:0000:0000:8a2e:0370:****"
     *                   <p>
     *                   注意：索引从 1 开始（不是 0），符合人类认知
     * @return 脱敏后的 IPv6 地址
     */
    public String mask(String ip, int[] maskGroups) {
        return mask(ip, maskGroups, properties.getIpv6MaskChar());
    }

    /**
     * 脱敏 IPv6 地址（指定脱敏位置和掩码字符）
     *
     * @param ip         IPv6 地址
     * @param maskGroups 要脱敏的组位置（1-based 索引）
     * @param maskChar   掩码字符（如 "****"）
     * @return 脱敏后的 IPv6 地址
     */
    public String mask(String ip, int[] maskGroups, String maskChar) {
        return maskInternal(ip, maskGroups, maskChar, true);
    }

    /**
     * 内部脱敏方法（支持跳过验证以避免重复验证）
     *
     * @param ip         IPv6 地址
     * @param maskGroups 要脱敏的组位置（1-based 索引）
     * @param maskChar   掩码字符
     * @param validate   是否验证 IP 格式
     * @return 脱敏后的 IPv6 地址
     */
    String maskInternal(String ip, int[] maskGroups, String maskChar, boolean validate) {
        // 1. 校验 IP 格式
        if (validate && !validator.isValidInet6Address(ip)) {
            throw new InvalidIpFormatException(ip);
        }

        // 2. 校验 maskGroups
        if (maskGroups == null || maskGroups.length == 0) {
            throw new EmptyMaskPositionsException();
        }

        // 3. 校验位置范围并转换为 HashSet（提升查找性能）
        Set<Integer> maskPosSet = new HashSet<>(maskGroups.length);
        for (int pos : maskGroups) {
            if (pos < 1 || pos > SimpleIpSensitiveConstant.IPV6_GROUPS) {
                throw new MaskPositionOutOfBoundsException(pos, SimpleIpSensitiveConstant.IPV6_GROUPS);
            }
            maskPosSet.add(pos);
        }

        // 4. 展开 IPv6 为完整格式
        String fullIp = expandIpv6(ip);

        // 5. 解析组
        String[] groups = fullIp.split(":");

        // 6. 脱敏（预分配容量：8个组 * 4字符 + 7个冒号）
        StringBuilder result = new StringBuilder(50);
        for (int i = 0; i < SimpleIpSensitiveConstant.IPV6_GROUPS; i++) {
            int position = i + 1;  // 1-based

            if (maskPosSet.contains(position)) {
                result.append(maskChar);
            } else {
                result.append(groups[i]);
            }

            if (i < SimpleIpSensitiveConstant.IPV6_GROUPS - 1) {
                result.append(":");
            }
        }

        return result.toString();
    }

    /**
     * 脱敏 IPv6 地址（跳过验证，用于已验证的场景）
     *
     * @param ip         IPv6 地址（已验证）
     * @param maskGroups 要脱敏的组位置（1-based 索引）
     * @return 脱敏后的 IPv6 地址
     */
    String maskWithoutValidation(String ip, int[] maskGroups) {
        return maskInternal(ip, maskGroups, properties.getIpv6MaskChar(), false);
    }

    /**
     * 脱敏 IPv6 地址（跳过验证，用于已验证的场景）
     *
     * @param ip         IPv6 地址（已验证）
     * @param maskGroups 要脱敏的组位置（1-based 索引）
     * @param maskChar   掩码字符
     * @return 脱敏后的 IPv6 地址
     */
    String maskWithoutValidation(String ip, int[] maskGroups, String maskChar) {
        return maskInternal(ip, maskGroups, maskChar, false);
    }

    /**
     * 脱敏 CIDR 格式的 IPv6 地址
     *
     * @param cidr CIDR 格式（如 "2001:db8::/32"）
     * @return 脱敏后的 CIDR
     * @throws InvalidCidrFormatException 如果 CIDR 格式不合法
     * @throws InvalidCidrPrefixException 如果前缀长度不合法
     * @example maskCidr(" 2001 : db8 : : / 32 ") → "2001:db8:****:0000:0000:0000:0000:0000/32"
     * maskCidr("2001:db8:85a3::/48") → "2001:db8:85a3:****:0000:0000:0000:0000/48"
     * maskCidr("2001:db8:85a3::1/128") → "2001:0db8:****:****:****:****:****:****" (使用默认脱敏策略)
     */
    public String maskCidr(String cidr) {
        // 1. 解析 CIDR
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            throw new InvalidCidrFormatException(cidr);
        }

        String ip = parts[0];
        int prefixLength;
        try {
            prefixLength = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new InvalidCidrFormatException(cidr);
        }

        // 2. 校验前缀长度
        if (prefixLength < 0 || prefixLength > SimpleIpSensitiveConstant.IPV6_TOTAL_BITS) {
            throw new InvalidCidrPrefixException(prefixLength);
        }

        // 3. 校验 IP 格式
        if (!validator.isValidInet6Address(ip)) {
            throw new InvalidIpFormatException(ip);
        }

        // 4. /128 特殊处理：等价于普通 IP，使用默认脱敏策略
        if (prefixLength == SimpleIpSensitiveConstant.IPV6_TOTAL_BITS) {
            return mask(ip);  // 不带 /128 后缀
        }

        // 5. 计算要脱敏的组位置
        // 规律：networkGroups = (prefixLength + 15) / 16
        // 脱敏网络位的最后一个组，主机位补0
        int networkGroups = (prefixLength + 15) / 16;
        int maskPosition = networkGroups;  // 1-based

        // 6. 展开 IPv6 为完整格式
        String fullIp = expandIpv6(ip);

        // 7. 解析组
        String[] groups = fullIp.split(":");

        // 8. 脱敏（预分配容量）
        StringBuilder result = new StringBuilder(60);
        for (int i = 0; i < SimpleIpSensitiveConstant.IPV6_GROUPS; i++) {
            int position = i + 1;  // 1-based

            if (position == maskPosition) {
                // 脱敏网络位的最后一个组
                result.append(properties.getIpv6MaskChar());
            } else if (position < maskPosition) {
                // 网络位之前的组：保留原值
                result.append(groups[i]);
            } else {
                // 主机位：补0000
                result.append("0000");
            }

            if (i < SimpleIpSensitiveConstant.IPV6_GROUPS - 1) {
                result.append(":");
            }
        }

        result.append("/").append(prefixLength);
        return result.toString();
    }

    /**
     * 将 IPv6 简写格式展开为完整格式
     *
     * @param ip IPv6 地址（可能是简写格式，如 "2001:db8::1"）
     * @return 完整格式的 IPv6 地址（如 "2001:0db8:0000:0000:0000:0000:0000:0001"）
     */
    private String expandIpv6(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            if (!(addr instanceof Inet6Address)) {
                throw new InvalidIpFormatException(ip);
            }

            byte[] bytes = addr.getAddress();
            StringBuilder sb = new StringBuilder(40);
            for (int i = 0; i < bytes.length; i += 2) {
                if (i > 0) {
                    sb.append(":");
                }
                int high = (bytes[i] & 0xff);
                int low = (bytes[i + 1] & 0xff);
                sb.append(String.format("%04x", (high << 8) | low));
            }
            return sb.toString();
        } catch (InvalidIpFormatException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidIpFormatException(ip);
        }
    }
}
