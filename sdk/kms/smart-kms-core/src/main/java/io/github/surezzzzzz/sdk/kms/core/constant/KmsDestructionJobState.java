package io.github.surezzzzzz.sdk.kms.core.constant;

import lombok.Getter;

/**
 * 销毁任务状态。
 *
 * @author surezzzzzz
 */
@Getter
public enum KmsDestructionJobState {

    /**
     * 任务到期后可被 worker 领取。
     */
    PENDING("PENDING", "待处理"),
    /**
     * 任务已被某个 worker 在有效租约内领取。
     */
    CLAIMED("CLAIMED", "已领取"),
    /**
     * 密钥材料已销毁且任务已完成。
     */
    COMPLETED("COMPLETED", "已完成");

    /**
     * 持久化使用的稳定编码。
     */
    private final String code;
    /**
     * 面向管理界面的中文说明。
     */
    private final String description;

    KmsDestructionJobState(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按稳定编码查找任务状态。
     *
     * @param code 任务状态编码
     * @return 对应状态；未知编码时返回 {@code null}
     */
    public static KmsDestructionJobState fromCode(String code) {
        for (KmsDestructionJobState value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 判断任务状态编码是否已定义。
     *
     * @param code 任务状态编码
     * @return 已定义时返回 {@code true}
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部销毁任务状态编码。
     *
     * @return 新建的任务状态编码数组
     */
    public static String[] getAllCodes() {
        KmsDestructionJobState[] values = values();
        String[] codes = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            codes[index] = values[index].code;
        }
        return codes;
    }

    /**
     * 返回稳定任务状态编码。
     *
     * @return 任务状态编码
     */
    @Override
    public String toString() {
        return code;
    }
}
