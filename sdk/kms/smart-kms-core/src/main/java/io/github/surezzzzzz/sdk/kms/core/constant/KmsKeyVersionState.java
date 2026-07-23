package io.github.surezzzzzz.sdk.kms.core.constant;

import lombok.Getter;

/**
 * 密钥版本状态。
 *
 * @author surezzzzzz
 */
@Getter
public enum KmsKeyVersionState {

    /**
     * 当前逻辑密钥可用于新密码学操作的版本。
     */
    ACTIVE("ACTIVE", "启用"),
    /**
     * 已被新版本替代，仅可用于历史解密、验签和公钥读取。
     */
    RETIRED("RETIRED", "已退役"),
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

    KmsKeyVersionState(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按稳定编码查找版本状态。
     *
     * @param code 版本状态编码
     * @return 对应状态；未知编码时返回 {@code null}
     */
    public static KmsKeyVersionState fromCode(String code) {
        for (KmsKeyVersionState value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 判断版本状态编码是否已定义。
     *
     * @param code 版本状态编码
     * @return 已定义时返回 {@code true}
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部版本状态编码。
     *
     * @return 新建的版本状态编码数组
     */
    public static String[] getAllCodes() {
        KmsKeyVersionState[] values = values();
        String[] codes = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            codes[index] = values[index].code;
        }
        return codes;
    }

    /**
     * 返回稳定版本状态编码。
     *
     * @return 版本状态编码
     */
    @Override
    public String toString() {
        return code;
    }
}
