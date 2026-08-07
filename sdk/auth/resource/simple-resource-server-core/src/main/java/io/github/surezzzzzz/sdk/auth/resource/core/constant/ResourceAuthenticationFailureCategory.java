package io.github.surezzzzzz.sdk.auth.resource.core.constant;

import lombok.Getter;

/**
 * 资源认证安全失败分类。
 *
 * @author surezzzzzz
 */
@Getter
public enum ResourceAuthenticationFailureCategory {

    /**
     * 凭据缺失。
     */
    CREDENTIAL_MISSING("CREDENTIAL_MISSING", "凭据缺失"),
    /**
     * 凭据歧义。
     */
    CREDENTIAL_AMBIGUOUS("CREDENTIAL_AMBIGUOUS", "凭据歧义"),
    /**
     * 凭据格式非法。
     */
    CREDENTIAL_MALFORMED("CREDENTIAL_MALFORMED", "凭据格式非法"),
    /**
     * 来源无法识别。
     */
    SOURCE_UNRECOGNIZED("SOURCE_UNRECOGNIZED", "来源无法识别"),
    /**
     * 签名或解密失败。
     */
    SIGNATURE_OR_DECRYPTION_FAILED("SIGNATURE_OR_DECRYPTION_FAILED", "签名或解密失败"),
    /**
     * 签发方无效。
     */
    ISSUER_INVALID("ISSUER_INVALID", "签发方无效"),
    /**
     * 受众无效。
     */
    AUDIENCE_INVALID("AUDIENCE_INVALID", "受众无效"),
    /**
     * 主体无效。
     */
    SUBJECT_INVALID("SUBJECT_INVALID", "主体无效"),
    /**
     * Token已过期。
     */
    TOKEN_EXPIRED("TOKEN_EXPIRED", "Token已过期"),
    /**
     * Token未激活或已撤销。
     */
    TOKEN_INACTIVE("TOKEN_INACTIVE", "Token未激活或已撤销"),
    /**
     * 应用授权无效。
     */
    AUTHORIZATION_INVALID("AUTHORIZATION_INVALID", "应用授权无效"),
    /**
     * Provider不可用。
     */
    PROVIDER_UNAVAILABLE("PROVIDER_UNAVAILABLE", "Provider不可用");

    /**
     * 分类代码。
     */
    private final String code;
    /**
     * 分类说明。
     */
    private final String description;

    ResourceAuthenticationFailureCategory(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按精确代码获取失败分类。
     *
     * @param code 分类代码
     * @return 分类；不存在时返回null
     */
    public static ResourceAuthenticationFailureCategory fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ResourceAuthenticationFailureCategory category : values()) {
            if (category.code.equals(code)) {
                return category;
            }
        }
        return null;
    }

    /**
     * 判断失败分类代码是否有效。
     *
     * @param code 分类代码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部失败分类代码。
     *
     * @return 分类代码数组
     */
    public static String[] getAllCodes() {
        ResourceAuthenticationFailureCategory[] categories = values();
        String[] codes = new String[categories.length];
        for (int index = 0; index < categories.length; index++) {
            codes[index] = categories[index].code;
        }
        return codes;
    }

    /**
     * 返回失败分类代码。
     *
     * @return 分类代码
     */
    @Override
    public String toString() {
        return code;
    }
}
