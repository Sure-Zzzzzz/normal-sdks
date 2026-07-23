package io.github.surezzzzzz.sdk.kms.core.constant;

import lombok.Getter;

/**
 * KMS 支持的密码算法。
 *
 * <p>每种算法固定绑定一个密钥用途，服务端不得使用用途不匹配的版本执行密码学操作。</p>
 *
 * @author surezzzzzz
 */
@Getter
public enum KmsAlgorithm {

    /**
     * P-256 ECDSA 签名算法，输出使用 ES256 JOSE 固定长度格式。
     */
    ES256("ES256", "P-256 ECDSA 签名", KmsKeyPurpose.SIGN),
    /**
     * AES-256-GCM 对称认证加密算法。
     */
    AES_256_GCM("AES_256_GCM", "AES-256-GCM 加密", KmsKeyPurpose.ENCRYPT);

    /**
     * 持久化和协议使用的稳定算法编码。
     */
    private final String code;
    /**
     * 面向管理界面的中文说明。
     */
    private final String description;
    /**
     * 算法允许的唯一密钥用途。
     */
    private final KmsKeyPurpose purpose;

    KmsAlgorithm(String code, String description, KmsKeyPurpose purpose) {
        this.code = code;
        this.description = description;
        this.purpose = purpose;
    }

    /**
     * 按稳定编码查找算法。
     *
     * @param code 算法编码
     * @return 对应算法；未知编码时返回 {@code null}
     */
    public static KmsAlgorithm fromCode(String code) {
        for (KmsAlgorithm value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 判断算法编码是否已定义。
     *
     * @param code 算法编码
     * @return 已定义时返回 {@code true}
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部稳定算法编码。
     *
     * @return 新建的算法编码数组
     */
    public static String[] getAllCodes() {
        KmsAlgorithm[] values = values();
        String[] codes = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            codes[index] = values[index].code;
        }
        return codes;
    }

    /**
     * 返回稳定算法编码。
     *
     * @return 算法编码
     */
    @Override
    public String toString() {
        return code;
    }
}
