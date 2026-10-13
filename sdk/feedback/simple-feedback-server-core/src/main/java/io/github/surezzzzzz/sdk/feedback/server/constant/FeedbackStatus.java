package io.github.surezzzzzz.sdk.feedback.server.constant;

import lombok.Getter;

/**
 * 反馈状态枚举（三态；无「处理中」伪中间态——无分派体系，宁缺毋滥；终态可重开）
 *
 * @author surezzzzzz
 */
@Getter
public enum FeedbackStatus {

    /**
     * 已提交（待处理；终态重开的目标态）
     */
    SUBMITTED("submitted", "已提交"),

    /**
     * 已解决（终态）
     */
    RESOLVED("resolved", "已解决"),

    /**
     * 不予处理（终态）
     */
    DISMISSED("dismissed", "不予处理");

    private final String code;
    private final String description;

    FeedbackStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 判断能否迁移到目标状态。
     *
     * <p>合法迁移：SUBMITTED→RESOLVED、SUBMITTED→DISMISSED（处理）；
     * RESOLVED→SUBMITTED、DISMISSED→SUBMITTED（终态重开）；其余一律非法
     * （含两个终态之间直迁与自迁移）。</p>
     *
     * @param target 目标状态
     * @return true 合法，false 非法
     */
    public boolean canTransitionTo(FeedbackStatus target) {
        if (target == null || target == this) {
            return false;
        }
        if (this == SUBMITTED) {
            return target == RESOLVED || target == DISMISSED;
        }
        return target == SUBMITTED;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 类型代码
     * @return 枚举，如果不存在返回 null
     */
    public static FeedbackStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (FeedbackStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断类型代码是否有效
     *
     * @param code 类型代码
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有有效的类型代码
     *
     * @return 类型代码数组
     */
    public static String[] getAllCodes() {
        FeedbackStatus[] statuses = values();
        String[] codes = new String[statuses.length];
        for (int i = 0; i < statuses.length; i++) {
            codes[i] = statuses[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
