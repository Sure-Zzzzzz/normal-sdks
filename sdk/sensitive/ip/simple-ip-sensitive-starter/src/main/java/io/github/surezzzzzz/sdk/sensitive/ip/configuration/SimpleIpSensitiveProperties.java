package io.github.surezzzzzz.sdk.sensitive.ip.configuration;

import io.github.surezzzzzz.sdk.sensitive.ip.constant.SimpleIpSensitiveConstant;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple IP Sensitive Properties
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleIpSensitiveConstant.CONFIG_PREFIX)
@ConditionalOnProperty(prefix = SimpleIpSensitiveConstant.CONFIG_PREFIX, name = "enable", havingValue = "true", matchIfMissing = true)
public class SimpleIpSensitiveProperties {

    /**
     * 是否启用（默认 true）
     */
    private boolean enable = true;

    /**
     * 默认脱敏位置配置
     */
    private DefaultMaskPositions defaultMaskPositions = new DefaultMaskPositions();

    /**
     * IPv4 掩码字符（默认 "*"）
     */
    private String ipv4MaskChar = SimpleIpSensitiveConstant.DEFAULT_IPV4_MASK_CHAR;

    /**
     * IPv6 掩码字符（默认 "****"）
     */
    private String ipv6MaskChar = SimpleIpSensitiveConstant.DEFAULT_IPV6_MASK_CHAR;

    /**
     * 默认脱敏位置配置
     */
    @Data
    public static class DefaultMaskPositions {
        /**
         * IPv4 默认脱敏位置（默认 [3, 4]）
         */
        private int[] ipv4 = SimpleIpSensitiveConstant.DEFAULT_IPV4_MASK_POSITIONS;

        /**
         * IPv6 默认脱敏位置（默认 [5, 6, 7, 8]）
         */
        private int[] ipv6 = SimpleIpSensitiveConstant.DEFAULT_IPV6_MASK_POSITIONS;
    }
}
