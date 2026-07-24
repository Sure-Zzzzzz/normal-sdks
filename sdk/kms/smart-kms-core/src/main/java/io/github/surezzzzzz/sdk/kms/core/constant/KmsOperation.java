package io.github.surezzzzzz.sdk.kms.core.constant;

import lombok.Getter;

/**
 * KMS 操作类型。
 *
 * @author surezzzzzz
 */
@Getter
public enum KmsOperation {

    /**
     * 使用 ES256 私钥签名。
     */
    SIGN("SIGN", "签名"),
    /**
     * 使用 ES256 公钥验签。
     */
    VERIFY("VERIFY", "验签"),
    /**
     * 使用 AES-256-GCM 加密。
     */
    ENCRYPT("ENCRYPT", "加密"),
    /**
     * 使用 AES-256-GCM 解密。
     */
    DECRYPT("DECRYPT", "解密"),
    /**
     * 读取已发布的 ES256 公钥。
     */
    READ_PUBLIC_KEY("READ_PUBLIC_KEY", "读取公钥"),
    /**
     * 创建逻辑密钥及初始版本。
     */
    CREATE_KEY("CREATE_KEY", "创建密钥"),
    /**
     * 创建新的活动密钥版本。
     */
    ROTATE_KEY("ROTATE_KEY", "轮换密钥"),
    /**
     * 修改逻辑密钥状态。
     */
    CHANGE_KEY_STATE("CHANGE_KEY_STATE", "修改密钥状态"),
    /**
     * 安排逻辑密钥及其版本销毁。
     */
    SCHEDULE_KEY_DESTRUCTION("SCHEDULE_KEY_DESTRUCTION", "安排密钥销毁"),
    /**
     * 取消尚未领取的密钥销毁任务。
     */
    CANCEL_KEY_DESTRUCTION("CANCEL_KEY_DESTRUCTION", "取消密钥销毁"),
    /**
     * 创建密钥精确授权策略。
     */
    CREATE_KEY_POLICY("CREATE_KEY_POLICY", "创建密钥策略"),
    /**
     * 撤销密钥精确授权策略。
     */
    REVOKE_KEY_POLICY("REVOKE_KEY_POLICY", "撤销密钥策略"),
    /**
     * 销毁 worker 处理到期密钥版本。
     */
    PROCESS_KEY_DESTRUCTION("PROCESS_KEY_DESTRUCTION", "处理密钥销毁");

    /**
     * 持久化和策略匹配使用的稳定编码。
     */
    private final String code;
    /**
     * 面向管理界面的中文说明。
     */
    private final String description;

    KmsOperation(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按稳定编码查找操作。
     *
     * @param code 操作编码
     * @return 对应操作；未知编码时返回 {@code null}
     */
    public static KmsOperation fromCode(String code) {
        for (KmsOperation value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 判断操作编码是否已定义。
     *
     * @param code 操作编码
     * @return 已定义时返回 {@code true}
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部稳定操作编码。
     *
     * @return 新建的操作编码数组
     */
    public static String[] getAllCodes() {
        KmsOperation[] values = values();
        String[] codes = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            codes[index] = values[index].code;
        }
        return codes;
    }

    /**
     * 返回稳定操作编码，避免展示枚举内部结构。
     *
     * @return 操作编码
     */
    @Override
    public String toString() {
        return code;
    }
}
