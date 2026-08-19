package io.github.surezzzzzz.sdk.http.xff.core.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Simple XFF Capture Core 常量。
 *
 * @author surezzzzzz
 */
public final class SimpleXffCaptureCoreConstant {

    /**
     * eventId 字段名。
     */
    public static final String FIELD_EVENT_ID = "eventId";
    /**
     * occurredAt 字段名。
     */
    public static final String FIELD_OCCURRED_AT = "occurredAt";
    /**
     * requestMethod 字段名。
     */
    public static final String FIELD_REQUEST_METHOD = "requestMethod";
    /**
     * requestUri 字段名。
     */
    public static final String FIELD_REQUEST_URI = "requestUri";
    /**
     * xffChain 字段名。
     */
    public static final String FIELD_XFF_CHAIN = "xffChain";
    /**
     * forwardedContext 字段名。
     */
    public static final String FIELD_FORWARDED_CONTEXT = "forwardedContext";
    /**
     * snapshot 字段名。
     */
    public static final String FIELD_CAPTURE_SNAPSHOT = "snapshot";
    /**
     * host 字段名。
     */
    public static final String FIELD_HOST = "host";
    /**
     * xRealIp 字段名。
     */
    public static final String FIELD_X_REAL_IP = "xRealIp";
    /**
     * xForwardedHost 字段名。
     */
    public static final String FIELD_X_FORWARDED_HOST = "xForwardedHost";
    /**
     * xForwardedPort 字段名。
     */
    public static final String FIELD_X_FORWARDED_PORT = "xForwardedPort";
    /**
     * xForwardedProto 字段名。
     */
    public static final String FIELD_X_FORWARDED_PROTO = "xForwardedProto";
    /**
     * rawHeaderList 字段名。
     */
    public static final String FIELD_RAW_HEADER_LIST = "rawHeaderList";
    /**
     * rawList 字段名。
     */
    public static final String FIELD_RAW_LIST = "rawList";
    /**
     * rawValueList 字段名。
     */
    public static final String FIELD_RAW_VALUE_LIST = "rawValueList";
    /**
     * scope 字段名。
     */
    public static final String FIELD_IP_SCOPE = "scope";
    /**
     * 集合包含 null 详情模板。
     */
    public static final String DETAIL_COLLECTION_CONTAINS_NULL = "%s 不能包含 null";

    // ==================== 校验详情常量 ====================
    /**
     * XFF Header 不存在时状态详情。
     */
    public static final String DETAIL_ABSENT_XFF_CHAIN_STATE_INVALID =
            "XFF Header 不存在时两个列表必须为空";
    /**
     * XFF Header 存在时状态详情。
     */
    public static final String DETAIL_PRESENT_XFF_CHAIN_STATE_INVALID =
            "XFF Header 存在时两个列表必须非空";
    /**
     * 普通 Header 不存在时状态详情。
     */
    public static final String DETAIL_ABSENT_HEADER_STATE_INVALID =
            "Header 不存在时原始值列表必须为空";
    /**
     * 普通 Header 存在时状态详情。
     */
    public static final String DETAIL_PRESENT_HEADER_STATE_INVALID =
            "Header 存在时原始值列表必须非空";
    /**
     * 地址分类结果状态非法详情。
     */
    public static final String DETAIL_ADDRESS_INFO_STATE_INVALID = "地址分类结果字段不一致";
    /**
     * IANA 特殊用途地址注册表版本日期。
     */
    public static final String IANA_REGISTRY_SNAPSHOT_DATE = "2025-10-09";

    // ==================== 地址分类常量 ====================
    /**
     * IP 分类版本。
     */
    public static final String IP_CLASSIFICATION_VERSION = "iana-2025-10-09";
    /**
     * IPv4 私网 CIDR。
     */
    public static final List<String> PRIVATE_IPV4_CIDR_LIST = Collections.unmodifiableList(Arrays.asList(
            "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"
    ));
    /**
     * IPv6 ULA 私网 CIDR。
     */
    public static final String PRIVATE_IPV6_CIDR = "fc00::/7";
    /**
     * IPv4 非公网 CIDR，基于 2025-10-09 IANA 特殊用途地址注册表快照。
     */
    public static final List<String> NON_PUBLIC_IPV4_CIDR_LIST = Collections.unmodifiableList(Arrays.asList(
            "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8",
            "169.254.0.0/16", "172.16.0.0/12", "192.0.0.0/24", "192.0.2.0/24",
            "192.88.99.0/24", "192.168.0.0/16", "198.18.0.0/15", "198.51.100.0/24",
            "203.0.113.0/24", "224.0.0.0/4", "240.0.0.0/4"
    ));
    /**
     * 宽非公网 CIDR 中仍可全局到达的 IPv4 例外。
     */
    public static final List<String> PUBLIC_IPV4_EXCEPTION_CIDR_LIST = Collections.unmodifiableList(Arrays.asList(
            "192.0.0.9/32", "192.0.0.10/32"
    ));
    /**
     * IPv6 非公网 CIDR，基于 2025-10-09 IANA 特殊用途地址注册表快照。
     */
    public static final List<String> NON_PUBLIC_IPV6_CIDR_LIST = Collections.unmodifiableList(Arrays.asList(
            "::/96", "::1/128", "64:ff9b:1::/48", "100::/64", "100:0:0:1::/64",
            "2001::/23", "2001:db8::/32", "2002::/16", "3fff::/20", "5f00::/16",
            "fc00::/7", "fe80::/10", "ff00::/8"
    ));
    /**
     * 宽非公网 CIDR 中仍可全局到达的 IPv6 例外。
     */
    public static final List<String> PUBLIC_IPV6_EXCEPTION_CIDR_LIST = Collections.unmodifiableList(Arrays.asList(
            "64:ff9b::/96", "2001:1::1/128", "2001:1::2/128", "2001:1::3/128",
            "2001:3::/32", "2001:4:112::/48", "2001:20::/28", "2001:30::/28"
    ));
    /**
     * 当前 IPv6 全局单播基础范围。
     */
    public static final List<String> PUBLIC_IPV6_BASE_CIDR_LIST = Collections.unmodifiableList(Arrays.asList(
            "2000::/3"
    ));
    /**
     * 空字符串。
     */
    public static final String EMPTY_VALUE = "";

    // ==================== 地址解析常量 ====================
    /**
     * IPv6 压缩分隔符。
     */
    public static final String IPV6_COMPRESSED_SEPARATOR = "::";
    /**
     * IPv6 分组分隔符。
     */
    public static final String IPV6_WORD_SEPARATOR_REGEX = ":";
    /**
     * CIDR 前缀分隔符。
     */
    public static final char CIDR_PREFIX_SEPARATOR = '/';
    /**
     * IPv4 分段分隔符。
     */
    public static final char IPV4_PART_SEPARATOR = '.';
    /**
     * IPv6 分组分隔符。
     */
    public static final char IPV6_WORD_SEPARATOR = ':';
    /**
     * IPv6 zone id 分隔符。
     */
    public static final char IPV6_ZONE_SEPARATOR = '%';
    /**
     * IPv6 方括号起始符。
     */
    public static final char IPV6_BRACKET_START = '[';
    /**
     * IPv6 方括号结束符。
     */
    public static final char IPV6_BRACKET_END = ']';
    /**
     * IPv4 字节数。
     */
    public static final int IPV4_BYTE_COUNT = 4;
    /**
     * IPv6 字节数。
     */
    public static final int IPV6_BYTE_COUNT = 16;
    /**
     * IPv6 16 位分组数。
     */
    public static final int IPV6_WORD_COUNT = 8;
    /**
     * IPv6 单组最大字符数。
     */
    public static final int IPV6_WORD_MAX_LENGTH = 4;
    /**
     * IPv4 单段最大字符数。
     */
    public static final int IPV4_PART_MAX_LENGTH = 3;
    /**
     * IPv4 单段最大值。
     */
    public static final int IPV4_PART_MAX_VALUE = 255;
    /**
     * 单字节位数。
     */
    public static final int BYTE_BIT_COUNT = 8;
    /**
     * IPv4-mapped IPv6 前导零字节数。
     */
    public static final int IPV4_MAPPED_ZERO_BYTE_COUNT = 10;
    /**
     * IPv4-mapped IPv6 标记字节值。
     */
    public static final int IPV4_MAPPED_MARKER = 0xFF;
    /**
     * 一个 IPv6 分组占用的字节数。
     */
    public static final int IPV6_WORD_BYTE_COUNT = 2;
    /**
     * 十六进制基数。
     */
    public static final int HEX_RADIX = 16;

    private SimpleXffCaptureCoreConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
