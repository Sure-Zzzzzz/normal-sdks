package io.github.surezzzzzz.sdk.sensitive.ip.validator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IP 地址校验器
 * <p>
 * 完整抄自 Apache Commons Validator 1.8.0 的 InetAddressValidator
 *
 * @author Apache Commons Validator
 * @author surezzzzzz (adapted)
 */
public class InetAddressValidator implements Serializable {

    private static final long serialVersionUID = -919201640201914789L;

    private static final String IPV4_REGEX =
            "^(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}$";

    private static final Pattern IPV4_PATTERN = Pattern.compile(IPV4_REGEX);

    private static final int IPV6_MAX_HEX_GROUPS = 8;

    private static final int IPV6_MAX_HEX_DIGITS_PER_GROUP = 4;

    private static final int MAX_UNSIGNED_SHORT = 0xffff;

    private static final int BASE_16 = 16;

    private static final Pattern REG_EX_IPV4_MAPPED =
            Pattern.compile("^::[fF]{4}:(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private static final InetAddressValidator VALIDATOR = new InetAddressValidator();

    /**
     * 获取单例实例
     */
    public static InetAddressValidator getInstance() {
        return VALIDATOR;
    }

    /**
     * 校验 IPv4 地址
     */
    public boolean isValidInet4Address(final String inet4Address) {
        if (inet4Address == null) {
            return false;
        }

        final Matcher m = IPV4_PATTERN.matcher(inet4Address);
        if (!m.matches()) {
            return false;
        }

        // Validate the octets
        final String[] octets = inet4Address.split("\\.");
        if (octets.length != 4) {
            return false;
        }

        try {
            for (String octet : octets) {
                final int value = Integer.parseInt(octet);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
        } catch (final NumberFormatException e) {
            return false;
        }

        return true;
    }

    /**
     * 校验 IPv6 地址
     */
    public boolean isValidInet6Address(final String inet6Address) {
        if (inet6Address == null) {
            return false;
        }

        final boolean containsCompressedZeroes = inet6Address.contains("::");
        if (containsCompressedZeroes && (inet6Address.indexOf("::") != inet6Address.lastIndexOf("::"))) {
            return false;
        }
        if ((inet6Address.startsWith(":") && !inet6Address.startsWith("::"))
                || (inet6Address.endsWith(":") && !inet6Address.endsWith("::"))) {
            return false;
        }
        String[] octets = inet6Address.split(":");
        if (containsCompressedZeroes) {
            final List<String> octetList = new ArrayList<>(Arrays.asList(octets));
            if (inet6Address.endsWith("::")) {
                octetList.add("");
            } else if (inet6Address.startsWith("::") && !octetList.isEmpty()) {
                octetList.remove(0);
            }
            octets = octetList.toArray(new String[octetList.size()]);
        }

        if (octets.length > IPV6_MAX_HEX_GROUPS) {
            return false;
        }

        int validOctets = 0;
        int emptyOctets = 0;

        for (int index = 0; index < octets.length; index++) {
            final String octet = octets[index];
            if (octet.length() == 0) {
                emptyOctets++;
                if (emptyOctets > 1) {
                    return false;
                }
            } else {
                emptyOctets = 0;
                if (index == octets.length - 1 && octet.contains(".")) {
                    if (!isValidInet4Address(octet)) {
                        return false;
                    }
                    validOctets += 2;
                    continue;
                }
                if (octet.length() > IPV6_MAX_HEX_DIGITS_PER_GROUP) {
                    return false;
                }
                int octetInt = 0;
                try {
                    octetInt = Integer.parseInt(octet, BASE_16);
                } catch (final NumberFormatException e) {
                    return false;
                }
                if (octetInt < 0 || octetInt > MAX_UNSIGNED_SHORT) {
                    return false;
                }
            }
            validOctets++;
        }
        if (validOctets > IPV6_MAX_HEX_GROUPS || (validOctets < IPV6_MAX_HEX_GROUPS && !containsCompressedZeroes)) {
            return false;
        }

        return true;
    }

    /**
     * 校验 IP 地址（自动识别 IPv4 或 IPv6）
     */
    public boolean isValid(final String inetAddress) {
        return isValidInet4Address(inetAddress) || isValidInet6Address(inetAddress);
    }
}
