package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant;

import lombok.Getter;

/**
 * Verification Mode
 *
 * <p>Token 验证模式枚举，控制 resource server 如何验证 token。
 *
 * @author surezzzzzz
 */
@Getter
public enum VerificationMode {

    /**
     * 本地 JWT 验签（默认）
     * 使用公钥本地验证 JWT 签名，性能最好，不支持即时撤销感知
     */
    JWT("jwt", "本地 JWT 验签"),

    /**
     * 调 introspect 端点验证
     * 每次请求调 /oauth2/introspect，支持即时撤销感知，有额外 HTTP 开销
     */
    INTROSPECT("introspect", "Introspect 端点验证");

    private final String code;
    private final String description;

    VerificationMode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 获取枚举
     *
     * @param code 验证模式 code
     * @return 枚举，不存在返回 null
     */
    public static VerificationMode fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (VerificationMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code)) {
                return mode;
            }
        }
        return null;
    }

    /**
     * 判断 code 是否有效
     *
     * @param code 验证模式 code
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有有效的 code
     *
     * @return code 数组
     */
    public static String[] getAllCodes() {
        VerificationMode[] modes = values();
        String[] codes = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            codes[i] = modes[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
