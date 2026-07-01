package io.github.surezzzzzz.sdk.elasticsearch.route.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 代理类型枚举
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public enum ProxyType {

    /**
     * 强制 CGLIB 代理
     */
    CGLIB("CGLIB", "强制 CGLIB 代理"),

    /**
     * 强制 JDK 动态代理
     */
    JDK("JDK", "强制 JDK 动态代理"),

    /**
     * 自动选择（默认），优先 CGLIB，失败后回退到 JDK
     */
    AUTO("AUTO", "自动选择，优先 CGLIB，失败后回退到 JDK");

    private final String code;
    private final String description;

    /**
     * 根据 code 获取枚举，大小写不敏感
     */
    public static ProxyType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ProxyType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断 code 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有有效的类型代码
     */
    public static String[] getAllCodes() {
        ProxyType[] types = values();
        String[] codes = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            codes[i] = types[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
