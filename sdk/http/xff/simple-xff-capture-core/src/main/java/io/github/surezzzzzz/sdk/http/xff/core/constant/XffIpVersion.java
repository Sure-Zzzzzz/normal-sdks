package io.github.surezzzzzz.sdk.http.xff.core.constant;

import lombok.Getter;

/**
 * XFF IP 版本。
 *
 * @author surezzzzzz
 */
@Getter
public enum XffIpVersion {

    /**
     * IPv4。
     */
    IPV4("IPV4", "IPv4"),

    /**
     * IPv6。
     */
    IPV6("IPV6", "IPv6");

    private final String code;
    private final String description;

    XffIpVersion(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取 IP 版本。
     *
     * @param code 版本代码
     * @return IP 版本，不存在时返回 null
     */
    public static XffIpVersion fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (XffIpVersion version : values()) {
            if (version.code.equals(code)) {
                return version;
            }
        }
        return null;
    }

    /**
     * 判断版本代码是否有效。
     *
     * @param code 版本代码
     * @return true 表示有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部版本代码。
     *
     * @return 版本代码数组
     */
    public static String[] getAllCodes() {
        XffIpVersion[] values = values();
        String[] codes = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            codes[index] = values[index].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
