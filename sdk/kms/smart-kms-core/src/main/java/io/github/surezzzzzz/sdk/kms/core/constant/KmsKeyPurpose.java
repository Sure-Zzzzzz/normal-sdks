package io.github.surezzzzzz.sdk.kms.core.constant;

import lombok.Getter;

/**
 * KMS 密钥用途。
 *
 * <p>单个逻辑密钥只允许一种用途，禁止将签名密钥复用为加密密钥或反向复用。</p>
 *
 * @author surezzzzzz
 */
@Getter
public enum KmsKeyPurpose {

    /**
     * 仅可用于 ES256 签名和验签。
     */
    SIGN("SIGN", "签名"),
    /**
     * 仅可用于 AES-256-GCM 加密和解密。
     */
    ENCRYPT("ENCRYPT", "加密");

    /**
     * 持久化和管理契约使用的稳定用途编码。
     */
    private final String code;
    /**
     * 面向管理界面的中文说明。
     */
    private final String description;

    KmsKeyPurpose(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按稳定编码查找密钥用途。
     *
     * @param code 用途编码
     * @return 对应用途；未知编码时返回 {@code null}
     */
    public static KmsKeyPurpose fromCode(String code) {
        for (KmsKeyPurpose value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 判断密钥用途编码是否已定义。
     *
     * @param code 用途编码
     * @return 已定义时返回 {@code true}
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部稳定密钥用途编码。
     *
     * @return 新建的用途编码数组
     */
    public static String[] getAllCodes() {
        KmsKeyPurpose[] values = values();
        String[] codes = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            codes[index] = values[index].code;
        }
        return codes;
    }

    /**
     * 返回稳定用途编码。
     *
     * @return 用途编码
     */
    @Override
    public String toString() {
        return code;
    }
}
