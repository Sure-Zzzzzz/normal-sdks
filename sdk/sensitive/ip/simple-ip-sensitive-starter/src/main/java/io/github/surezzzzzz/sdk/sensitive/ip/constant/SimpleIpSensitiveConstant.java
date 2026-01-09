package io.github.surezzzzzz.sdk.sensitive.ip.constant;

/**
 * Simple IP Sensitive Constants
 *
 * @author surezzzzzz
 */
public class SimpleIpSensitiveConstant {

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.sensitive.ip";

    /**
     * 默认 IPv4 掩码字符
     */
    public static final String DEFAULT_IPV4_MASK_CHAR = "*";

    /**
     * 默认 IPv6 掩码字符
     */
    public static final String DEFAULT_IPV6_MASK_CHAR = "****";

    /**
     * 默认 IPv4 脱敏位置（脱敏第3、4段）
     */
    public static final int[] DEFAULT_IPV4_MASK_POSITIONS = {3, 4};

    /**
     * 默认 IPv6 脱敏位置（脱敏后4组）
     */
    public static final int[] DEFAULT_IPV6_MASK_POSITIONS = {5, 6, 7, 8};

    /**
     * IPv4 段数
     */
    public static final int IPV4_SEGMENTS = 4;

    /**
     * IPv6 组数
     */
    public static final int IPV6_GROUPS = 8;

    /**
     * IPv4 总位数
     */
    public static final int IPV4_TOTAL_BITS = 32;

    /**
     * IPv6 总位数
     */
    public static final int IPV6_TOTAL_BITS = 128;

    private SimpleIpSensitiveConstant() {
        throw new UnsupportedOperationException("Constant class cannot be instantiated");
    }
}
