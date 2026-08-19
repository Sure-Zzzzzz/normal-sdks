package io.github.surezzzzzz.sdk.http.xff.core.support;

import io.github.surezzzzzz.sdk.http.xff.core.constant.*;
import io.github.surezzzzzz.sdk.http.xff.core.exception.XffCaptureValidationException;
import io.github.surezzzzzz.sdk.http.xff.core.model.XffAddressInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * XFF 地址判断 Helper。
 *
 * <p>所有方法只处理 IP 字面量，不接受域名，也不会发起 DNS 查询。公网分类基于
 * 2025-10-09 更新的 IANA 特殊用途地址注册表快照，并补充组播等基础地址类型规则。</p>
 *
 * @author surezzzzzz
 */
public final class XffAddressHelper {

    private static final List<Cidr> PRIVATE_IPV4_CIDR_LIST =
            compileCidrList(SimpleXffCaptureCoreConstant.PRIVATE_IPV4_CIDR_LIST);
    private static final Cidr PRIVATE_IPV6_CIDR = compileCidr(SimpleXffCaptureCoreConstant.PRIVATE_IPV6_CIDR);
    private static final List<Cidr> NON_PUBLIC_IPV4_CIDR_LIST =
            compileCidrList(SimpleXffCaptureCoreConstant.NON_PUBLIC_IPV4_CIDR_LIST);
    private static final List<Cidr> PUBLIC_IPV4_EXCEPTION_CIDR_LIST =
            compileCidrList(SimpleXffCaptureCoreConstant.PUBLIC_IPV4_EXCEPTION_CIDR_LIST);
    private static final List<Cidr> NON_PUBLIC_IPV6_CIDR_LIST =
            compileCidrList(SimpleXffCaptureCoreConstant.NON_PUBLIC_IPV6_CIDR_LIST);
    private static final List<Cidr> PUBLIC_IPV6_EXCEPTION_CIDR_LIST =
            compileCidrList(SimpleXffCaptureCoreConstant.PUBLIC_IPV6_EXCEPTION_CIDR_LIST);
    private static final List<Cidr> PUBLIC_IPV6_BASE_CIDR_LIST =
            compileCidrList(SimpleXffCaptureCoreConstant.PUBLIC_IPV6_BASE_CIDR_LIST);

    private XffAddressHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 判断输入是否为纯 IPv4 或 IPv6 字面量。
     *
     * @param value 待判断值
     * @return true 表示合法 IP 字面量
     */
    public static boolean isIpLiteral(String value) {
        return classify(value).isIpLiteral();
    }

    /**
     * 判断输入是否为 RFC 1918 IPv4 私网或 RFC 4193 IPv6 ULA。
     *
     * @param value 待判断值
     * @return true 表示私网地址
     */
    public static boolean isPrivateIp(String value) {
        return classify(value).getScope() == XffIpScope.PRIVATE;
    }

    /**
     * 判断输入是否为全局公网单播地址。
     *
     * <p>不属于私网不等于公网，回环、链路本地、文档和其他特殊用途地址均返回 false。
     * IPv4-mapped IPv6 按内嵌 IPv4 分类。</p>
     *
     * @param value 待判断值
     * @return true 表示全局公网单播地址
     */
    public static boolean isPublicIp(String value) {
        return classify(value).getScope() == XffIpScope.PUBLIC;
    }

    /**
     * 严格解析并分类 IP 字面量。
     *
     * <p>IPv4-mapped IPv6 按内嵌 IPv4 规范化与分类。</p>
     *
     * @param value 待分类值
     * @return 不可变地址分类结果
     */
    public static XffAddressInfo classify(String value) {
        ParsedAddress address = parse(value);
        if (address == null) {
            return new XffAddressInfo(false, null, null, XffIpScope.INVALID);
        }
        if (address.ipv4) {
            return classifyIpv4(address.bytes);
        }
        byte[] mappedIpv4 = mappedIpv4(address.bytes);
        if (mappedIpv4 != null) {
            return classifyIpv4(mappedIpv4);
        }
        XffIpScope scope;
        if (matches(address.bytes, PRIVATE_IPV6_CIDR)) {
            scope = XffIpScope.PRIVATE;
        } else if (isPublicIpv6(address.bytes)) {
            scope = XffIpScope.PUBLIC;
        } else {
            scope = XffIpScope.SPECIAL;
        }
        return new XffAddressInfo(true, formatIpv6(address.bytes), XffIpVersion.IPV6, scope);
    }

    private static XffAddressInfo classifyIpv4(byte[] address) {
        XffIpScope scope;
        if (matchesAny(address, PRIVATE_IPV4_CIDR_LIST)) {
            scope = XffIpScope.PRIVATE;
        } else if (isPublic(address, PUBLIC_IPV4_EXCEPTION_CIDR_LIST, NON_PUBLIC_IPV4_CIDR_LIST)) {
            scope = XffIpScope.PUBLIC;
        } else {
            scope = XffIpScope.SPECIAL;
        }
        return new XffAddressInfo(true, formatIpv4(address), XffIpVersion.IPV4, scope);
    }

    private static String formatIpv4(byte[] address) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < address.length; index++) {
            if (index > 0) {
                result.append(SimpleXffCaptureCoreConstant.IPV4_PART_SEPARATOR);
            }
            result.append(address[index] & SimpleXffCaptureCoreConstant.IPV4_PART_MAX_VALUE);
        }
        return result.toString();
    }

    private static String formatIpv6(byte[] address) {
        int[] words = new int[SimpleXffCaptureCoreConstant.IPV6_WORD_COUNT];
        for (int index = 0; index < words.length; index++) {
            int high = address[index * SimpleXffCaptureCoreConstant.IPV6_WORD_BYTE_COUNT]
                    & SimpleXffCaptureCoreConstant.IPV4_PART_MAX_VALUE;
            int low = address[index * SimpleXffCaptureCoreConstant.IPV6_WORD_BYTE_COUNT + 1]
                    & SimpleXffCaptureCoreConstant.IPV4_PART_MAX_VALUE;
            words[index] = (high << SimpleXffCaptureCoreConstant.BYTE_BIT_COUNT) | low;
        }

        int bestStart = -1;
        int bestLength = 0;
        int currentStart = -1;
        for (int index = 0; index <= words.length; index++) {
            if (index < words.length && words[index] == 0) {
                if (currentStart < 0) {
                    currentStart = index;
                }
                continue;
            }
            if (currentStart >= 0) {
                int currentLength = index - currentStart;
                if (currentLength >= 2 && currentLength > bestLength) {
                    bestStart = currentStart;
                    bestLength = currentLength;
                }
                currentStart = -1;
            }
        }

        StringBuilder result = new StringBuilder();
        for (int index = 0; index < words.length; index++) {
            if (index == bestStart) {
                result.append(SimpleXffCaptureCoreConstant.IPV6_COMPRESSED_SEPARATOR);
                index += bestLength - 1;
                continue;
            }
            if (result.length() > 0
                    && result.charAt(result.length() - 1) != SimpleXffCaptureCoreConstant.IPV6_WORD_SEPARATOR) {
                result.append(SimpleXffCaptureCoreConstant.IPV6_WORD_SEPARATOR);
            }
            result.append(Integer.toHexString(words[index]));
        }
        return result.toString();
    }

    private static boolean isPublicIpv6(byte[] address) {
        Cidr publicException = longestMatch(address, PUBLIC_IPV6_EXCEPTION_CIDR_LIST);
        Cidr nonPublic = longestMatch(address, NON_PUBLIC_IPV6_CIDR_LIST);
        if (publicException != null && (nonPublic == null
                || publicException.prefixLength > nonPublic.prefixLength)) {
            return true;
        }
        return matchesAny(address, PUBLIC_IPV6_BASE_CIDR_LIST) && nonPublic == null;
    }

    private static boolean isPublic(byte[] address, List<Cidr> publicExceptionList,
                                    List<Cidr> nonPublicList) {
        Cidr publicException = longestMatch(address, publicExceptionList);
        Cidr nonPublic = longestMatch(address, nonPublicList);
        if (publicException == null) {
            return nonPublic == null;
        }
        return nonPublic == null || publicException.prefixLength > nonPublic.prefixLength;
    }

    private static Cidr longestMatch(byte[] address, List<Cidr> cidrList) {
        for (Cidr cidr : cidrList) {
            if (matches(address, cidr)) {
                return cidr;
            }
        }
        return null;
    }

    private static boolean matchesAny(byte[] address, List<Cidr> cidrList) {
        return longestMatch(address, cidrList) != null;
    }

    private static boolean matches(byte[] address, Cidr cidr) {
        if (cidr == null || address.length != cidr.address.length) {
            return false;
        }
        int completeBytes = cidr.prefixLength / SimpleXffCaptureCoreConstant.BYTE_BIT_COUNT;
        int remainingBits = cidr.prefixLength % SimpleXffCaptureCoreConstant.BYTE_BIT_COUNT;
        for (int index = 0; index < completeBytes; index++) {
            if (address[index] != cidr.address[index]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = SimpleXffCaptureCoreConstant.IPV4_PART_MAX_VALUE
                << (SimpleXffCaptureCoreConstant.BYTE_BIT_COUNT - remainingBits);
        return (address[completeBytes] & mask) == (cidr.address[completeBytes] & mask);
    }

    private static List<Cidr> compileCidrList(List<String> cidrValues) {
        List<Cidr> cidrList = new ArrayList<>(cidrValues.size());
        for (String value : cidrValues) {
            cidrList.add(compileCidr(value));
        }
        cidrList.sort(Comparator.comparingInt(Cidr::getPrefixLength).reversed());
        return Collections.unmodifiableList(cidrList);
    }

    private static Cidr compileCidr(String value) {
        try {
            int separator = value.lastIndexOf(SimpleXffCaptureCoreConstant.CIDR_PREFIX_SEPARATOR);
            if (separator <= 0 || separator == value.length() - 1) {
                throw ruleException(value);
            }
            ParsedAddress address = parse(value.substring(0, separator));
            if (address == null) {
                throw ruleException(value);
            }
            int prefixLength = Integer.parseInt(value.substring(separator + 1));
            int maxPrefixLength = address.bytes.length * SimpleXffCaptureCoreConstant.BYTE_BIT_COUNT;
            if (prefixLength < 0 || prefixLength > maxPrefixLength) {
                throw ruleException(value);
            }
            return new Cidr(address.bytes, prefixLength);
        } catch (XffCaptureValidationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new XffCaptureValidationException(ErrorCode.ADDRESS_RULE_INVALID,
                    String.format(ErrorMessage.ADDRESS_RULE_INVALID, value), e);
        }
    }

    private static XffCaptureValidationException ruleException(String value) {
        return new XffCaptureValidationException(ErrorCode.ADDRESS_RULE_INVALID,
                String.format(ErrorMessage.ADDRESS_RULE_INVALID, value));
    }

    private static ParsedAddress parse(String value) {
        if (value == null || value.isEmpty()
                || value.indexOf(SimpleXffCaptureCoreConstant.IPV6_ZONE_SEPARATOR) >= 0
                || value.indexOf(SimpleXffCaptureCoreConstant.IPV6_BRACKET_START) >= 0
                || value.indexOf(SimpleXffCaptureCoreConstant.IPV6_BRACKET_END) >= 0) {
            return null;
        }
        if (value.indexOf(SimpleXffCaptureCoreConstant.IPV6_WORD_SEPARATOR) >= 0) {
            byte[] bytes = parseIpv6(value);
            return bytes == null ? null : new ParsedAddress(bytes, false);
        }
        byte[] bytes = parseIpv4(value);
        return bytes == null ? null : new ParsedAddress(bytes, true);
    }

    private static byte[] parseIpv4(String value) {
        byte[] result = new byte[SimpleXffCaptureCoreConstant.IPV4_BYTE_COUNT];
        int partIndex = 0;
        int start = 0;
        for (int index = 0; index <= value.length(); index++) {
            if (index != value.length()
                    && value.charAt(index) != SimpleXffCaptureCoreConstant.IPV4_PART_SEPARATOR) {
                char current = value.charAt(index);
                if (current < '0' || current > '9') {
                    return null;
                }
                continue;
            }
            if (partIndex >= result.length || index == start) {
                return null;
            }
            String part = value.substring(start, index);
            if (part.length() > SimpleXffCaptureCoreConstant.IPV4_PART_MAX_LENGTH
                    || (part.length() > 1 && part.charAt(0) == '0')) {
                return null;
            }
            int number;
            try {
                number = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return null;
            }
            if (number > SimpleXffCaptureCoreConstant.IPV4_PART_MAX_VALUE) {
                return null;
            }
            result[partIndex++] = (byte) number;
            start = index + 1;
        }
        return partIndex == result.length ? result : null;
    }

    private static byte[] parseIpv6(String value) {
        int compressedIndex = value.indexOf(SimpleXffCaptureCoreConstant.IPV6_COMPRESSED_SEPARATOR);
        if (compressedIndex != value.lastIndexOf(SimpleXffCaptureCoreConstant.IPV6_COMPRESSED_SEPARATOR)) {
            return null;
        }

        String left = compressedIndex >= 0 ? value.substring(0, compressedIndex) : value;
        String right = compressedIndex >= 0
                ? value.substring(compressedIndex + SimpleXffCaptureCoreConstant.IPV6_COMPRESSED_SEPARATOR.length())
                : SimpleXffCaptureCoreConstant.EMPTY_VALUE;
        int[] leftWords = parseIpv6Words(left, compressedIndex < 0);
        int[] rightWords = parseIpv6Words(right, true);
        if (leftWords == null || rightWords == null) {
            return null;
        }

        int totalWords = leftWords.length + rightWords.length;
        if ((compressedIndex < 0 && totalWords != SimpleXffCaptureCoreConstant.IPV6_WORD_COUNT)
                || (compressedIndex >= 0 && totalWords >= SimpleXffCaptureCoreConstant.IPV6_WORD_COUNT)) {
            return null;
        }

        int[] words = new int[SimpleXffCaptureCoreConstant.IPV6_WORD_COUNT];
        System.arraycopy(leftWords, 0, words, 0, leftWords.length);
        System.arraycopy(rightWords, 0, words, words.length - rightWords.length, rightWords.length);
        byte[] bytes = new byte[SimpleXffCaptureCoreConstant.IPV6_BYTE_COUNT];
        for (int index = 0; index < words.length; index++) {
            bytes[index * SimpleXffCaptureCoreConstant.IPV6_WORD_BYTE_COUNT] =
                    (byte) (words[index] >>> SimpleXffCaptureCoreConstant.BYTE_BIT_COUNT);
            bytes[index * SimpleXffCaptureCoreConstant.IPV6_WORD_BYTE_COUNT + 1] = (byte) words[index];
        }
        return bytes;
    }

    private static int[] parseIpv6Words(String value, boolean allowEmbeddedIpv4) {
        if (value.isEmpty()) {
            return new int[0];
        }
        String[] parts = value.split(SimpleXffCaptureCoreConstant.IPV6_WORD_SEPARATOR_REGEX, -1);
        int wordCount = parts.length;
        boolean embeddedIpv4 = parts[parts.length - 1]
                .indexOf(SimpleXffCaptureCoreConstant.IPV4_PART_SEPARATOR) >= 0;
        if (embeddedIpv4) {
            if (!allowEmbeddedIpv4) {
                return null;
            }
            wordCount++;
        }
        int[] words = new int[wordCount];
        int outputIndex = 0;
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty()) {
                return null;
            }
            if (index == parts.length - 1 && embeddedIpv4) {
                byte[] ipv4 = parseIpv4(part);
                if (ipv4 == null) {
                    return null;
                }
                words[outputIndex++] = ((ipv4[0] & SimpleXffCaptureCoreConstant.IPV4_PART_MAX_VALUE)
                        << SimpleXffCaptureCoreConstant.BYTE_BIT_COUNT)
                        | (ipv4[1] & SimpleXffCaptureCoreConstant.IPV4_PART_MAX_VALUE);
                words[outputIndex] = ((ipv4[2] & SimpleXffCaptureCoreConstant.IPV4_PART_MAX_VALUE)
                        << SimpleXffCaptureCoreConstant.BYTE_BIT_COUNT)
                        | (ipv4[3] & SimpleXffCaptureCoreConstant.IPV4_PART_MAX_VALUE);
                continue;
            }
            if (!isHexWord(part)) {
                return null;
            }
            try {
                words[outputIndex++] = Integer.parseInt(part, SimpleXffCaptureCoreConstant.HEX_RADIX);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return words;
    }

    private static boolean isHexWord(String value) {
        if (value.length() > SimpleXffCaptureCoreConstant.IPV6_WORD_MAX_LENGTH) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            boolean digit = current >= '0' && current <= '9';
            boolean lower = current >= 'a' && current <= 'f';
            boolean upper = current >= 'A' && current <= 'F';
            if (!digit && !lower && !upper) {
                return false;
            }
        }
        return true;
    }

    private static byte[] mappedIpv4(byte[] value) {
        if (value.length != SimpleXffCaptureCoreConstant.IPV6_BYTE_COUNT) {
            return null;
        }
        for (int index = 0; index < SimpleXffCaptureCoreConstant.IPV4_MAPPED_ZERO_BYTE_COUNT; index++) {
            if (value[index] != 0) {
                return null;
            }
        }
        int markerIndex = SimpleXffCaptureCoreConstant.IPV4_MAPPED_ZERO_BYTE_COUNT;
        if ((value[markerIndex] & SimpleXffCaptureCoreConstant.IPV4_PART_MAX_VALUE)
                != SimpleXffCaptureCoreConstant.IPV4_MAPPED_MARKER
                || (value[markerIndex + 1] & SimpleXffCaptureCoreConstant.IPV4_PART_MAX_VALUE)
                != SimpleXffCaptureCoreConstant.IPV4_MAPPED_MARKER) {
            return null;
        }
        byte[] result = new byte[SimpleXffCaptureCoreConstant.IPV4_BYTE_COUNT];
        System.arraycopy(value, value.length - result.length, result, 0, result.length);
        return result;
    }

    private static final class ParsedAddress {
        private final byte[] bytes;
        private final boolean ipv4;

        private ParsedAddress(byte[] bytes, boolean ipv4) {
            this.bytes = bytes;
            this.ipv4 = ipv4;
        }
    }

    private static final class Cidr {
        private final byte[] address;
        private final int prefixLength;

        private Cidr(byte[] address, int prefixLength) {
            this.address = address;
            this.prefixLength = prefixLength;
        }

        private int getPrefixLength() {
            return prefixLength;
        }
    }
}
