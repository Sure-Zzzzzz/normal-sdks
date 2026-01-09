package io.github.surezzzzzz.sdk.sensitive.ip.helper;

import io.github.surezzzzzz.sdk.sensitive.ip.annotation.SimpleIpSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.ip.configuration.SimpleIpSensitiveProperties;
import io.github.surezzzzzz.sdk.sensitive.ip.constant.SimpleIpSensitiveConstant;
import io.github.surezzzzzz.sdk.sensitive.ip.exception.*;
import io.github.surezzzzzz.sdk.sensitive.ip.validator.InetAddressValidator;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashSet;
import java.util.Set;

/**
 * IPv4 脱敏 Helper
 *
 * @author surezzzzzz
 */
@SimpleIpSensitiveComponent
public class SimpleIpv4SensitiveHelper {

    @Autowired
    private SimpleIpSensitiveProperties properties;

    private final InetAddressValidator validator = InetAddressValidator.getInstance();

    /**
     * 脱敏 IPv4 地址（使用默认策略）
     *
     * @param ip IPv4 地址（如 "192.168.1.1"）
     * @return 脱敏后的 IPv4 地址
     * @throws InvalidIpFormatException 如果 IP 格式不合法
     */
    public String mask(String ip) {
        return mask(ip, properties.getDefaultMaskPositions().getIpv4(), properties.getIpv4MaskChar());
    }

    /**
     * 脱敏 IPv4 地址（指定脱敏位置）
     *
     * @param ip            IPv4 地址（如 "192.168.1.1"）
     * @param maskPositions 要脱敏的段位置（1-based 索引，符合人类认知）
     *                      示例：
     *                      - [3, 4] 表示脱敏第3、4段 → "192.168.*.*"
     *                      - [1, 2] 表示脱敏第1、2段 → "*.*.1.1"
     *                      - [2, 3] 表示脱敏第2、3段 → "192.*.*1"
     *                      <p>
     *                      注意：索引从 1 开始（不是 0），这是为了符合人类认知习惯：
     *                      - 第1段 = 192
     *                      - 第2段 = 168
     *                      - 第3段 = 1
     *                      - 第4段 = 1
     * @return 脱敏后的 IPv4 地址
     * @throws InvalidIpFormatException         如果 IP 格式不合法
     * @throws EmptyMaskPositionsException      如果 maskPositions 为空
     * @throws MaskPositionOutOfBoundsException 如果 maskPositions 越界
     */
    public String mask(String ip, int[] maskPositions) {
        return mask(ip, maskPositions, properties.getIpv4MaskChar());
    }

    /**
     * 脱敏 IPv4 地址（指定脱敏位置和掩码字符）
     *
     * @param ip            IPv4 地址
     * @param maskPositions 要脱敏的段位置（1-based 索引）
     * @param maskChar      掩码字符（如 "*"、"X"）
     * @return 脱敏后的 IPv4 地址
     * @throws InvalidIpFormatException         如果 IP 格式不合法
     * @throws EmptyMaskPositionsException      如果 maskPositions 为空
     * @throws MaskPositionOutOfBoundsException 如果 maskPositions 越界
     */
    public String mask(String ip, int[] maskPositions, String maskChar) {
        return maskInternal(ip, maskPositions, maskChar, true);
    }

    /**
     * 内部脱敏方法（支持跳过验证以避免重复验证）
     *
     * @param ip            IPv4 地址
     * @param maskPositions 要脱敏的段位置（1-based 索引）
     * @param maskChar      掩码字符
     * @param validate      是否验证 IP 格式
     * @return 脱敏后的 IPv4 地址
     */
    String maskInternal(String ip, int[] maskPositions, String maskChar, boolean validate) {
        // 1. 校验 IP 格式
        if (validate && !validator.isValidInet4Address(ip)) {
            throw new InvalidIpFormatException(ip);
        }

        // 2. 校验 maskPositions
        if (maskPositions == null || maskPositions.length == 0) {
            throw new EmptyMaskPositionsException();
        }

        // 3. 校验位置范围并转换为 HashSet（提升查找性能）
        Set<Integer> maskPosSet = new HashSet<>(maskPositions.length);
        for (int pos : maskPositions) {
            if (pos < 1 || pos > SimpleIpSensitiveConstant.IPV4_SEGMENTS) {
                throw new MaskPositionOutOfBoundsException(pos, SimpleIpSensitiveConstant.IPV4_SEGMENTS);
            }
            maskPosSet.add(pos);
        }

        // 4. 解析 IP 段
        String[] segments = ip.split("\\.");

        // 5. 脱敏（预分配容量：4个段 + 3个点 + 可能的掩码字符）
        StringBuilder result = new StringBuilder(20);
        for (int i = 0; i < SimpleIpSensitiveConstant.IPV4_SEGMENTS; i++) {
            int position = i + 1;  // 1-based

            if (maskPosSet.contains(position)) {
                result.append(maskChar);
            } else {
                result.append(segments[i]);
            }

            if (i < SimpleIpSensitiveConstant.IPV4_SEGMENTS - 1) {
                result.append(".");
            }
        }

        return result.toString();
    }

    /**
     * 脱敏 IPv4 地址（跳过验证，用于已验证的场景）
     *
     * @param ip            IPv4 地址（已验证）
     * @param maskPositions 要脱敏的段位置（1-based 索引）
     * @return 脱敏后的 IPv4 地址
     */
    String maskWithoutValidation(String ip, int[] maskPositions) {
        return maskInternal(ip, maskPositions, properties.getIpv4MaskChar(), false);
    }

    /**
     * 脱敏 IPv4 地址（跳过验证，用于已验证的场景）
     *
     * @param ip            IPv4 地址（已验证）
     * @param maskPositions 要脱敏的段位置（1-based 索引）
     * @param maskChar      掩码字符
     * @return 脱敏后的 IPv4 地址
     */
    String maskWithoutValidation(String ip, int[] maskPositions, String maskChar) {
        return maskInternal(ip, maskPositions, maskChar, false);
    }

    /**
     * 脱敏 CIDR 格式的 IPv4 地址
     *
     * @param cidr CIDR 格式（如 "192.168.1.0/24"）
     * @return 脱敏后的 CIDR
     * @throws InvalidCidrFormatException 如果 CIDR 格式不合法
     * @throws InvalidCidrPrefixException 如果前缀长度不合法
     * @example maskCidr(" 10.0.0.0 / 8 ") → "*.0.0.0/8"
     * maskCidr("192.168.0.0/16") → "192.*.0.0/16"
     * maskCidr("192.168.1.0/24") → "192.168.*.0/24"
     * maskCidr("192.168.1.1/32") → "192.168.*.*"(使用默认脱敏策略)
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
        if (prefixLength < 0 || prefixLength > SimpleIpSensitiveConstant.IPV4_TOTAL_BITS) {
            throw new InvalidCidrPrefixException(prefixLength);
        }

        // 3. 校验 IP 格式
        if (!validator.isValidInet4Address(ip)) {
            throw new InvalidIpFormatException(ip);
        }

        // 4. /32 特殊处理：等价于普通 IP，使用默认脱敏策略
        if (prefixLength == SimpleIpSensitiveConstant.IPV4_TOTAL_BITS) {
            return mask(ip);  // 不带 /32 后缀
        }

        // 5. 计算要脱敏的段位置
        // 规律：networkBytes = (prefixLength + 7) / 8
        // 脱敏网络位的最后一个字节，主机位补0
        int networkBytes = (prefixLength + 7) / 8;
        int maskPosition = networkBytes;  // 1-based

        // 6. 解析 IP
        String[] segments = ip.split("\\.");

        // 7. 脱敏（预分配容量）
        StringBuilder result = new StringBuilder(25);
        for (int i = 0; i < SimpleIpSensitiveConstant.IPV4_SEGMENTS; i++) {
            int position = i + 1;  // 1-based

            if (position == maskPosition) {
                // 脱敏网络位的最后一个字节
                result.append(properties.getIpv4MaskChar());
            } else if (position < maskPosition) {
                // 网络位之前的段：保留原值
                result.append(segments[i]);
            } else {
                // 主机位：补0
                result.append("0");
            }

            if (i < SimpleIpSensitiveConstant.IPV4_SEGMENTS - 1) {
                result.append(".");
            }
        }

        result.append("/").append(prefixLength);
        return result.toString();
    }
}
