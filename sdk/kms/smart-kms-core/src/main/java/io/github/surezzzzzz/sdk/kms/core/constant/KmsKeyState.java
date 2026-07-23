package io.github.surezzzzzz.sdk.kms.core.constant;

import lombok.Getter;

/**
 * 逻辑密钥状态。
 *
 * @author surezzzzzz
 */
@Getter
public enum KmsKeyState {

    /**
     * 可执行签名、加密和解密操作。
     */
    ACTIVE("ACTIVE", "启用"),
    /**
     * 拒绝密码学操作，但可读取已发布 ES256 公钥。
     */
    DISABLED("DISABLED", "禁用"),
    /**
     * 已安排销毁，所有操作和公钥发布均被阻断。
     */
    PENDING_DESTRUCTION("PENDING_DESTRUCTION", "待销毁"),
    /**
     * 材料已销毁，状态不可逆。
     */
    DESTROYED("DESTROYED", "已销毁");

    /**
     * 持久化使用的稳定编码。
     */
    private final String code;
    /**
     * 面向管理界面的中文说明。
     */
    private final String description;

    KmsKeyState(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按稳定编码查找逻辑密钥状态。
     *
     * @param code 逻辑密钥状态编码
     * @return 对应状态；未知编码时返回 {@code null}
     */
    public static KmsKeyState fromCode(String code) {
        for (KmsKeyState value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 判断逻辑密钥状态编码是否已定义。
     *
     * @param code 逻辑密钥状态编码
     * @return 已定义时返回 {@code true}
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部逻辑密钥状态编码。
     *
     * @return 新建的逻辑密钥状态编码数组
     */
    public static String[] getAllCodes() {
        KmsKeyState[] values = values();
        String[] codes = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            codes[index] = values[index].code;
        }
        return codes;
    }

    /**
     * 返回稳定逻辑密钥状态编码。
     *
     * @return 逻辑密钥状态编码
     */
    @Override
    public String toString() {
        return code;
    }
}
